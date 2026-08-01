#ifndef HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H
#define HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H

#include <cstddef>
#include <cstdint>

// GENERATED FILE - do not edit by hand. Run: python tools/gen_frame_vectors.py
//
// Mirror of protocol/retry_vectors.txt, the single source of truth for the backoff curve
// shared with the Kotlin side. transport_conformance.cpp asserts every entry at compile
// time, so a change to the policy that is not reflected here fails the native build.

namespace homemoney::sync::vectors {

struct RetryVector {
    const char* name;
    std::uint32_t baseDelayMs;
    std::uint32_t maxDelayMs;
    std::uint32_t retryIndex;
    std::uint32_t randomValue;
    std::uint32_t ceilingMs;
    std::uint32_t delayMs;
};

inline constexpr RetryVector kRetryVectors[] = {
    { "default_r0_lo", 250U, 8000U, 0U, 0U, 250U, 125U },
    { "default_r0_hi", 250U, 8000U, 0U, 4294967295U, 250U, 128U },
    { "default_r1_mid", 250U, 8000U, 1U, 12345U, 500U, 296U },
    { "default_r2_mid", 250U, 8000U, 2U, 999983U, 1000U, 988U },
    { "default_r3_mid", 250U, 8000U, 3U, 1592594996U, 2000U, 1993U },
    { "default_r5_cap", 250U, 8000U, 5U, 0U, 8000U, 4000U },
    { "default_r9_cap", 250U, 8000U, 9U, 4294967295U, 8000U, 5822U },
    { "default_r99_cap", 250U, 8000U, 99U, 7U, 8000U, 4007U },
    { "odd_ceiling_lo", 125U, 100000U, 0U, 0U, 125U, 62U },
    { "odd_ceiling_hi", 125U, 100000U, 0U, 4294967295U, 125U, 125U },
    { "zero_base", 0U, 8000U, 3U, 42U, 0U, 0U },
    { "cap_below_base", 1000U, 100U, 4U, 42U, 100U, 92U },
    { "fast_r0", 50U, 1000U, 0U, 0U, 50U, 25U },
    { "fast_r4_cap", 50U, 1000U, 4U, 4294967295U, 800U, 654U },
};

inline constexpr std::size_t kRetryVectorCount =
        sizeof(kRetryVectors) / sizeof(kRetryVectors[0]);

struct XorshiftVector {
    std::uint32_t seed;
    std::uint32_t after1;
    std::uint32_t after2;
    std::uint32_t after3;
};

inline constexpr XorshiftVector kXorshiftVectors[] = {
    { 1U, 270369U, 67634689U, 2647435461U },
    { 2U, 540738U, 134253570U, 697882754U },
    { 2654435769U, 1359758873U, 3761132862U, 2075758394U },
    { 3735928559U, 1199382711U, 2384302402U, 3129746520U },
    { 2147483647U, 2148245535U, 1963948411U, 3727350027U },
    { 2147483648U, 2148024320U, 2299036804U, 2861646152U },
    { 4294967295U, 253983U, 4228382207U, 1958451267U },
};

inline constexpr std::size_t kXorshiftVectorCount =
        sizeof(kXorshiftVectors) / sizeof(kXorshiftVectors[0]);

}  // namespace homemoney::sync::vectors

#endif  // HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H
