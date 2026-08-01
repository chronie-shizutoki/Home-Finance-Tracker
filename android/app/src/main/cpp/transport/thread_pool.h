#ifndef HOMEMONEY_TRANSPORT_THREAD_POOL_H
#define HOMEMONEY_TRANSPORT_THREAD_POOL_H

#include <condition_variable>
#include <cstddef>
#include <deque>
#include <functional>
#include <mutex>
#include <thread>
#include <vector>

/**
 * Fixed size worker pool with a bounded queue.
 *
 * Why the server needs one: the old accept loop handled connections serially on the
 * accept thread, and handling a connection means calling into Kotlin to show a "device X
 * wants to sync" dialog and waiting up to 60 s for the user. During that minute the
 * listener was not accepting, so every other phone on the LAN saw a connect that hung and
 * eventually timed out with no explanation. With a pool the listener never blocks, and a
 * genuinely overloaded server can answer BUSY - a retryable, explainable error - instead
 * of going silent.
 *
 * The queue is bounded on purpose. An unbounded queue under load just moves the failure
 * from "refused quickly" to "accepted then timed out", which is strictly worse for the
 * user and for diagnosis.
 */
namespace homemoney::sync {

class ThreadPool {
public:
    using Task = std::function<void()>;
    /// Run on each worker at start / exit. Used to attach the thread to the JVM.
    using ThreadHook = std::function<void()>;

    ThreadPool(std::size_t threadCount,
               std::size_t queueCapacity,
               ThreadHook onThreadStart = nullptr,
               ThreadHook onThreadStop = nullptr);

    ~ThreadPool();

    ThreadPool(const ThreadPool&) = delete;
    ThreadPool& operator=(const ThreadPool&) = delete;

    /// Queues a task. Returns false when the pool is saturated or already shut down.
    bool tryPost(Task task);

    /// Stops accepting work, wakes the workers and joins them. Idempotent.
    void shutdown();

    /// Tasks currently queued, for the observability counters.
    std::size_t queueDepth() const;

private:
    void workerLoop();

    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<Task> queue_;
    std::vector<std::thread> workers_;
    std::size_t queueCapacity_;
    ThreadHook onThreadStart_;
    ThreadHook onThreadStop_;
    bool stopping_ = false;
};

}  // namespace homemoney::sync

#endif  // HOMEMONEY_TRANSPORT_THREAD_POOL_H
