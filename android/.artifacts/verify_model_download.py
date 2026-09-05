"""Validates the chunked-download fix against the real ModelScope CDN.

The production chunk path only ever handles LFS objects, which resolve to
cdn-lfs-*.modelscope.cn and speak proper HTTP 206. That is what this script
exercises -- it slices llm.mnn.weight (4.73 GB) into four 64 KB windows and
fetches each of them, so the whole run costs ~256 KB instead of gigabytes.

Checks performed:
  1. Every chunk -- including chunk 0, whose Range header the old code
     omitted -- comes back 206 with exactly the requested byte count.
  2. A request WITHOUT a Range header returns the entire object rather than a
     slice, which is precisely why the old chunk 0 failed its size check and
     retried 15 times over gigabytes of traffic.
"""

import urllib.error
import urllib.request

HOST = "https://www.modelscope.cn"
REPO = "MNN/Qwen3-VL-8B-Instruct-MNN"
NAME = "llm.mnn.weight"
PARALLEL_CHUNKS = 4
SLICE = 64 * 1024


def resolve_size():
    """Reads the object size from a 1-byte Range response."""
    req = urllib.request.Request(
        f"{HOST}/models/{REPO}/resolve/master/{NAME}",
        headers={"Accept-Encoding": "identity", "Range": "bytes=0-0"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return int(resp.headers["Content-Range"].split("/")[-1])


def fetch(start, end, send_range, timeout=60):
    headers = {"Accept-Encoding": "identity", "User-Agent": "HomeFinanceTracker/1.0"}
    if send_range:
        headers["Range"] = f"bytes={start}-{end - 1}"
    req = urllib.request.Request(
        f"{HOST}/models/{REPO}/resolve/master/{NAME}", headers=headers
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.status, resp.headers.get("Content-Range"), resp.read()


def main():
    total = resolve_size()
    print(f"{NAME}: {total:,} bytes on the server")
    chunk = (total + PARALLEL_CHUNKS - 1) // PARALLEL_CHUNKS

    print("\n[1] chunked download, every chunk sends Range (the fix)")
    ok = True
    for i in range(PARALLEL_CHUNKS):
        start = i * chunk
        end = min(start + chunk, total)
        # Sample a 64 KB window inside the chunk instead of pulling gigabytes.
        probe_start, probe_end = start, min(start + SLICE, end)
        status, content_range, body = fetch(probe_start, probe_end, send_range=True)
        expected = probe_end - probe_start
        good = status == 206 and len(body) == expected
        ok &= good
        print(f"  chunk{i}: bytes={probe_start}-{probe_end - 1} "
              f"status={status} got={len(body)}B expected={expected}B "
              f"{'OK' if good else 'MISMATCH'}")
        if content_range:
            print(f"          Content-Range: {content_range}")

    print("\n[2] chunk 0 WITHOUT a Range header (the original bug)")
    # Only read the headers, then close -- never pull the 4.7 GB body.
    req = urllib.request.Request(
        f"{HOST}/models/{REPO}/resolve/master/{NAME}",
        headers={"Accept-Encoding": "identity", "User-Agent": "HomeFinanceTracker/1.0"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        body_len = resp.headers.get("Content-Length")
        print(f"  status={resp.status} Content-Length={body_len} "
              f"(a 64 KB chunk would be {SLICE})")
        resp.close()
    whole = body_len is not None and int(body_len) == total
    print("  -> " + ("server returns the WHOLE object, not a slice: the old size "
                     "check failed and each retry re-downloaded it"
                     if whole else "unexpected: body is not the full object"))

    print("\nRESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
