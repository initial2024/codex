from __future__ import annotations

import hashlib
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "data/ime_sources/mature_sources.json"
CEDICT_RE = re.compile(r"^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.*)/$")
TONE_RE = re.compile(r"[1-5]")
NON_PINYIN_RE = re.compile(r"[^a-zv]+")


def git_blob_sha1(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def normalize_pinyin(value: str) -> str:
    value = value.lower().replace("ü", "v").replace("u:", "v")
    value = TONE_RE.sub("", value)
    return NON_PINYIN_RE.sub("", value)


def main() -> int:
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    spec = config["sources"]["cedict"]
    req = urllib.request.Request(spec["url"], headers={"User-Agent": "Orbit-IME-v027-CEDICT-audit"})
    with urllib.request.urlopen(req, timeout=120) as response:
        raw = response.read()
    actual = git_blob_sha1(raw)
    expected = str(spec["git_blob_sha1"]).lower()
    if actual != expected:
        raise RuntimeError(f"CEDICT blob mismatch: expected {expected}, got {actual}")

    structural = 0
    runtime_accepted = 0
    runtime_unique: set[tuple[str, str]] = set()
    noncomment = 0
    for raw_line in raw.decode("utf-8-sig").splitlines():
        if not raw_line or raw_line.startswith("#"):
            continue
        noncomment += 1
        match = CEDICT_RE.match(raw_line.strip())
        if not match:
            continue
        structural += 1
        _trad, simp, pinyin, _gloss = match.groups()
        normalized = normalize_pinyin(pinyin)
        if normalized and simp:
            runtime_accepted += 1
            runtime_unique.add((normalized, simp))

    print(json.dumps({
        "blob_sha1": actual,
        "noncomment_lines": noncomment,
        "structural_cedict_rows": structural,
        "runtime_accepted_rows": runtime_accepted,
        "runtime_unique_pairs": len(runtime_unique),
        "minimum_cedict_entries": int(config["policy"]["minimum_cedict_entries"]),
    }, ensure_ascii=False, indent=2))
    if runtime_accepted < int(config["policy"]["minimum_cedict_entries"]):
        raise RuntimeError("Pinned CEDICT source itself does not meet minimum_cedict_entries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
