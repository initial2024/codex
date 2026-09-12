#!/usr/bin/env python3
"""Fail the build when generated mature IME assets are incomplete or suspiciously small."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ASSETS = ROOT / "app/src/main/assets/ime"
DEFAULT_CONFIG = ROOT / "data/ime_sources/mature_sources.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate generated Orbit mature IME assets")
    parser.add_argument("--assets", default=str(DEFAULT_ASSETS))
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = parser.parse_args()

    assets = Path(args.assets).resolve()
    config = json.loads(Path(args.config).resolve().read_text(encoding="utf-8"))
    policy = config["policy"]
    report_path = assets / "mature-report.json"
    manifest_path = assets / "manifest.json"
    require(report_path.is_file(), "mature-report.json is missing")
    require(manifest_path.is_file(), "runtime manifest.json is missing")

    report = json.loads(report_path.read_text(encoding="utf-8"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    stats = report.get("stats", {})
    counts = report.get("runtime_counts", {})

    min_aosp = int(policy["minimum_aosp_entries"])
    min_jieba = int(policy["minimum_jieba_generated_entries"])
    min_runtime_cn = int(policy["minimum_runtime_lexicon_entries"])
    min_en = int(policy["minimum_english_entries"])
    require(int(stats.get("aosp_lexicon_entries", 0)) >= min_aosp, f"AOSP lexicon too small: {stats.get('aosp_lexicon_entries')}")
    require(int(stats.get("jieba_generated_entries", 0)) >= min_jieba, f"Jieba derived lexicon too small: {stats.get('jieba_generated_entries')}")
    require(int(stats.get("esdb_english_entries", 0)) >= min_en, f"ESDB English pack too small: {stats.get('esdb_english_entries')}")
    require(int(counts.get("lexicon", 0)) >= min_runtime_cn, f"runtime Chinese lexicon too small: {counts.get('lexicon')}")
    require(int(counts.get("english", 0)) >= min_en, f"runtime English pack too small: {counts.get('english')}")

    ngrams = counts.get("ngrams", {})
    require(int(ngrams.get("1", 0)) >= 2000, f"1-gram pack too small: {ngrams.get('1')}")
    require(int(ngrams.get("2", 0)) >= 10000, f"2-gram pack too small: {ngrams.get('2')}")
    require(int(ngrams.get("3", 0)) >= 10000, f"3-gram pack too small: {ngrams.get('3')}")

    cn_shards = list((assets / "lexicon").glob("*.odict"))
    en_shards = list((assets / "english").glob("*.odict"))
    require(len(cn_shards) >= 20, f"too few Chinese shards: {len(cn_shards)}")
    require(len(en_shards) >= 20, f"too few English shards: {len(en_shards)}")

    notice_dir = assets / "third_party_notices"
    aosp_notice = notice_dir / "AOSP-PinyinIME-NOTICE.txt"
    esdb_notice = notice_dir / "ESDB-SCOWL-Copyright.txt"
    jieba_notice = notice_dir / "Jieba-LICENSE.txt"
    require(aosp_notice.is_file() and aosp_notice.stat().st_size > 1000, "AOSP notice missing/incomplete")
    require(esdb_notice.is_file() and esdb_notice.stat().st_size > 1000, "ESDB notice missing/incomplete")
    require(jieba_notice.is_file() and jieba_notice.stat().st_size > 500, "Jieba MIT license missing/incomplete")

    source_licenses = {str(item.get("license")) for item in manifest.get("sources", [])}
    require("Apache-2.0" in source_licenses, "AOSP Apache-2.0 source missing from runtime manifest")
    require("MIT" in source_licenses, "Jieba MIT source missing from runtime manifest")
    require("ESDB-2026" in source_licenses, "ESDB source missing from runtime manifest")

    pins = report.get("pins", {})
    for key in ("aosp_pinyin", "aosp_notice", "jieba_dict", "jieba_license", "esdb_en_us", "esdb_copyright"):
        require(key in pins and pins[key].get("git_blob_sha1"), f"source pin missing: {key}")

    android_manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    forbidden = (
        "android.permission.INTERNET",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.POST_NOTIFICATIONS",
        "android.accessibilityservice.AccessibilityService",
    )
    for token in forbidden:
        require(token not in android_manifest, f"forbidden manifest capability found: {token}")

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    require('versionCode = 16' in gradle, "versionCode is not 16")
    require('versionName = "0.16.0"' in gradle, "versionName is not 0.16.0")

    summary = {
        "status": "PASS",
        "aosp_lexicon_entries": stats["aosp_lexicon_entries"],
        "jieba_generated_entries": stats["jieba_generated_entries"],
        "jieba_skipped_ambiguous_or_missing_reading": stats.get("jieba_skipped_ambiguous_or_missing_reading", 0),
        "english_entries": stats["esdb_english_entries"],
        "runtime_lexicon": counts["lexicon"],
        "runtime_english": counts["english"],
        "ngrams": ngrams,
        "chinese_shards": len(cn_shards),
        "english_shards": len(en_shards),
    }
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
