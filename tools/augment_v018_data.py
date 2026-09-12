#!/usr/bin/env python3
"""Augment Orbit mature assets (introduced in v0.18, retained by v0.19).

This stage runs after prepare_mature_ime_data.py and adds/refreshes:
- pinned CC-CEDICT in the runtime Pinyin lexicon;
- sharded Chinese<->English lexical translation assets;
- pinned Unicode Emoji 17.0 fully-qualified data;
- broader ESDB/SCOWL en_US-large English packing, including useful title-case/
  acronym vocabulary normalized to lowercase lookup keys;
- required attribution/permission notices.

The installed IME remains offline; all downloads happen on the build machine.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
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
CEDICT_RE = re.compile(r"^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.*)/$")
ASCII_GLOSS_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 '\-.,!?():/]+$")
EN_NORMALIZE_RE = re.compile(r"[^a-z0-9 '\-]+")
SCOWL_WORD_RE = re.compile(r"^[A-Za-z]+(?:'[A-Za-z]+)?(?:-[A-Za-z]+)*$")


def git_blob_sha1(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def download_git_blob_verified(spec: dict, target: Path) -> bytes:
    expected = str(spec["git_blob_sha1"]).lower()
    if target.is_file():
        data = target.read_bytes()
        if git_blob_sha1(data) == expected:
            return data
        target.unlink()
    request = urllib.request.Request(str(spec["url"]), headers={"User-Agent": "Orbit-IME-build-data/0.19"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    actual = git_blob_sha1(data)
    if actual != expected:
        raise RuntimeError(f"Git blob mismatch for {spec['name']}: expected {expected}, got {actual}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data


def download_sha256_verified(spec: dict, target: Path) -> bytes:
    expected = str(spec["sha256"]).lower()
    if target.is_file():
        data = target.read_bytes()
        if hashlib.sha256(data).hexdigest() == expected:
            return data
        target.unlink()
    request = urllib.request.Request(str(spec["url"]), headers={"User-Agent": "Orbit-IME-build-data/0.19"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise RuntimeError(f"SHA-256 mismatch for {spec['name']}: expected {expected}, got {actual}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data


def normalize_english_gloss(raw: str) -> str:
    value = raw.strip().replace("sb", "somebody").replace("sth", "something")
    value = re.sub(r"\([^)]*(?:abbr\.|variant|classifier|Taiwan|Mainland|lit\.)[^)]*\)", "", value, flags=re.I)
    value = value.replace(";", ",")
    value = re.sub(r"\s+", " ", value).strip(" ,.;")
    if not (1 <= len(value) <= 96) or not ASCII_GLOSS_RE.fullmatch(value):
        return ""
    lowered = value.lower()
    rejected = (
        "classifier for ", "variant of ", "see also ", "see ", "old variant of ",
        "used in ", "surname ", "abbr. for ", "abbr for ", "same as ",
    )
    if lowered.startswith(rejected):
        return ""
    return value


def normalize_english_key(raw: str) -> str:
    value = EN_NORMALIZE_RE.sub(" ", raw.lower())
    return re.sub(r"\s+", " ", value).strip()


def parse_cedict(raw: bytes):
    for line in raw.decode("utf-8-sig").splitlines():
        if not line or line.startswith("#"):
            continue
        match = CEDICT_RE.match(line.strip())
        if not match:
            continue
        traditional, simplified, pinyin, gloss_blob = match.groups()
        glosses = [normalize_english_gloss(value) for value in gloss_blob.split("/")]
        glosses = [value for value in glosses if value]
        if simplified and pinyin and glosses:
            yield traditional, simplified, pinyin, glosses


def write_broader_english_asset(raw: bytes, output_tsv: Path, policy: dict) -> int:
    """Repack the pinned SCOWL large list without discarding all proper nouns/acronyms.

    Lookup keys remain lowercase ASCII. Original spelling is retained as an optional
    display candidate when it differs from the lookup key. This is vocabulary data,
    not a frequency corpus; project-authored common words still receive stronger
    product-specific ranking through the seed layer.
    """
    min_len = int(policy["english_min_key_length"])
    max_len = int(policy["english_max_key_length"])
    base_frequency = int(policy["english_base_frequency"])
    records: dict[str, tuple[int, set[str]]] = {}
    for raw_line in raw.decode("utf-8-sig").splitlines():
        word = raw_line.strip()
        if not word or not SCOWL_WORD_RE.fullmatch(word):
            continue
        key = re.sub(r"[^a-z]", "", word.lower())
        if not (min_len <= len(key) <= max_len):
            continue
        # SCOWL level/order is not treated as true usage frequency. Keep a modest
        # length penalty and a small lowercase-common-word boost only.
        frequency = max(4_000, base_frequency - max(0, len(key) - 4) * 700)
        if word == word.lower():
            frequency += 2_000
        old_frequency, displays = records.get(key, (0, set()))
        updated = set(displays)
        if word != key:
            updated.add(word)
        records[key] = (max(old_frequency, frequency), updated)

    output_tsv.parent.mkdir(parents=True, exist_ok=True)
    with output_tsv.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Derived from pinned ESDB/SCOWL en_US-large.\n")
        handle.write("# lowercase-key<TAB>frequency<TAB>optional display candidates\n")
        for key, (frequency, displays) in sorted(records.items()):
            extras = "\t".join(sorted(displays)[:4])
            handle.write(f"{key}\t{frequency}" + (f"\t{extras}" if extras else "") + "\n")
    return len(records)


def add_sources_to_import_manifest(staging: Path, sources: dict, default_frequency: int) -> Path:
    raw_manifest = staging / "mature_import_manifest.json"
    if not raw_manifest.is_file():
        raise RuntimeError("mature_import_manifest.json missing; run prepare_mature_ime_data.py first")
    payload = json.loads(raw_manifest.read_text(encoding="utf-8"))
    items = [item for item in payload.get("sources", []) if item.get("name") not in {"cc-cedict-2026-09-10", "esdb-scowl-en-us"}]
    items.append({
        "name": "cc-cedict-2026-09-10",
        "path": "cc_cedict.txt",
        "format": "cedict",
        "license": "CC-BY-SA-4.0",
        "url": sources["cedict"]["url"],
        "redistribution_allowed": True,
        "attribution": sources["cedict"]["attribution"],
        "default_frequency": int(default_frequency),
    })
    items.append({
        "name": "esdb-scowl-en-us",
        "path": "esdb_en_us.tsv",
        "format": "english-tsv",
        "license": "ESDB-2026",
        "url": sources["esdb_en_us"]["url"],
        "redistribution_allowed": True,
        "attribution": sources["esdb_en_us"]["attribution"],
    })
    payload["sources"] = items
    augmented = staging / "mature_import_manifest_v019.json"
    augmented.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return augmented


def write_translation_assets(raw: bytes, output: Path) -> dict[str, int]:
    zh_records: dict[str, str] = {}
    en_records: dict[str, str] = {}
    cedict_entries = 0
    for _traditional, simplified, _pinyin, glosses in parse_cedict(raw):
        cedict_entries += 1
        if simplified not in zh_records:
            zh_records[simplified] = glosses[0]
        for gloss in glosses[:5]:
            candidates = [gloss]
            lowered = gloss.lower()
            if lowered.startswith("to ") and len(gloss) > 3:
                candidates.append(gloss[3:])
            candidates.extend(part.strip() for part in re.split(r",|\bor\b", gloss, flags=re.I) if part.strip())
            for candidate in candidates:
                key = normalize_english_key(candidate)
                if 1 <= len(key) <= 80 and key not in en_records:
                    en_records[key] = simplified

    translation_dir = output / "translation"
    for dirname in (translation_dir / "zh", translation_dir / "en"):
        dirname.mkdir(parents=True, exist_ok=True)
        for old in dirname.glob("*.odict"):
            old.unlink()

    zh_shards: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for source, target in zh_records.items():
        shard = format(ord(source[0]) & 63, "02x")
        zh_shards[shard].append((source, target))
    for shard, rows in zh_shards.items():
        rows.sort(key=lambda item: (-len(item[0]), item[0]))
        with (translation_dir / "zh" / f"{shard}.odict").open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("#ORBIT_TRANSLATION\t1\tZH_EN\tCC-BY-SA-4.0\n")
            for source, target in rows:
                handle.write(f"{source}\t{target}\n")

    en_shards: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for source, target in en_records.items():
        first = source[0] if source and "a" <= source[0] <= "z" else "_"
        en_shards[first].append((source, target))
    for shard, rows in en_shards.items():
        rows.sort(key=lambda item: (-len(item[0].split()), -len(item[0]), item[0]))
        with (translation_dir / "en" / f"{shard}.odict").open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("#ORBIT_TRANSLATION\t1\tEN_ZH\tCC-BY-SA-4.0\n")
            for source, target in rows:
                handle.write(f"{source}\t{target}\n")

    return {
        "cedict_entries": cedict_entries,
        "translation_zh_entries": len(zh_records),
        "translation_en_entries": len(en_records),
        "translation_zh_shards": len(zh_shards),
        "translation_en_shards": len(en_shards),
    }


def parse_unicode_emoji(raw: bytes) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for raw_line in raw.decode("utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "; fully-qualified" not in line or "#" not in line:
            continue
        emoji = line.split("#", 1)[1].strip().split(" ", 1)[0]
        if emoji and emoji not in seen:
            seen.add(emoji)
            result.append(emoji)
    return result


def write_unicode_emoji(raw: bytes, output: Path) -> int:
    emojis = parse_unicode_emoji(raw)
    target = output / "emoji_unicode.txt"
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# Unicode Emoji 17.0 fully-qualified sequences. Unicode License v3.\n")
        for emoji in emojis:
            handle.write(emoji + "\n")
    return len(emojis)


def write_notices(cdict_raw: bytes, output: Path) -> None:
    notice_dir = output / "third_party_notices"
    notice_dir.mkdir(parents=True, exist_ok=True)
    header_lines: list[str] = []
    for line in cdict_raw.decode("utf-8-sig").splitlines():
        if not line.startswith("#"):
            break
        header_lines.append(line)
    (notice_dir / "CC-CEDICT-NOTICE.txt").write_text("\n".join(header_lines) + "\n", encoding="utf-8")
    unicode_notice = """UNICODE LICENSE V3 - COPYRIGHT AND PERMISSION NOTICE
Copyright © 1991-2026 Unicode, Inc.
Permission is granted, free of charge, to deal in Unicode Data Files and associated documentation without restriction, including use, copy, modification, merge, publication, distribution and sale, provided that the Unicode copyright and permission notice appears with copies or associated documentation.
Official license text: https://www.unicode.org/license.txt
Emoji data source: https://www.unicode.org/Public/emoji/17.0/emoji-test.txt
"""
    (notice_dir / "Unicode-Emoji-NOTICE.txt").write_text(unicode_notice, encoding="utf-8")


def update_report(output: Path, config: dict, extra_stats: dict[str, int]) -> None:
    report_path = output / "mature-report.json"
    manifest_path = output / "manifest.json"
    if not report_path.is_file() or not manifest_path.is_file():
        raise RuntimeError("mature report/runtime manifest missing after augmented importer")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    runtime_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    report["version"] = 3
    report.setdefault("stats", {}).update(extra_stats)
    report["runtime_counts"] = runtime_manifest.get("counts", {})
    pins = report.setdefault("pins", {})
    for key in ("cedict", "unicode_emoji"):
        spec = config["sources"][key]
        pin = {"url": spec["url"], "license": spec["license"]}
        if "git_blob_sha1" in spec:
            pin["git_blob_sha1"] = spec["git_blob_sha1"]
        if "sha256" in spec:
            pin["sha256"] = spec["sha256"]
        pins[key] = pin
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Augment Orbit IME v0.19 mature offline data")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE))
    parser.add_argument("--staging-dir", default=str(DEFAULT_STAGING))
    args = parser.parse_args()

    config = json.loads(Path(args.config).resolve().read_text(encoding="utf-8"))
    sources, policy = config["sources"], config["policy"]
    output = Path(args.output).resolve()
    cache = Path(args.cache_dir).resolve()
    staging = Path(args.staging_dir).resolve()
    staging.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)

    cedict_raw = download_git_blob_verified(sources["cedict"], cache / "cc_cedict.txt")
    emoji_raw = download_sha256_verified(sources["unicode_emoji"], cache / "unicode_emoji_17.txt")
    esdb_raw = download_git_blob_verified(sources["esdb_en_us"], cache / "esdb_en_US.txt")
    (staging / "cc_cedict.txt").write_bytes(cedict_raw)

    english_count = write_broader_english_asset(esdb_raw, staging / "esdb_en_us.tsv", policy)
    augmented_manifest = add_sources_to_import_manifest(staging, sources, int(policy["cedict_default_frequency"]))
    importer = ROOT / "tools/ime_importer.py"
    result = subprocess.run(
        [sys.executable, str(importer), "--manifest", str(augmented_manifest), "--output", str(output)],
        cwd=str(ROOT), check=False,
    )
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    stats = write_translation_assets(cedict_raw, output)
    stats["esdb_english_entries"] = english_count
    stats["unicode_emoji_entries"] = write_unicode_emoji(emoji_raw, output)
    write_notices(cedict_raw, output)
    update_report(output, config, stats)
    print(json.dumps({"status": "PASS", **stats}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
