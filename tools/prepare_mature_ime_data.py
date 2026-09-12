#!/usr/bin/env python3
"""Prepare reproducible base Orbit IME assets.

This stage downloads and verifies only the base AOSP/Jieba/ESDB sources.
v0.18 CC-CEDICT and Unicode Emoji are added by augment_v018_data.py.
The installed keyboard itself still has no INTERNET permission.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "data/ime_sources/mature_sources.json"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/ime"
DEFAULT_CACHE = ROOT / "build/ime-mature/vendor"
DEFAULT_STAGING = ROOT / "build/ime-mature/staging"
ASCII_ENGLISH_RE = re.compile(r"^[a-z]+(?:'[a-z]+)?(?:-[a-z]+)*$")
LETTERS_RE = re.compile(r"[^a-z]+")
MAX_COUNT = 2_000_000_000
BASE_PIN_KEYS = (
    "aosp_pinyin",
    "aosp_notice",
    "jieba_dict",
    "jieba_license",
    "esdb_en_us",
    "esdb_copyright",
)


def git_blob_sha1(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def download_verified(spec: dict, target: Path, allow_download: bool = True) -> bytes:
    expected = str(spec["git_blob_sha1"]).lower()
    if target.is_file():
        data = target.read_bytes()
        if git_blob_sha1(data) == expected:
            return data
        target.unlink()
    if not allow_download:
        raise RuntimeError(f"verified cache missing for {spec['name']}")
    request = urllib.request.Request(str(spec["url"]), headers={"User-Agent": "Orbit-IME-build-data/0.18"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    actual = git_blob_sha1(data)
    if actual != expected:
        raise RuntimeError(f"Git blob hash mismatch for {spec['name']}: expected {expected}, got {actual}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data


def is_cjk(char: str) -> bool:
    code = ord(char)
    return 0x3400 <= code <= 0x4DBF or 0x4E00 <= code <= 0x9FFF or 0xF900 <= code <= 0xFAFF


def clamp(value: int | float) -> int:
    return max(1, min(MAX_COUNT, int(round(value))))


def normalized_syllable(raw: str) -> str:
    return LETTERS_RE.sub("", raw.lower().replace("ü", "v"))


def decode_aosp_dictionary(raw: bytes) -> str:
    """Decode the pinned AOSP PinyinIME dictionary without changing source verification.

    The current rawdict_utf16_65105_freq.txt is UTF-16 with a BOM, while older
    compatible source snapshots may be UTF-8/UTF-8-SIG. Hash verification is
    intentionally performed on the original bytes before this decoding step.
    """
    if raw.startswith((b"\xff\xfe", b"\xfe\xff")):
        return raw.decode("utf-16")
    return raw.decode("utf-8-sig")


def iter_aosp_rows(raw: bytes):
    for line in decode_aosp_dictionary(raw).splitlines():
        parts = line.strip().split()
        if len(parts) < 4:
            continue
        text, raw_freq, flag = parts[0], parts[1], parts[2]
        if flag != "0" or not text or not all(is_cjk(ch) for ch in text):
            continue
        syllables = [normalized_syllable(value) for value in parts[3:]]
        if not syllables or any(not syllable for syllable in syllables):
            continue
        try:
            frequency = float(raw_freq)
        except ValueError:
            continue
        yield text, syllables, frequency


def parse_aosp_pinyin(raw: bytes, output_tsv: Path, ngram_tsv: Path, frequency_scale: int, max_phrase_chars: int, ngram_limits: dict[str, int]) -> dict[str, int]:
    lexicon: dict[tuple[str, str], int] = {}
    grams: dict[int, dict[tuple[str, ...], int]] = {1: defaultdict(int), 2: defaultdict(int), 3: defaultdict(int)}
    accepted = 0
    for text, syllables, raw_frequency in iter_aosp_rows(raw):
        if not (1 <= len(text) <= max_phrase_chars):
            continue
        accepted += 1
        pinyin = "".join(syllables)
        frequency = clamp(raw_frequency * frequency_scale)
        key = (pinyin, text)
        lexicon[key] = max(frequency, lexicon.get(key, 0))
        chars = list(text)
        for n in (1, 2, 3):
            for index in range(max(0, len(chars) - n + 1)):
                gram = tuple(chars[index:index + n])
                grams[n][gram] = clamp(grams[n][gram] + frequency)

    output_tsv.parent.mkdir(parents=True, exist_ok=True)
    with output_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Derived from pinned AOSP PinyinIME raw dictionary.\n")
        handle.write("# pinyin<TAB>text<TAB>frequency\n")
        for (pinyin, text), frequency in sorted(lexicon.items(), key=lambda item: (item[0][0], -item[1], item[0][1])):
            handle.write(f"{pinyin}\t{text}\t{frequency}\n")

    selected_grams: list[tuple[tuple[str, ...], int]] = []
    gram_counts: dict[str, int] = {}
    for n in (1, 2, 3):
        limit = int(ngram_limits[str(n)])
        rows = sorted(grams[n].items(), key=lambda item: (-item[1], item[0]))[:limit]
        gram_counts[str(n)] = len(rows)
        selected_grams.extend(rows)
    with ngram_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Character N-gram counts derived from pinned AOSP PinyinIME phrases.\n")
        for tokens, count in selected_grams:
            handle.write("\t".join(tokens) + f"\t{count}\n")

    return {
        "aosp_lexicon_entries": len(lexicon),
        "aosp_accepted_rows": accepted,
        "ngram1": gram_counts["1"],
        "ngram2": gram_counts["2"],
        "ngram3": gram_counts["3"],
    }


def build_aosp_pronunciation_index(raw: bytes, polyphone_ratio: float) -> tuple[dict[str, tuple[str, float]], dict[str, str], set[str]]:
    char_readings: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
    phrase_readings: dict[str, tuple[str, float]] = {}
    aosp_texts: set[str] = set()
    for text, syllables, frequency in iter_aosp_rows(raw):
        aosp_texts.add(text)
        if len(text) == 1 and len(syllables) == 1:
            char_readings[text][syllables[0]] += frequency
        if len(text) == len(syllables):
            joined = "".join(syllables)
            old = phrase_readings.get(text)
            if old is None or frequency > old[1]:
                phrase_readings[text] = (joined, frequency)

    safe_chars: dict[str, tuple[str, float]] = {}
    for char, readings in char_readings.items():
        ranked = sorted(readings.items(), key=lambda item: (-item[1], item[0]))
        if ranked and (len(ranked) == 1 or ranked[0][1] >= ranked[1][1] * polyphone_ratio):
            safe_chars[char] = ranked[0]
    return safe_chars, {text: value[0] for text, value in phrase_readings.items()}, aosp_texts


def parse_jieba_chinese(
    raw: bytes,
    aosp_raw: bytes,
    output_tsv: Path,
    ngram_tsv: Path,
    frequency_scale: int,
    min_phrase_chars: int,
    max_phrase_chars: int,
    polyphone_ratio: float,
    ngram_limits: dict[str, int],
) -> dict[str, int]:
    safe_chars, exact_aosp_pinyin, aosp_texts = build_aosp_pronunciation_index(aosp_raw, polyphone_ratio)
    records: dict[tuple[str, str], int] = {}
    grams: dict[int, dict[tuple[str, ...], int]] = {1: defaultdict(int), 2: defaultdict(int), 3: defaultdict(int)}
    source_rows = skipped_no_reading = skipped_existing = 0

    for raw_line in raw.decode("utf-8-sig").splitlines():
        parts = raw_line.strip().split()
        if len(parts) < 2:
            continue
        word = parts[0]
        if not (min_phrase_chars <= len(word) <= max_phrase_chars) or not all(is_cjk(ch) for ch in word):
            continue
        try:
            raw_frequency = int(parts[1])
        except ValueError:
            continue
        source_rows += 1
        if word in aosp_texts:
            skipped_existing += 1
            continue

        pinyin = exact_aosp_pinyin.get(word)
        if not pinyin:
            readings: list[str] = []
            for char in word:
                safe = safe_chars.get(char)
                if safe is None:
                    readings = []
                    break
                readings.append(safe[0])
            if not readings:
                skipped_no_reading += 1
                continue
            pinyin = "".join(readings)

        frequency = clamp(max(3000, raw_frequency * frequency_scale))
        records[(pinyin, word)] = max(frequency, records.get((pinyin, word), 0))
        chars = list(word)
        for n in (1, 2, 3):
            for index in range(max(0, len(chars) - n + 1)):
                gram = tuple(chars[index:index + n])
                grams[n][gram] = clamp(grams[n][gram] + frequency)

    output_tsv.parent.mkdir(parents=True, exist_ok=True)
    with output_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Derived from pinned Jieba MIT frequency dictionary using conservative AOSP readings.\n")
        handle.write("# pinyin<TAB>text<TAB>frequency\n")
        for (pinyin, text), frequency in sorted(records.items(), key=lambda item: (item[0][0], -item[1], item[0][1])):
            handle.write(f"{pinyin}\t{text}\t{frequency}\n")

    selected: list[tuple[tuple[str, ...], int]] = []
    counts: dict[str, int] = {}
    for n in (1, 2, 3):
        limit = int(ngram_limits[str(n)])
        rows = sorted(grams[n].items(), key=lambda item: (-item[1], item[0]))[:limit]
        counts[str(n)] = len(rows)
        selected.extend(rows)
    with ngram_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Character N-gram counts derived from accepted Jieba words.\n")
        for tokens, count in selected:
            handle.write("\t".join(tokens) + f"\t{count}\n")

    return {
        "jieba_source_rows": source_rows,
        "jieba_generated_entries": len(records),
        "jieba_skipped_existing_aosp": skipped_existing,
        "jieba_skipped_ambiguous_or_missing_reading": skipped_no_reading,
        "jieba_ngram1": counts["1"],
        "jieba_ngram2": counts["2"],
        "jieba_ngram3": counts["3"],
        "safe_aosp_character_readings": len(safe_chars),
    }


def parse_esdb_english(raw: bytes, output_tsv: Path, min_key_length: int, max_key_length: int, base_frequency: int) -> dict[str, int]:
    records: dict[str, tuple[int, str | None]] = {}
    for raw_line in raw.decode("utf-8-sig").splitlines():
        word = raw_line.strip()
        if not word or word != word.lower() or not ASCII_ENGLISH_RE.fullmatch(word):
            continue
        key = LETTERS_RE.sub("", word)
        if not (min_key_length <= len(key) <= max_key_length):
            continue
        frequency = max(4_000, base_frequency - max(0, len(key) - 4) * 900)
        candidate = word if word != key else None
        previous = records.get(key)
        if previous is None or frequency > previous[0]:
            records[key] = (frequency, candidate)

    output_tsv.parent.mkdir(parents=True, exist_ok=True)
    with output_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Derived from pinned ESDB/SCOWL generated word list.\n")
        handle.write("# key<TAB>frequency<TAB>optional display candidate\n")
        for key, (frequency, candidate) in sorted(records.items()):
            handle.write(f"{key}\t{frequency}" + (f"\t{candidate}" if candidate else "") + "\n")
    return {"esdb_english_entries": len(records)}


def copy_project_seed(staging: Path) -> None:
    source_dir = ROOT / "data/ime_sources"
    for name in ("seed_lexicon.tsv", "seed_english.tsv", "seed_ngram.tsv"):
        shutil.copy2(source_dir / name, staging / name)


def write_import_manifest(staging: Path, sources: dict) -> Path:
    manifest = {"sources": [
        {"name": "orbit-project-lexicon", "path": "seed_lexicon.tsv", "format": "orbit-tsv", "license": "PROJECT", "url": "", "redistribution_allowed": True, "attribution": "Orbit IME project-authored seed data", "default_frequency": 1000},
        {"name": "aosp-pinyinime-rawdict", "path": "aosp_pinyin.tsv", "format": "orbit-tsv", "license": "Apache-2.0", "url": sources["aosp_pinyin"]["url"], "redistribution_allowed": True, "attribution": sources["aosp_pinyin"]["attribution"]},
        {"name": "jieba-frequency-derived-pinyin", "path": "jieba_pinyin.tsv", "format": "orbit-tsv", "license": "MIT", "url": sources["jieba_dict"]["url"], "redistribution_allowed": True, "attribution": sources["jieba_dict"]["attribution"]},
        {"name": "orbit-project-english", "path": "seed_english.tsv", "format": "english-tsv", "license": "PROJECT", "url": "", "redistribution_allowed": True, "attribution": "Orbit IME project-authored English seed data"},
        {"name": "esdb-scowl-en-us-large", "path": "esdb_en_us.tsv", "format": "english-tsv", "license": "ESDB-2026", "url": sources["esdb_en_us"]["url"], "redistribution_allowed": True, "attribution": sources["esdb_en_us"]["attribution"]},
        {"name": "orbit-project-ngram", "path": "seed_ngram.tsv", "format": "ngram-tsv", "license": "PROJECT", "url": "", "redistribution_allowed": True, "attribution": "Orbit IME project-authored N-gram seed data"},
        {"name": "aosp-derived-character-ngram", "path": "aosp_ngram.tsv", "format": "ngram-tsv", "license": "Apache-2.0", "url": sources["aosp_pinyin"]["url"], "redistribution_allowed": True, "attribution": sources["aosp_pinyin"]["attribution"]},
        {"name": "jieba-derived-character-ngram", "path": "jieba_ngram.tsv", "format": "ngram-tsv", "license": "MIT", "url": sources["jieba_dict"]["url"], "redistribution_allowed": True, "attribution": sources["jieba_dict"]["attribution"]},
    ]}
    path = staging / "mature_import_manifest.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def copy_notices(output: Path, aosp_notice: bytes, esdb_notice: bytes, jieba_license: bytes) -> None:
    notice_dir = output / "third_party_notices"
    notice_dir.mkdir(parents=True, exist_ok=True)
    (notice_dir / "AOSP-PinyinIME-NOTICE.txt").write_bytes(aosp_notice)
    (notice_dir / "ESDB-SCOWL-Copyright.txt").write_bytes(esdb_notice)
    (notice_dir / "Jieba-LICENSE.txt").write_bytes(jieba_license)


def base_pin_payload(spec: dict) -> dict[str, str]:
    return {
        "url": str(spec["url"]),
        "git_blob_sha1": str(spec["git_blob_sha1"]),
        "license": str(spec["license"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare pinned mature Orbit IME base data")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE))
    parser.add_argument("--staging-dir", default=str(DEFAULT_STAGING))
    parser.add_argument("--offline", action="store_true", help="use verified cache only")
    args = parser.parse_args()

    config = json.loads(Path(args.config).resolve().read_text(encoding="utf-8"))
    sources, policy = config["sources"], config["policy"]
    cache = Path(args.cache_dir).resolve()
    staging = Path(args.staging_dir).resolve()
    output = Path(args.output).resolve()
    staging.mkdir(parents=True, exist_ok=True)
    copy_project_seed(staging)

    aosp_raw = download_verified(sources["aosp_pinyin"], cache / "aosp_rawdict.txt", not args.offline)
    aosp_notice = download_verified(sources["aosp_notice"], cache / "AOSP_NOTICE.txt", not args.offline)
    jieba_raw = download_verified(sources["jieba_dict"], cache / "jieba_dict.txt", not args.offline)
    jieba_license = download_verified(sources["jieba_license"], cache / "Jieba_LICENSE.txt", not args.offline)
    esdb_raw = download_verified(sources["esdb_en_us"], cache / "esdb_en_US_large.txt", not args.offline)
    esdb_notice = download_verified(sources["esdb_copyright"], cache / "ESDB_Copyright.txt", not args.offline)

    ngram_limits = {str(k): int(v) for k, v in policy["ngram_limits"].items()}
    stats: dict[str, int] = {}
    stats.update(parse_aosp_pinyin(aosp_raw, staging / "aosp_pinyin.tsv", staging / "aosp_ngram.tsv", int(policy["aosp_frequency_scale"]), int(policy["aosp_max_phrase_chars"]), ngram_limits))
    stats.update(parse_jieba_chinese(jieba_raw, aosp_raw, staging / "jieba_pinyin.tsv", staging / "jieba_ngram.tsv", int(policy["jieba_frequency_scale"]), int(policy["jieba_min_phrase_chars"]), int(policy["jieba_max_phrase_chars"]), float(policy["jieba_polyphone_ratio"]), ngram_limits))
    stats.update(parse_esdb_english(esdb_raw, staging / "esdb_en_us.tsv", int(policy["english_min_key_length"]), int(policy["english_max_key_length"]), int(policy["english_base_frequency"])))

    manifest = write_import_manifest(staging, sources)
    importer = ROOT / "tools/ime_importer.py"
    result = subprocess.run([sys.executable, str(importer), "--manifest", str(manifest), "--output", str(output)], cwd=str(ROOT), check=False)
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    copy_notices(output, aosp_notice, esdb_notice, jieba_license)
    runtime_manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    report = {
        "format": "ORBIT_MATURE_IME_DATA",
        "version": 3,
        "stats": stats,
        "runtime_counts": runtime_manifest.get("counts", {}),
        "pins": {key: base_pin_payload(sources[key]) for key in BASE_PIN_KEYS},
    }
    (output / "mature-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
