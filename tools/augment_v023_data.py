#!/usr/bin/env python3
"""Orbit IME v0.23 vocabulary/ranking augmentation.

Runs after augment_v020_data.py and adds:
- THUOCL domain vocabulary with conservative pronunciation derivation;
- FrequencyWords zh/en usage-frequency overlays;
- a precomputed next-association asset for low-latency post-commit suggestions;
- pinned third-party notices and v0.23 statistics.

The installed IME remains fully offline. All downloads happen only on the build machine.
"""
from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

import prepare_mature_ime_data as base

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "data/ime_sources/mature_sources.json"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/ime"
DEFAULT_STAGING = ROOT / "build/ime-mature/staging"
DEFAULT_CACHE = ROOT / "build/ime-mature/vendor"
MAX_COUNT = 2_000_000_000
THUOCL_KEYS = (
    "thuocl_it", "thuocl_animal", "thuocl_finance", "thuocl_car", "thuocl_idiom",
    "thuocl_place", "thuocl_food", "thuocl_law", "thuocl_people", "thuocl_medical", "thuocl_poem",
)


def is_cjk_text(text: str) -> bool:
    return bool(text) and all(
        0x3400 <= ord(ch) <= 0x4DBF or 0x4E00 <= ord(ch) <= 0x9FFF or 0xF900 <= ord(ch) <= 0xFAFF
        for ch in text
    )


def clamp(value: int | float) -> int:
    return max(1, min(MAX_COUNT, int(round(value))))


def parse_orbit_lexicon(path: Path) -> list[tuple[str, str, int]]:
    rows: list[tuple[str, str, int]] = []
    if not path.is_file():
        return rows
    with path.open("r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 3:
                continue
            pinyin, text = parts[0].strip(), parts[1].strip()
            try:
                frequency = int(parts[2])
            except ValueError:
                continue
            if pinyin and text:
                rows.append((pinyin, text, max(1, frequency)))
    return rows


def build_pronunciation_index(staging: Path) -> tuple[dict[str, str], dict[str, str]]:
    """Build exact word and conservative single-character reading indexes from prior stages."""
    manifest = staging / "mature_import_manifest_v020.json"
    if not manifest.is_file():
        raise RuntimeError("mature_import_manifest_v020.json missing; run augment_v020_data.py first")
    payload = json.loads(manifest.read_text(encoding="utf-8"))
    exact_best: dict[str, tuple[str, int]] = {}
    char_counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for source in payload.get("sources", []):
        if source.get("format") != "orbit-tsv":
            continue
        path = staging / str(source.get("path", ""))
        for pinyin, text, freq in parse_orbit_lexicon(path):
            if not is_cjk_text(text):
                continue
            old = exact_best.get(text)
            if old is None or freq > old[1]:
                exact_best[text] = (pinyin, freq)
            if len(text) == 1:
                char_counts[text][pinyin] += freq

    chars: dict[str, str] = {}
    for char, readings in char_counts.items():
        ranked = sorted(readings.items(), key=lambda item: (-item[1], item[0]))
        if not ranked:
            continue
        if len(ranked) == 1 or ranked[0][1] >= ranked[1][1] * 2.2:
            chars[char] = ranked[0][0]
    return {text: value[0] for text, value in exact_best.items()}, chars


def derive_pinyin(word: str, exact: dict[str, str], chars: dict[str, str]) -> str | None:
    direct = exact.get(word)
    if direct:
        return direct
    pieces: list[str] = []
    for char in word:
        reading = chars.get(char)
        if not reading:
            return None
        pieces.append(reading)
    return "".join(pieces) if pieces else None


def parse_thuocl(raw: bytes) -> list[tuple[str, int]]:
    rows: list[tuple[str, int]] = []
    for raw_line in raw.decode("utf-8-sig", errors="ignore").splitlines():
        parts = raw_line.strip().split()
        if len(parts) < 2:
            continue
        word = parts[0].strip()
        if not (2 <= len(word) <= 16) or not is_cjk_text(word):
            continue
        try:
            freq = int(parts[-1])
        except ValueError:
            continue
        if freq > 0:
            rows.append((word, freq))
    return rows


def parse_frequency_words(raw: bytes) -> list[tuple[str, int]]:
    rows: list[tuple[str, int]] = []
    for raw_line in raw.decode("utf-8-sig", errors="ignore").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        try:
            token, raw_count = line.rsplit(maxsplit=1)
            count = int(raw_count)
        except (ValueError, TypeError):
            continue
        if token and count > 0:
            rows.append((token, count))
    return rows


def scaled_frequency(raw: int, floor: int = 4000, multiplier: float = 70000.0) -> int:
    # Preserve rank across very different corpora without allowing gigantic raw counts
    # to drown project-curated/user-learning signals.
    return clamp(max(floor, math.log1p(max(1, raw)) * multiplier))


def build_chinese_overlay(
    thuocl_rows: list[tuple[str, int]],
    frequency_rows: list[tuple[str, int]],
    exact: dict[str, str],
    chars: dict[str, str],
    target: Path,
) -> tuple[int, int, int]:
    records: dict[tuple[str, str], int] = {}
    source_count = 0
    generated_thuocl = 0
    generated_frequency = 0

    for word, raw_freq in thuocl_rows:
        source_count += 1
        pinyin = derive_pinyin(word, exact, chars)
        if not pinyin:
            continue
        key = (pinyin, word)
        value = scaled_frequency(raw_freq, floor=18000, multiplier=85000.0)
        if key not in records:
            generated_thuocl += 1
        records[key] = max(records.get(key, 0), value)

    for word, raw_freq in frequency_rows:
        if not (1 <= len(word) <= 16) or not is_cjk_text(word):
            continue
        pinyin = derive_pinyin(word, exact, chars)
        if not pinyin:
            continue
        key = (pinyin, word)
        value = scaled_frequency(raw_freq, floor=22000, multiplier=95000.0)
        if key not in records:
            generated_frequency += 1
        records[key] = max(records.get(key, 0), value)

    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# v0.23 THUOCL + FrequencyWords Chinese overlay.\n")
        handle.write("# pinyin<TAB>text<TAB>frequency\n")
        for (pinyin, text), frequency in sorted(records.items(), key=lambda item: (item[0][0], -item[1], item[0][1])):
            handle.write(f"{pinyin}\t{text}\t{frequency}\n")
    return source_count, generated_thuocl, generated_frequency


def build_english_frequency_overlay(rows: list[tuple[str, int]], target: Path) -> int:
    records: dict[str, int] = {}
    for raw_word, count in rows:
        word = raw_word.strip().lower()
        if not (2 <= len(word) <= 40):
            continue
        if not all(ch.isascii() and (ch.isalpha() or ch in "'-") for ch in word):
            continue
        key = "".join(ch for ch in word if ch.isalpha())
        if not key:
            continue
        records[key] = max(records.get(key, 0), scaled_frequency(count, floor=12000, multiplier=100000.0))
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# FrequencyWords English usage-frequency overlay; content CC BY-SA 4.0.\n")
        handle.write("# key<TAB>frequency<TAB>optional display candidate\n")
        for key, frequency in sorted(records.items()):
            handle.write(f"{key}\t{frequency}\n")
    return len(records)


def build_ngram_overlay(rows: list[tuple[str, int]], target: Path, limits: dict[str, int]) -> dict[str, int]:
    grams: dict[int, dict[tuple[str, ...], int]] = {1: defaultdict(int), 2: defaultdict(int), 3: defaultdict(int)}
    for word, raw_count in rows:
        if not (1 <= len(word) <= 20) or not is_cjk_text(word):
            continue
        weight = scaled_frequency(raw_count, floor=2000, multiplier=25000.0)
        chars = list(word)
        for n in (1, 2, 3):
            for index in range(max(0, len(chars) - n + 1)):
                gram = tuple(chars[index:index + n])
                grams[n][gram] = clamp(grams[n][gram] + weight)
    counts: dict[str, int] = {}
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# v0.23 FrequencyWords-derived Chinese character N-grams. CC BY-SA 4.0.\n")
        for n in (1, 2, 3):
            selected = sorted(grams[n].items(), key=lambda item: (-item[1], item[0]))[: int(limits[str(n)])]
            counts[str(n)] = len(selected)
            for tokens, count in selected:
                handle.write("\t".join(tokens) + f"\t{count}\n")
    return counts


def build_manifest(staging: Path, config: dict) -> Path:
    source_path = staging / "mature_import_manifest_v020.json"
    if not source_path.is_file():
        raise RuntimeError("mature_import_manifest_v020.json missing")
    payload = json.loads(source_path.read_text(encoding="utf-8"))
    sources = [item for item in payload.get("sources", []) if item.get("name") not in {
        "thuocl-frequency-overlay", "frequencywords-english-overlay", "frequencywords-chinese-ngram",
    }]
    sources.extend([
        {
            "name": "thuocl-frequency-overlay",
            "path": "v023_chinese_overlay.tsv",
            "format": "orbit-tsv",
            "license": "MIT",
            "url": "https://github.com/thunlp/THUOCL",
            "redistribution_allowed": True,
            "attribution": config["sources"]["thuocl_readme"]["attribution"],
            "default_frequency": 1000,
        },
        {
            "name": "frequencywords-english-overlay",
            "path": "v023_english_frequency.tsv",
            "format": "english-tsv",
            "license": "CC-BY-SA-4.0",
            "url": config["sources"]["frequencywords_en"]["url"],
            "redistribution_allowed": True,
            "attribution": config["sources"]["frequencywords_en"]["attribution"],
        },
        {
            "name": "frequencywords-chinese-ngram",
            "path": "v023_frequency_ngram.tsv",
            "format": "ngram-tsv",
            "license": "CC-BY-SA-4.0",
            "url": config["sources"]["frequencywords_zh"]["url"],
            "redistribution_allowed": True,
            "attribution": config["sources"]["frequencywords_zh"]["attribution"],
        },
    ])
    payload["sources"] = sources
    target = staging / "mature_import_manifest_v023.json"
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return target


def load_phrase_rows_from_manifest(staging: Path, manifest: Path) -> list[tuple[str, int]]:
    payload = json.loads(manifest.read_text(encoding="utf-8"))
    merged: dict[str, int] = {}
    for source in payload.get("sources", []):
        if source.get("format") != "orbit-tsv":
            continue
        for _pinyin, text, frequency in parse_orbit_lexicon(staging / str(source.get("path", ""))):
            if 2 <= len(text) <= 20 and is_cjk_text(text):
                merged[text] = max(merged.get(text, 0), frequency)
    return list(merged.items())


def association_shard(context: str) -> int:
    return sum(ord(ch) for ch in context) % 32


def build_association_assets(output: Path, phrases: list[tuple[str, int]]) -> int:
    buckets: dict[str, dict[str, int]] = defaultdict(dict)
    for text, frequency in phrases:
        for split in range(1, len(text)):
            prefix = text[:split]
            suffix = text[split:]
            if not suffix or len(suffix) > 12:
                continue
            for width in range(1, min(4, len(prefix)) + 1):
                context = prefix[-width:]
                old = buckets[context].get(suffix, 0)
                buckets[context][suffix] = max(old, frequency)

    association_dir = output / "association"
    if association_dir.exists():
        shutil.rmtree(association_dir)
    association_dir.mkdir(parents=True, exist_ok=True)
    shard_rows: dict[int, list[tuple[str, str, int]]] = defaultdict(list)
    total = 0
    for context, candidates in buckets.items():
        ranked = sorted(candidates.items(), key=lambda item: (-item[1], item[0]))[:48]
        for candidate, frequency in ranked:
            shard_rows[association_shard(context)].append((context, candidate, frequency))
            total += 1
    for shard in range(32):
        rows = sorted(shard_rows.get(shard, []), key=lambda item: (item[0], -item[2], item[1]))
        with (association_dir / f"{shard:02x}.odict").open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("# Orbit v0.23 precomputed next-association data.\n")
            for context, candidate, frequency in rows:
                handle.write(f"{context}\t{candidate}\t{frequency:x}\n")
    return total


def copy_notices(cache: Path, output: Path) -> None:
    notices = output / "third_party_notices"
    notices.mkdir(parents=True, exist_ok=True)
    for source_name, target_name in (
        ("thuocl_license.txt", "THUOCL-LICENSE.txt"),
        ("thuocl_readme.txt", "THUOCL-README.txt"),
        ("frequencywords_readme.txt", "FrequencyWords-README.txt"),
    ):
        source = cache / source_name
        if source.is_file():
            shutil.copy2(source, notices / target_name)


def update_report(output: Path, stats: dict, pins: dict) -> None:
    report_path = output / "mature-report.json"
    manifest_path = output / "manifest.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    runtime = json.loads(manifest_path.read_text(encoding="utf-8"))
    report["version"] = 5
    report.setdefault("stats", {}).update(stats)
    report.setdefault("pins", {}).update(pins)
    report["runtime_counts"] = runtime.get("counts", {})
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Augment Orbit IME v0.23 domain/frequency/association data")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--staging-dir", default=str(DEFAULT_STAGING))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE))
    args = parser.parse_args()

    config = json.loads(Path(args.config).resolve().read_text(encoding="utf-8"))
    policy = config["policy"]
    sources = config["sources"]
    output = Path(args.output).resolve()
    staging = Path(args.staging_dir).resolve()
    cache = Path(args.cache_dir).resolve()
    staging.mkdir(parents=True, exist_ok=True)
    cache.mkdir(parents=True, exist_ok=True)

    exact, chars = build_pronunciation_index(staging)

    thuocl_rows: list[tuple[str, int]] = []
    pins: dict[str, dict] = {}
    for key in THUOCL_KEYS:
        spec = sources[key]
        raw = base.download_verified(spec, cache / f"{key}.txt")
        thuocl_rows.extend(parse_thuocl(raw))
        pins[key] = {"git_blob_sha1": spec["git_blob_sha1"], "url": spec["url"]}
    thuocl_license = base.download_verified(sources["thuocl_license"], cache / "thuocl_license.txt")
    base.download_verified(sources["thuocl_readme"], cache / "thuocl_readme.txt")
    pins["thuocl_license"] = {"git_blob_sha1": sources["thuocl_license"]["git_blob_sha1"], "url": sources["thuocl_license"]["url"]}

    fw_en_raw = base.download_verified(sources["frequencywords_en"], cache / "frequencywords_en.txt")
    fw_zh_raw = base.download_verified(sources["frequencywords_zh"], cache / "frequencywords_zh.txt")
    base.download_verified(sources["frequencywords_readme"], cache / "frequencywords_readme.txt")
    pins["frequencywords_en"] = {"git_blob_sha1": sources["frequencywords_en"]["git_blob_sha1"], "url": sources["frequencywords_en"]["url"]}
    pins["frequencywords_zh"] = {"git_blob_sha1": sources["frequencywords_zh"]["git_blob_sha1"], "url": sources["frequencywords_zh"]["url"]}

    fw_en_rows = parse_frequency_words(fw_en_raw)
    fw_zh_rows = parse_frequency_words(fw_zh_raw)

    source_count, generated_thuocl, generated_frequency_zh = build_chinese_overlay(
        thuocl_rows, fw_zh_rows, exact, chars, staging / "v023_chinese_overlay.tsv"
    )
    frequency_en_entries = build_english_frequency_overlay(fw_en_rows, staging / "v023_english_frequency.tsv")
    ngram_counts = build_ngram_overlay(fw_zh_rows, staging / "v023_frequency_ngram.tsv", policy["ngram_limits"])

    manifest = build_manifest(staging, config)
    result = subprocess.run(
        [sys.executable, str(ROOT / "tools/ime_importer.py"), "--manifest", str(manifest), "--output", str(output)],
        cwd=str(ROOT), check=False,
    )
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    phrase_rows = load_phrase_rows_from_manifest(staging, manifest)
    association_entries = build_association_assets(output, phrase_rows)
    copy_notices(cache, output)

    stats = {
        "thuocl_source_entries": source_count,
        "thuocl_generated_entries": generated_thuocl,
        "frequencywords_zh_source_entries": len(fw_zh_rows),
        "frequencywords_zh_generated_entries": generated_frequency_zh,
        "frequencywords_en_entries": frequency_en_entries,
        "association_entries": association_entries,
        "v023_ngram1": ngram_counts.get("1", 0),
        "v023_ngram2": ngram_counts.get("2", 0),
        "v023_ngram3": ngram_counts.get("3", 0),
    }
    update_report(output, stats, pins)

    if source_count < int(policy["minimum_thuocl_source_entries"]):
        raise RuntimeError(f"THUOCL source unexpectedly small: {source_count}")
    if generated_thuocl < int(policy["minimum_thuocl_generated_entries"]):
        raise RuntimeError(f"THUOCL generated overlay too small: {generated_thuocl}")
    if frequency_en_entries < int(policy["minimum_frequencywords_en_entries"]):
        raise RuntimeError(f"FrequencyWords English overlay too small: {frequency_en_entries}")
    if len(fw_zh_rows) < int(policy["minimum_frequencywords_zh_entries"]):
        raise RuntimeError(f"FrequencyWords Chinese source too small: {len(fw_zh_rows)}")
    if association_entries < int(policy["minimum_association_entries"]):
        raise RuntimeError(f"association pack too small: {association_entries}")

    print(json.dumps({"status": "PASS", **stats}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
