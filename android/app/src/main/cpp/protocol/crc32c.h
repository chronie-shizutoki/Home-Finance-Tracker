#ifndef HOMEMONEY_SYNC_CRC32C_H
#define HOMEMONEY_SYNC_CRC32C_H

#include <cstddef>
#include <cstdint>

/**
 * CRC-32C (Castagnoli) - the integrity primitive shared by both peers of a LAN sync.
 *
 * Parameters, spelled out so the Kotlin mirror in Crc32c.kt can be checked against them:
 *   polynomial  0x1EDC6F41, used here in its reflected form 0x82F63B78
 *   init        0xFFFFFFFF
 *   reflect in  yes
 *   reflect out yes
 *   final xor   0xFFFFFFFF
 *   check       crc32c("123456789") == 0xE3069283
 *
 * Castagnoli is preferred over the zlib polynomial because it has a better Hamming
 * distance for the frame sizes we use and is hardware accelerated on arm64 (CRC32CX),
 * which matters when hashing every chunk of a multi-megabyte transfer.
 */
namespace homemoney::sync {

namespace detail {

/// Reflected CRC-32C polynomial.
inline constexpr std::uint32_t kCrc32cPolynomial = 0x82F63B78u;

/// Builds the 256 entry lookup table at compile time; no runtime initialisation and no
/// static initialisation order problems.
struct Crc32cTable {
    std::uint32_t entries[256]{};

    constexpr Crc32cTable() {
        for (std::uint32_t i = 0; i < 256; ++i) {
            std::uint32_t crc = i;
            for (int bit = 0; bit < 8; ++bit) {
                crc = (crc & 1u) != 0u ? (crc >> 1) ^ kCrc32cPolynomial : (crc >> 1);
            }
            entries[i] = crc;
        }
    }
};

inline constexpr Crc32cTable kCrc32cTable{};

}  // namespace detail

/**
 * Continue a CRC-32C over another block of data.
 *
 * @param crc the running value, or crc32cInit() for the first block.
 * @return the updated running value; pass it to crc32cFinish() to obtain the checksum.
 */
constexpr std::uint32_t crc32cUpdate(std::uint32_t crc, const std::uint8_t* data,
                                     std::size_t length) {
    for (std::size_t i = 0; i < length; ++i) {
        crc = detail::kCrc32cTable.entries[(crc ^ data[i]) & 0xFFu] ^ (crc >> 8);
    }
    return crc;
}

/// Seed value for an incremental computation.
constexpr std::uint32_t crc32cInit() { return 0xFFFFFFFFu; }

/// Applies the final xor to a running value.
constexpr std::uint32_t crc32cFinish(std::uint32_t crc) { return crc ^ 0xFFFFFFFFu; }

/// One-shot CRC-32C over a single buffer.
constexpr std::uint32_t crc32c(const std::uint8_t* data, std::size_t length) {
    return crc32cFinish(crc32cUpdate(crc32cInit(), data, length));
}

}  // namespace homemoney::sync

#endif  // HOMEMONEY_SYNC_CRC32C_H
