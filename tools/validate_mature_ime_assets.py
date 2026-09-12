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
    min_cedict = int(policy["minimum_cedict_entries"])
    min_runtime_cn = int(policy["minimum_runtime_lexicon_entries"])
    min_en = int(policy["minimum_english_entries"])
    min_emoji = int(policy["minimum_unicode_emoji_entries"])
    min_translate_zh = int(policy["minimum_translation_zh_entries"])
    min_translate_en = int(policy["minimum_translation_en_entries"])

    require(int(stats.get("aosp_lexicon_entries", 0)) >= min_aosp, f"AOSP lexicon too small: {stats.get('aosp_lexicon_entries')}")
    require(int(stats.get("jieba_generated_entries", 0)) >= min_jieba, f"Jieba derived lexicon too small: {stats.get('jieba_generated_entries')}")
    require(int(stats.get("cedict_entries", 0)) >= min_cedict, f"CC-CEDICT pack too small: {stats.get('cedict_entries')}")
    require(int(stats.get("esdb_english_entries", 0)) >= min_en, f"ESDB English pack too small: {stats.get('esdb_english_entries')}")
    require(int(stats.get("unicode_emoji_entries", 0)) >= min_emoji, f"Unicode emoji pack too small: {stats.get('unicode_emoji_entries')}")
    require(int(stats.get("translation_zh_entries", 0)) >= min_translate_zh, f"ZH->EN translation pack too small: {stats.get('translation_zh_entries')}")
    require(int(stats.get("translation_en_entries", 0)) >= min_translate_en, f"EN->ZH translation pack too small: {stats.get('translation_en_entries')}")
    require(int(counts.get("lexicon", 0)) >= min_runtime_cn, f"runtime Chinese lexicon too small: {counts.get('lexicon')}")
    require(int(counts.get("english", 0)) >= min_en, f"runtime English pack too small: {counts.get('english')}")

    ngrams = counts.get("ngrams", {})
    require(int(ngrams.get("1", 0)) >= 5000, f"1-gram pack too small: {ngrams.get('1')}")
    require(int(ngrams.get("2", 0)) >= 30000, f"2-gram pack too small: {ngrams.get('2')}")
    require(int(ngrams.get("3", 0)) >= 30000, f"3-gram pack too small: {ngrams.get('3')}")

    cn_shards = list((assets / "lexicon").glob("*.odict"))
    en_shards = list((assets / "english").glob("*.odict"))
    zh_translation_shards = list((assets / "translation/zh").glob("*.odict"))
    en_translation_shards = list((assets / "translation/en").glob("*.odict"))
    require(len(cn_shards) >= 20, f"too few Chinese shards: {len(cn_shards)}")
    require(len(en_shards) >= 20, f"too few English shards: {len(en_shards)}")
    require(len(zh_translation_shards) >= 40, f"too few Chinese translation shards: {len(zh_translation_shards)}")
    require(len(en_translation_shards) >= 20, f"too few English translation shards: {len(en_translation_shards)}")

    emoji_file = assets / "emoji_unicode.txt"
    require(emoji_file.is_file() and emoji_file.stat().st_size > 10000, "Unicode emoji asset missing/incomplete")

    notice_dir = assets / "third_party_notices"
    notices = {
        "AOSP": (notice_dir / "AOSP-PinyinIME-NOTICE.txt", 1000),
        "ESDB": (notice_dir / "ESDB-SCOWL-Copyright.txt", 1000),
        "Jieba": (notice_dir / "Jieba-LICENSE.txt", 500),
        "CC-CEDICT": (notice_dir / "CC-CEDICT-NOTICE.txt", 300),
        "Unicode Emoji": (notice_dir / "Unicode-Emoji-NOTICE.txt", 200),
    }
    for name, (path, minimum_bytes) in notices.items():
        require(path.is_file() and path.stat().st_size > minimum_bytes, f"{name} notice missing/incomplete")

    source_licenses = {str(item.get("license")) for item in manifest.get("sources", [])}
    require("Apache-2.0" in source_licenses, "AOSP Apache-2.0 source missing from runtime manifest")
    require("MIT" in source_licenses, "Jieba MIT source missing from runtime manifest")
    require("ESDB-2026" in source_licenses, "ESDB source missing from runtime manifest")
    require("CC-BY-SA-4.0" in source_licenses, "CC-CEDICT source missing from runtime manifest")

    pins = report.get("pins", {})
    for key in ("aosp_pinyin", "aosp_notice", "jieba_dict", "jieba_license", "esdb_en_us", "esdb_copyright", "cedict"):
        require(key in pins and pins[key].get("git_blob_sha1"), f"source pin missing: {key}")
    require("unicode_emoji" in pins and pins["unicode_emoji"].get("sha256"), "Unicode emoji SHA-256 pin missing")

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
    require('android:name=".OrbitStickerProvider"' in android_manifest, "local sticker provider missing")
    require('android:exported="false"' in android_manifest, "sticker provider must stay non-exported")
    require('android:grantUriPermissions="true"' in android_manifest, "sticker provider URI grants missing")

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    require('versionCode = 18' in gradle, "versionCode is not 18")
    require('versionName = "0.18.0"' in gradle, "versionName is not 0.18.0")

    summary = {
        "status": "PASS",
        "aosp_lexicon_entries": stats["aosp_lexicon_entries"],
        "jieba_generated_entries": stats["jieba_generated_entries"],
        "cedict_entries": stats["cedict_entries"],
        "runtime_lexicon": counts["lexicon"],
        "english_entries": stats["esdb_english_entries"],
        "runtime_english": counts["english"],
        "unicode_emoji_entries": stats["unicode_emoji_entries"],
        "translation_zh_entries": stats["translation_zh_entries"],
        "translation_en_entries": stats["translation_en_entries"],
        "ngrams": ngrams,
        "chinese_shards": len(cn_shards),
        "english_shards": len(en_shards),
        "translation_zh_shards": len(zh_translation_shards),
        "translation_en_shards": len(en_translation_shards),
    }
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
