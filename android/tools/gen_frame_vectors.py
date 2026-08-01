#!/usr/bin/env python3
"""Generate the golden frame vectors shared by the C++ and Kotlin sync implementations.

This script is deliberately a *third*, independent implementation of the wire format
described in docs/sync/REFACTOR_PLAN.md section 3.1. The vectors it emits are therefore
not derived from either production codec, so a bug that exists in both of them cannot
quietly validate itself.

Outputs (all checked in, regenerate whenever the frame layout or backoff curve changes):
  app/src/main/cpp/protocol/frame_vectors.txt            single source of truth, read by
                                                         the Kotlin ProtocolConformanceTest
  app/src/main/cpp/protocol/frame_vectors_generated.h    constexpr mirror, verified by the
                                                         C++ compile time assertions
  app/src/main/cpp/protocol/retry_vectors.txt            backoff curve, read by the Kotlin
                                                         SyncRetryPolicyTest
  app/src/main/cpp/protocol/retry_vectors_generated.h    constexpr mirror for the C++
                                                         transport assertions

Usage:
  python tools/gen_frame_vectors.py
"""

from __future__ import annotations

import struct
from pathlib import Path

MAGIC = 0x48465331
VERSION = 2
HEADER_SIZE = 32

# Reflected CRC-32C (Castagnoli) polynomial.
CRC32C_POLY = 0x82F63B78

# Opcodes, mirrored from sync_protocol.h.
OP_HELLO = 0x01
OP_HELLO_ACK = 0x02
OP_AUTH = 0x03
OP_MANIFEST = 0x10
OP_CHUNK = 0x12
OP_CHUNK_ACK = 0x13
OP_COMMIT = 0x20
OP_PING = 0x30
OP_ERROR = 0x40
OP_BYE = 0x41

# Flag bits, mirrored from sync_protocol.h.
FLAG_NONE = 0x0000
FLAG_COMPRESSED = 0x0001
FLAG_LAST_CHUNK = 0x0002
FLAG_RESUMED = 0x0004
FLAG_REQUIRE_ACK = 0x0008


def _crc32c_table() -> list[int]:
    table = []
    for i in range(256):
        crc = i
        for _ in range(8):
            crc = (crc >> 1) ^ CRC32C_POLY if crc & 1 else crc >> 1
        table.append(crc)
    return table


_TABLE = _crc32c_table()


def crc32c(data: bytes) -> int:
    """One-shot CRC-32C. crc32c(b"123456789") == 0xE3069283."""
    crc = 0xFFFFFFFF
    for byte in data:
        crc = _TABLE[(crc ^ byte) & 0xFF] ^ (crc >> 8)
    return crc ^ 0xFFFFFFFF


def encode_header(opcode: int, flags: int, session_id: int, seq: int, payload: bytes) -> bytes:
    """Build a 32 byte frame header, written straight from the spec table."""
    partial = struct.pack(
        ">IBBHQIII",
        MAGIC,
        VERSION,
        opcode,
        flags,
        session_id,
        seq,
        len(payload),
        crc32c(payload),
    )
    assert len(partial) == HEADER_SIZE - 4, f"unexpected partial header size {len(partial)}"
    return partial + struct.pack(">I", crc32c(partial))


class Vector:
    def __init__(self, name: str, opcode: int, flags: int, session_id: int, seq: int,
                 payload: bytes) -> None:
        self.name = name
        self.opcode = opcode
        self.flags = flags
        self.session_id = session_id
        self.seq = seq
        self.payload = payload
        self.header = encode_header(opcode, flags, session_id, seq, payload)


def build_vectors() -> list[Vector]:
    """Cases chosen to pin down endianness, field offsets and every boundary value."""
    return [
        # Everything zero: catches a field written at the wrong offset only if the
        # checksum moves, which it does, so this is the cheapest smoke vector.
        Vector("ping_empty", OP_PING, FLAG_NONE, 0, 0, b""),
        # Distinct byte in every position of the 64 bit session id: catches any endianness
        # or offset mistake in the widest field.
        Vector("hello_require_ack", OP_HELLO, FLAG_REQUIRE_ACK, 0x0123456789ABCDEF, 1,
               b'{"protocolVersion":2}'),
        Vector("hello_ack", OP_HELLO_ACK, FLAG_NONE, 0x0123456789ABCDEF, 2,
               b'{"accepted":true}'),
        # All-ones session id and seq: catches signed/unsigned confusion on the JVM, where
        # Long and Int are signed and a naive implementation overflows here.
        Vector("chunk_last_compressed", OP_CHUNK, FLAG_COMPRESSED | FLAG_LAST_CHUNK,
               0xFFFFFFFFFFFFFFFF, 0xFFFFFFFF, bytes(range(256))),
        Vector("chunk_ack", OP_CHUNK_ACK, FLAG_NONE, 0x00000000FFFFFFFF, 0x7FFFFFFF,
               struct.pack(">I", 17)),
        Vector("manifest", OP_MANIFEST, FLAG_REQUIRE_ACK, 0x8000000000000000, 0x80000000,
               b"manifest-body"),
        Vector("commit_resumed", OP_COMMIT, FLAG_RESUMED, 1, 65535, bytes(16)),
        Vector("auth", OP_AUTH, FLAG_REQUIRE_ACK, 0x00FF00FF00FF00FF, 3, bytes(32)),
        Vector("error_crc", OP_ERROR, FLAG_NONE, 42, 7, b"CRC_MISMATCH"),
        Vector("bye", OP_BYE, FLAG_NONE, 0xDEADBEEFCAFEBABE, 12345, b""),
    ]


TXT_HEADER = """\
# Golden frame vectors for the Home Finance LAN sync protocol.
#
# GENERATED FILE - do not edit by hand. Run: python tools/gen_frame_vectors.py
#
# This file is the single source of truth for the 32 byte frame header. Both ends are
# pinned to it:
#   - Kotlin: ProtocolConformanceTest reads this file directly.
#   - C++:    frame_vectors_generated.h mirrors it and is checked at compile time.
#
# Columns, pipe separated:
#   name | version | opcode | flags | sessionId | seq | payloadHex | expectedHeaderHex
# Numeric columns are lowercase hex without a 0x prefix, except seq which is decimal.
"""


def write_txt(path: Path, vectors: list[Vector]) -> None:
    lines = [TXT_HEADER]
    for v in vectors:
        lines.append(
            "|".join([
                v.name,
                f"{VERSION:02x}",
                f"{v.opcode:02x}",
                f"{v.flags:04x}",
                f"{v.session_id:016x}",
                str(v.seq),
                v.payload.hex(),
                v.header.hex(),
            ])
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


MAX_VECTOR_PAYLOAD = 256


def write_header(path: Path, vectors: list[Vector]) -> None:
    rows = []
    for v in vectors:
        assert len(v.payload) <= MAX_VECTOR_PAYLOAD, f"{v.name} payload too long"
        expected = ", ".join(f"0x{b:02X}" for b in v.header)
        payload = ", ".join(f"0x{b:02X}" for b in v.payload) if v.payload else ""
        rows.append(
            "    {{ \"{name}\", {version}, 0x{opcode:02X}, 0x{flags:04X}, "
            "0x{session:016X}ULL, {seq}U, {plen}U, 0x{pcrc:08X}U,\n"
            "      {{ {expected} }},\n"
            "      {{ {payload} }} }},".format(
                name=v.name,
                version=VERSION,
                opcode=v.opcode,
                flags=v.flags,
                session=v.session_id,
                seq=v.seq,
                plen=len(v.payload),
                pcrc=crc32c(v.payload),
                expected=expected,
                payload=payload,
            )
        )

    body = "\n".join(rows)
    path.write_text(
        f"""#ifndef HOMEMONEY_SYNC_FRAME_VECTORS_GENERATED_H
#define HOMEMONEY_SYNC_FRAME_VECTORS_GENERATED_H

#include <array>
#include <cstdint>

// GENERATED FILE - do not edit by hand. Run: python tools/gen_frame_vectors.py
//
// Mirror of protocol/frame_vectors.txt, which is the single source of truth shared with
// the Kotlin side. protocol_conformance.cpp asserts every entry at compile time, so a
// change to the frame layout that is not reflected here fails the native build.

namespace homemoney::sync::vectors {{

struct FrameVector {{
    const char* name;
    std::uint8_t version;
    std::uint8_t opcode;
    std::uint16_t flags;
    std::uint64_t sessionId;
    std::uint32_t seq;
    std::uint32_t payloadLen;
    std::uint32_t payloadCrc32;
    std::array<std::uint8_t, 32> expected;
    /// Payload bytes, zero padded. Only the first payloadLen entries are meaningful.
    std::array<std::uint8_t, {MAX_VECTOR_PAYLOAD}> payload;
}};

inline constexpr FrameVector kFrameVectors[] = {{
{body}
}};

inline constexpr std::size_t kFrameVectorCount =
        sizeof(kFrameVectors) / sizeof(kFrameVectors[0]);

}}  // namespace homemoney::sync::vectors

#endif  // HOMEMONEY_SYNC_FRAME_VECTORS_GENERATED_H
""",
        encoding="utf-8",
    )


# ----------------------------------------------------------------- retry / backoff

# Mirrored from transport/retry_policy.h.
MAX_BACKOFF_SHIFT = 16
MASK32 = 0xFFFFFFFF


def backoff_ceiling_ms(base_delay_ms: int, max_delay_ms: int, retry_index: int) -> int:
    """Unjittered ceiling, written straight from the spec rather than ported from C++."""
    if base_delay_ms == 0:
        return 0
    shift = min(retry_index, MAX_BACKOFF_SHIFT)
    scaled = base_delay_ms << shift
    return max_delay_ms if scaled >= max_delay_ms else scaled


def jittered_delay_ms(base_delay_ms: int, max_delay_ms: int, retry_index: int,
                      random_value: int) -> int:
    """Equal jitter: guaranteed half the ceiling, plus a random share of the rest."""
    ceiling = backoff_ceiling_ms(base_delay_ms, max_delay_ms, retry_index)
    if ceiling == 0:
        return 0
    half = ceiling // 2
    span = ceiling - half
    return half + (random_value % (span + 1))


def xorshift32(state: int) -> int:
    """32 bit xorshift. Pinned because the JVM has no unsigned int and it is easy to get
    the logical vs arithmetic right shift wrong there."""
    state &= MASK32
    state ^= (state << 13) & MASK32
    state ^= state >> 17
    state ^= (state << 5) & MASK32
    return state & MASK32


class RetryVector:
    def __init__(self, name: str, base: int, cap: int, retry_index: int,
                 random_value: int) -> None:
        self.name = name
        self.base = base
        self.cap = cap
        self.retry_index = retry_index
        self.random_value = random_value
        self.ceiling = backoff_ceiling_ms(base, cap, retry_index)
        self.delay = jittered_delay_ms(base, cap, retry_index, random_value)


def build_retry_vectors() -> list[RetryVector]:
    """Covers the default curve, the cap, the odd-ceiling rounding and the extremes."""
    default_base, default_cap = 250, 8000
    out = [
        # The production curve, one row per retry, with jitter at both extremes so the
        # lower bound (random 0) and the upper bound (random 0xFFFFFFFF) are both pinned.
        RetryVector("default_r0_lo", default_base, default_cap, 0, 0),
        RetryVector("default_r0_hi", default_base, default_cap, 0, MASK32),
        RetryVector("default_r1_mid", default_base, default_cap, 1, 12345),
        RetryVector("default_r2_mid", default_base, default_cap, 2, 999983),
        RetryVector("default_r3_mid", default_base, default_cap, 3, 0x5EED1234),
        # Saturation: 250 << 5 = 8000 exactly, and everything beyond stays clamped.
        RetryVector("default_r5_cap", default_base, default_cap, 5, 0),
        RetryVector("default_r9_cap", default_base, default_cap, 9, MASK32),
        # Absurd retry index must clamp rather than shift by 64 (undefined in C++).
        RetryVector("default_r99_cap", default_base, default_cap, 99, 7),
        # Odd ceiling: 125 -> half 62, span 63, so the range is [62, 125] inclusive.
        RetryVector("odd_ceiling_lo", 125, 100000, 0, 0),
        RetryVector("odd_ceiling_hi", 125, 100000, 0, MASK32),
        # Degenerate configurations must not divide by zero or return garbage.
        RetryVector("zero_base", 0, 8000, 3, 42),
        RetryVector("cap_below_base", 1000, 100, 4, 42),
        # Aggressive curve used for keepalive style reconnects.
        RetryVector("fast_r0", 50, 1000, 0, 0),
        RetryVector("fast_r4_cap", 50, 1000, 4, MASK32),
    ]
    return out


RETRY_TXT_HEADER = """\
# Golden backoff vectors for the Home Finance LAN sync retry policy.
#
# GENERATED FILE - do not edit by hand. Run: python tools/gen_frame_vectors.py
#
# Both ends must agree on the backoff curve, otherwise one device gives up while the other
# is still waiting to retry and the session dies with a misleading error.
#   - Kotlin: SyncRetryPolicyTest reads this file directly.
#   - C++:    retry_vectors_generated.h mirrors it and is checked at compile time.
#
# Columns, pipe separated (all decimal):
#   name | baseDelayMs | maxDelayMs | retryIndex | randomValue | ceilingMs | delayMs
"""

XORSHIFT_SEEDS = [1, 2, 0x9E3779B9, 0xDEADBEEF, 0x7FFFFFFF, 0x80000000, MASK32]


def write_retry_txt(path: Path, vectors: list[RetryVector]) -> None:
    lines = [RETRY_TXT_HEADER]
    for v in vectors:
        lines.append("|".join([
            v.name,
            str(v.base),
            str(v.cap),
            str(v.retry_index),
            str(v.random_value),
            str(v.ceiling),
            str(v.delay),
        ]))
    lines.append("")
    lines.append("# xorshift32 chain: name | seed | after1 | after2 | after3")
    for seed in XORSHIFT_SEEDS:
        a = xorshift32(seed)
        b = xorshift32(a)
        c = xorshift32(b)
        lines.append("|".join(["xorshift", str(seed), str(a), str(b), str(c)]))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_retry_header(path: Path, vectors: list[RetryVector]) -> None:
    rows = [
        "    {{ \"{name}\", {base}U, {cap}U, {idx}U, {rnd}U, {ceiling}U, {delay}U }},".format(
            name=v.name, base=v.base, cap=v.cap, idx=v.retry_index,
            rnd=v.random_value, ceiling=v.ceiling, delay=v.delay)
        for v in vectors
    ]
    xor_rows = []
    for seed in XORSHIFT_SEEDS:
        a = xorshift32(seed)
        b = xorshift32(a)
        c = xorshift32(b)
        xor_rows.append(f"    {{ {seed}U, {a}U, {b}U, {c}U }},")

    body = "\n".join(rows)
    xor_body = "\n".join(xor_rows)
    path.write_text(
        f"""#ifndef HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H
#define HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H

#include <cstddef>
#include <cstdint>

// GENERATED FILE - do not edit by hand. Run: python tools/gen_frame_vectors.py
//
// Mirror of protocol/retry_vectors.txt, the single source of truth for the backoff curve
// shared with the Kotlin side. transport_conformance.cpp asserts every entry at compile
// time, so a change to the policy that is not reflected here fails the native build.

namespace homemoney::sync::vectors {{

struct RetryVector {{
    const char* name;
    std::uint32_t baseDelayMs;
    std::uint32_t maxDelayMs;
    std::uint32_t retryIndex;
    std::uint32_t randomValue;
    std::uint32_t ceilingMs;
    std::uint32_t delayMs;
}};

inline constexpr RetryVector kRetryVectors[] = {{
{body}
}};

inline constexpr std::size_t kRetryVectorCount =
        sizeof(kRetryVectors) / sizeof(kRetryVectors[0]);

struct XorshiftVector {{
    std::uint32_t seed;
    std::uint32_t after1;
    std::uint32_t after2;
    std::uint32_t after3;
}};

inline constexpr XorshiftVector kXorshiftVectors[] = {{
{xor_body}
}};

inline constexpr std::size_t kXorshiftVectorCount =
        sizeof(kXorshiftVectors) / sizeof(kXorshiftVectors[0]);

}}  // namespace homemoney::sync::vectors

#endif  // HOMEMONEY_SYNC_RETRY_VECTORS_GENERATED_H
""",
        encoding="utf-8",
    )


def main() -> None:
    # Sanity check the primitive before it is used to bless anything else.
    assert crc32c(b"123456789") == 0xE3069283, "CRC-32C implementation is wrong"

    root = Path(__file__).resolve().parent.parent
    out_dir = root / "app" / "src" / "main" / "cpp" / "protocol"
    out_dir.mkdir(parents=True, exist_ok=True)

    vectors = build_vectors()
    write_txt(out_dir / "frame_vectors.txt", vectors)
    write_header(out_dir / "frame_vectors_generated.h", vectors)

    retry_vectors = build_retry_vectors()
    write_retry_txt(out_dir / "retry_vectors.txt", retry_vectors)
    write_retry_header(out_dir / "retry_vectors_generated.h", retry_vectors)

    print(f"wrote {len(vectors)} frame vectors and {len(retry_vectors)} retry vectors "
          f"to {out_dir}")


if __name__ == "__main__":
    main()
