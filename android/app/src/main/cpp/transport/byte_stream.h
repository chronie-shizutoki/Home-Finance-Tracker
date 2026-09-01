#ifndef HOMEMONEY_TRANSPORT_BYTE_STREAM_H
#define HOMEMONEY_TRANSPORT_BYTE_STREAM_H

#include <array>
#include <concepts>
#include <cstddef>
#include <cstdint>

#include "transport/io_result.h"

/**
 * Byte stream abstraction for the frame codec.
 *
 * The codec is written against these two concepts rather than against a raw fd. That is
 * not architecture astronautics: this device has no host C++ toolchain, so the only way
 * to execute the codec's *runtime* behaviour during a build is to run it at compile time
 * over an in-memory stream. MemoryReader below can replay a buffer one byte at a time and
 * inject EINTR / EAGAIN, which lets protocol_conformance style static_asserts cover the
 * partial-read paths that used to be the source of the "sync randomly truncates" bugs.
 *
 * Production code plugs FdStream (transport/socket_stream.h) into exactly the same slots.
 */
namespace homemoney::sync {

/// Anything that can supply bytes.
template <typename T>
concept ByteReader = requires(T& reader, std::uint8_t* dst, std::size_t n) {
    { reader.readSome(dst, n) } -> std::same_as<IoResult>;
};

/// Anything that can accept bytes.
template <typename T>
concept ByteWriter = requires(T& writer, const std::uint8_t* src, std::size_t n) {
    { writer.writeSome(src, n) } -> std::same_as<IoResult>;
};

/**
 * In-memory reader used by the compile time conformance tests.
 *
 * @param maxChunk        largest number of bytes a single readSome may return; 0 means
 *                        "as much as asked for". Setting this to 1 models the worst case
 *                        TCP segmentation a receiver can see on a lossy link.
 * @param transientEvery  every Nth call fails transiently without consuming input,
 *                        alternating EAGAIN and EINTR; 0 disables the injection.
 */
class MemoryReader {
public:
    constexpr MemoryReader(const std::uint8_t* data,
                           std::size_t size,
                           std::size_t maxChunk = 0,
                           std::uint32_t transientEvery = 0)
        : data_(data), size_(size), maxChunk_(maxChunk), transientEvery_(transientEvery) {}

    constexpr IoResult readSome(std::uint8_t* dst, std::size_t n) {
        ++calls_;
        if (transientEvery_ != 0 && (calls_ % transientEvery_) == 0) {
            // Alternate the two transient reasons so both branches of the retry loop are
            // exercised rather than only the one that happens to come first.
            const bool interrupted = (calls_ % (2u * transientEvery_)) == 0;
            return IoResult{interrupted ? IoStatus::kInterrupted : IoStatus::kWouldBlock, 0};
        }
        if (n == 0) {
            return IoResult{IoStatus::kOk, 0};
        }
        if (pos_ >= size_) {
            return IoResult{IoStatus::kClosed, 0};
        }
        std::size_t take = size_ - pos_;
        if (take > n) {
            take = n;
        }
        if (maxChunk_ != 0 && take > maxChunk_) {
            take = maxChunk_;
        }
        for (std::size_t i = 0; i < take; ++i) {
            dst[i] = data_[pos_ + i];
        }
        pos_ += take;
        return IoResult{IoStatus::kOk, take};
    }

    [[nodiscard]] constexpr std::size_t consumed() const { return pos_; }
    [[nodiscard]] constexpr bool exhausted() const { return pos_ >= size_; }

private:
    const std::uint8_t* data_;
    std::size_t size_;
    std::size_t pos_ = 0;
    std::size_t maxChunk_;
    std::uint32_t transientEvery_;
    std::uint32_t calls_ = 0;
};

/**
 * In-memory writer with the mirror-image knobs: short writes and injected transients.
 * Capacity is a template parameter so the whole thing lives on the stack and stays usable
 * inside a constant expression.
 */
template <std::size_t Capacity>
class MemoryWriter {
public:
    constexpr explicit MemoryWriter(std::size_t maxChunk = 0, std::uint32_t transientEvery = 0)
        : maxChunk_(maxChunk), transientEvery_(transientEvery) {}

    constexpr IoResult writeSome(const std::uint8_t* src, std::size_t n) {
        ++calls_;
        if (transientEvery_ != 0 && (calls_ % transientEvery_) == 0) {
            const bool interrupted = (calls_ % (2u * transientEvery_)) == 0;
            return IoResult{interrupted ? IoStatus::kInterrupted : IoStatus::kWouldBlock, 0};
        }
        if (n == 0) {
            return IoResult{IoStatus::kOk, 0};
        }
        if (len_ >= Capacity) {
            // A real socket would block here; for the test double a full buffer is a bug
            // in the test, so surface it loudly instead of silently dropping bytes.
            return IoResult{IoStatus::kError, 0};
        }
        std::size_t take = Capacity - len_;
        if (take > n) {
            take = n;
        }
        if (maxChunk_ != 0 && take > maxChunk_) {
            take = maxChunk_;
        }
        for (std::size_t i = 0; i < take; ++i) {
            buffer_[len_ + i] = src[i];
        }
        len_ += take;
        return IoResult{IoStatus::kOk, take};
    }

    [[nodiscard]] constexpr const std::uint8_t* data() const { return buffer_.data(); }
    [[nodiscard]] constexpr std::size_t size() const { return len_; }

    constexpr bool equals(const std::uint8_t* expected, std::size_t expectedSize) const {
        if (len_ != expectedSize) {
            return false;
        }
        for (std::size_t i = 0; i < len_; ++i) {
            if (buffer_[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

private:
    std::array<std::uint8_t, Capacity> buffer_{};
    std::size_t len_ = 0;
    std::size_t maxChunk_;
    std::uint32_t transientEvery_;
    std::uint32_t calls_ = 0;
};

}  // namespace homemoney::sync

#endif  // HOMEMONEY_TRANSPORT_BYTE_STREAM_H
