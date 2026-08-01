#include "transport/thread_pool.h"

#include <utility>

namespace homemoney::sync {

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

ThreadPool::~ThreadPool() {
    shutdown();
}

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

std::size_t ThreadPool::queueDepth() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return queue_.size();
}

void ThreadPool::workerLoop() {
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

    if (onThreadStop_) {
        onThreadStop_();
    }
}

}  // namespace homemoney::sync
