/**
 * Fixed-size worker pool with a bounded queue — implementation.
 *
 * This pool serves exactly one purpose: it decouples the accept loop from the per-connection
 * handler so that a slow handler (e.g. one waiting on a user confirmation dialog) does not
 * stall every other device on the LAN. See thread_pool.h for the design rationale.
 *
 * Thread safety: all public methods are guarded by mutex_. The worker loop holds the lock
 * only while dequeuing; task execution is outside the critical section so that a long-running
 * handler does not prevent other workers from picking up the next job.
 */

#include "transport/thread_pool.h"

#include <utility>

namespace homemoney::sync {

// ----------------------------------------------------------------------- construction

/// Spawns @p threadCount workers and primes them to wait on the condition variable.
/// Both threadCount and queueCapacity are clamped to a minimum of 1 so a caller passing
/// zero does not accidentally create a pool that can never accept work.
ThreadPool::ThreadPool(std::size_t threadCount,
                       std::size_t queueCapacity,
                       ThreadHook onThreadStart,
                       ThreadHook onThreadStop)
    : queueCapacity_(queueCapacity == 0 ? 1 : queueCapacity),
      onThreadStart_(std::move(onThreadStart)),
      onThreadStop_(std::move(onThreadStop)) {
    const std::size_t count = threadCount == 0 ? 1 : threadCount;
    workers_.reserve(count);
    for (std::size_t i = 0; i < count; ++i) {
        workers_.emplace_back([this] { workerLoop(); });
    }
}

/// Calls shutdown() to ensure workers are joined before the pool object is destroyed.
/// The destructor itself does not block — shutdown() does the joining synchronously,
/// which is safe because the pool owns the worker threads and the destructor is the
/// last reference to them.
ThreadPool::~ThreadPool() {
    shutdown();
}

// ----------------------------------------------------------------------- submission

/// Attempts to enqueue a task for execution by an idle worker.
///
/// Returns false in two cases:
///  1. The queue is full (workers are saturated). The caller should answer BUSY to the
///     connecting peer rather than silently dropping the connection.
///  2. The pool is shutting down. Queued work is deliberately discarded during shutdown
///     because the sockets those handlers would touch are being closed.
///
/// The lock is released before notify_one() to avoid the "hurry up and wait" pattern
/// where the woken worker immediately blocks on the mutex the poster still holds.
bool ThreadPool::tryPost(Task task) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopping_ || queue_.size() >= queueCapacity_) {
            return false;
        }
        queue_.push_back(std::move(task));
    }
    cv_.notify_one();
    return true;
}

// ----------------------------------------------------------------------- teardown

/// Stops accepting new work, discards any queued-but-not-yet-started tasks, and joins
/// every worker thread. Idempotent: calling shutdown() more than once is harmless.
///
/// Queued tasks are deliberately dropped rather than executed during teardown. These are
/// connection handlers whose sockets are about to be closed by stopServer(); running them
/// during teardown would touch a JVM that may already be detaching, which on Android is a
/// crash that produces no useful stack trace.
void ThreadPool::shutdown() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopping_) {
            return;
        }
        stopping_ = true;
        // Drop queued work: these are connection handlers whose sockets are about to be
        // closed anyway, and running them during teardown would touch a detaching JVM.
        queue_.clear();
    }
    cv_.notify_all();
    for (std::thread& worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    workers_.clear();
}

// ----------------------------------------------------------------------- observability

/// Returns the number of tasks currently waiting for a worker. Used by transportStats()
/// to expose the pool's health as a diagnostic metric.
std::size_t ThreadPool::queueDepth() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return queue_.size();
}

// ---------------------------------------------------------------------- worker loop

/// The single function every worker thread runs for its entire lifetime.
///
/// The loop blocks on the condition variable until a task is available or the pool is
/// being torn down. When both conditions are true (stopping_ is set AND the queue is
/// empty), the worker exits so that shutdown()'s join() can complete.
///
/// Exception safety: a handler that throws is caught here. The exception is intentionally
/// swallowed because a single misbehaving handler must not take down the entire sync
/// service — one lost connection is far better than a crashed process.
void ThreadPool::workerLoop() {
    // Attach this native thread to the JVM so it can call back into Kotlin.
    // On Android, a native thread cannot make JNI calls until it is attached.
    if (onThreadStart_) {
        onThreadStart_();
    }

    for (;;) {
        Task task;
        {
            std::unique_lock<std::mutex> lock(mutex_);
            cv_.wait(lock, [this] { return stopping_ || !queue_.empty(); });
            if (stopping_ && queue_.empty()) {
                break;
            }
            task = std::move(queue_.front());
            queue_.pop_front();
        }
        if (task) {
            // A handler must never take the whole server down. Anything that escapes is a
            // bug, but losing one connection beats losing the sync service.
            try {
                task();
            } catch (...) {
                // Intentionally swallowed; the handler logs its own failures.
            }
        }
    }

    // Detach from the JVM before the thread exits. Failing to detach leaks JNI internal
    // bookkeeping and eventually prevents any new native thread from attaching.
    if (onThreadStop_) {
        onThreadStop_();
    }
}

}  // namespace homemoney::sync
