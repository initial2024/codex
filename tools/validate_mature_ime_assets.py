#!/usr/bin/env python3
"""Fail the build when Orbit v0.27 mature assets, privacy gates or local runtimes regress."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ASSETS = ROOT / "app/src/main/assets/ime"
DEFAULT_CONFIG = ROOT / "data/ime_sources/mature_sources.json"
SRC = ROOT / "app/src/main/java/com/ccwu/orbitime"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def read(path: Path) -> str:
    require(path.is_file(), f"missing required file: {path}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate Orbit v0.27 mature IME assets/runtime gates")
    parser.add_argument("--assets", default=str(DEFAULT_ASSETS))
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = parser.parse_args()

    assets = Path(args.assets).resolve()
    config = json.loads(read(Path(args.config).resolve()))
    require(int(config.get("version", 0)) >= 5, "mature source config older than v0.23")
    policy = config["policy"]
    report = json.loads(read(assets / "mature-report.json"))
    manifest = json.loads(read(assets / "manifest.json"))
    stats = report.get("stats", {})
    counts = report.get("runtime_counts", {})
    require(int(report.get("version", 0)) >= 5, f"mature report is stale: {report.get('version')}")

    data_gates = {
        "aosp_lexicon_entries": "minimum_aosp_entries",
        "jieba_generated_entries": "minimum_jieba_generated_entries",
        "cedict_entries": "minimum_cedict_entries",
        "cedict_four_char_entries": "minimum_cedict_idiom_entries",
        "project_software_entries": "minimum_project_software_entries",
        "thuocl_source_entries": "minimum_thuocl_source_entries",
        "thuocl_generated_entries": "minimum_thuocl_generated_entries",
        "frequencywords_en_entries": "minimum_frequencywords_en_entries",
        "frequencywords_zh_source_entries": "minimum_frequencywords_zh_entries",
        "association_entries": "minimum_association_entries",
        "unicode_emoji_entries": "minimum_unicode_emoji_entries",
        "translation_zh_entries": "minimum_translation_zh_entries",
        "translation_en_entries": "minimum_translation_en_entries",
    }
    for stat, policy_key in data_gates.items():
        actual = int(stats.get(stat, 0))
        minimum = int(policy[policy_key])
        require(actual >= minimum, f"{stat} below gate: {actual} < {minimum}")

    require(int(counts.get("lexicon", 0)) >= int(policy["minimum_runtime_lexicon_entries"]), "runtime Chinese lexicon too small")
    require(int(counts.get("english", 0)) >= int(policy["minimum_english_entries"]), "runtime English pack too small")
    ngrams = counts.get("ngrams", {})
    require(int(ngrams.get("1", 0)) >= 5000, "1-gram pack too small")
    require(int(ngrams.get("2", 0)) >= 30000, "2-gram pack too small")
    require(int(ngrams.get("3", 0)) >= 30000, "3-gram pack too small")

    cn_shards = list((assets / "lexicon").glob("*.odict"))
    en_shards = list((assets / "english").glob("*.odict"))
    association_shards = list((assets / "association").glob("*.odict"))
    zh_translation_shards = list((assets / "translation/zh").glob("*.odict"))
    en_translation_shards = list((assets / "translation/en").glob("*.odict"))
    require(len(cn_shards) >= 20, "too few Chinese shards")
    require(len(en_shards) >= 20, "too few English shards")
    require(len(association_shards) == 32, f"association pack must have 32 shards, got {len(association_shards)}")
    require(len(zh_translation_shards) >= 40, "too few ZH translation shards")
    require(len(en_translation_shards) >= 20, "too few EN translation shards")
    require((assets / "emoji_unicode.txt").stat().st_size > 10000, "Unicode emoji asset missing/incomplete")

    notice_dir = assets / "third_party_notices"
    for label, name, minimum in (
        ("AOSP", "AOSP-PinyinIME-NOTICE.txt", 1000),
        ("ESDB", "ESDB-SCOWL-Copyright.txt", 1000),
        ("Jieba", "Jieba-LICENSE.txt", 500),
        ("CC-CEDICT", "CC-CEDICT-NOTICE.txt", 300),
        ("Unicode", "Unicode-Emoji-NOTICE.txt", 200),
        ("THUOCL", "THUOCL-LICENSE.txt", 500),
        ("THUOCL README", "THUOCL-README.txt", 1000),
        ("FrequencyWords", "FrequencyWords-README.txt", 500),
    ):
        path = notice_dir / name
        require(path.is_file() and path.stat().st_size >= minimum, f"{label} notice missing/incomplete")

    source_names = {str(item.get("name")) for item in manifest.get("sources", [])}
    source_licenses = {str(item.get("license")) for item in manifest.get("sources", [])}
    for required in (
        "orbit-project-software-vocabulary",
        "cc-cedict-four-char-boost",
        "thuocl-frequency-overlay",
        "frequencywords-english-overlay",
        "frequencywords-chinese-ngram",
    ):
        require(required in source_names, f"runtime source missing: {required}")
    for license_name in ("Apache-2.0", "MIT", "ESDB-2026", "CC-BY-SA-4.0"):
        require(license_name in source_licenses, f"runtime license metadata missing: {license_name}")

    pins = report.get("pins", {})
    for key in ("aosp_pinyin", "jieba_dict", "esdb_en_us", "cedict", "frequencywords_en", "frequencywords_zh"):
        require(key in pins and pins[key].get("git_blob_sha1"), f"source pin missing: {key}")
    for key in ("thuocl_it", "thuocl_idiom", "thuocl_place", "thuocl_medical", "thuocl_poem"):
        require(key in pins and pins[key].get("git_blob_sha1"), f"THUOCL source pin missing: {key}")
    require("unicode_emoji" in pins and pins["unicode_emoji"].get("sha256"), "Unicode emoji SHA-256 pin missing")

    android_manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
    require('android.permission.RECORD_AUDIO' in android_manifest, "v0.24 RECORD_AUDIO declaration missing")
    for forbidden in (
        "android.permission.INTERNET",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.accessibilityservice.AccessibilityService",
    ):
        require(forbidden not in android_manifest, f"forbidden manifest capability found: {forbidden}")
    require('android:name=".OrbitStickerProvider"' in android_manifest, "local sticker provider missing")
    require('android:exported="false"' in android_manifest, "sticker provider must remain non-exported")
    require('android:grantUriPermissions="true"' in android_manifest, "sticker provider URI grants missing")

    settings = read(ROOT / "settings.gradle.kts")
    require('https://jitpack.io' in settings, "sherpa JitPack build repository missing")
    gradle = read(ROOT / "app/build.gradle.kts")
    require('versionCode = 27' in gradle and 'versionName = "0.27.0"' in gradle, "Gradle is not v0.27.0")
    require('com.github.k2-fsa:sherpa-onnx:1.13.8' in gradle, "pinned sherpa-onnx 1.13.8 dependency missing")
    for token in ("test_ime_data_pipeline_v023.py", "augment_v023_data.py", "test_model_pack_pipeline.py"):
        require(token in gradle, f"preBuild stage missing: {token}")

    ime_prefs = read(SRC / "ImePreferences.kt")
    for token in ("FUZZY_OFF", "FUZZY_STANDARD", "FUZZY_ENHANCED"):
        require(token in ime_prefs, f"three-level fuzzy preference missing: {token}")
    skin_manager = read(SRC / "SkinManager.kt")
    for token in ("APPEARANCE_SYSTEM", "APPEARANCE_LIGHT", "APPEARANCE_DARK", "APPEARANCE_AMOLED", "UI_MODE_NIGHT_YES"):
        require(token in skin_manager, f"night appearance support missing: {token}")

    pinyin_engine = read(SRC / "PinyinImeEngine.kt")
    for token in ("MAX_RESULTS = 48", "PREFIX_POOL_LIMIT = 120", "BEAM_WIDTH = 88", "MAX_QUERY_CACHE = 128"):
        require(token in pinyin_engine, f"expanded Pinyin runtime missing: {token}")
    english_engine = read(SRC / "EnglishImeEngine.kt")
    require("EnglishFuzzyEngine" in english_engine and "DEFAULT_LIMIT = 48" in english_engine, "expanded English runtime missing")
    user_store = read(SRC / "UserDictionaryStore.kt")
    require('STORE_DIR = "orbit-user-dictionary"' in user_store and "journal.tsv" in user_store, "file+journal learning regressed")
    require("MAX_CANDIDATES = 48" in user_store and "DEFAULT_ASSOCIATION_LIMIT = 48" in user_store, "expanded candidate/association pool missing")
    require("AssociationAsset" in read(SRC / "NextAssociationEngine.kt"), "fast association asset path not wired")

    offline_translation = read(SRC / "OfflineTranslationPack.kt")
    require("FluentLocalTranslationEngine" in offline_translation and "TranslationOutputNormalizer" in offline_translation, "local translation pipeline regressed")
    require("MAX_CONTEXT_SENTENCES = 4" in read(SRC / "ContextTranslationEngine.kt"), "4-sentence context translation missing")
    require("MAX_TRANSLATION_SEGMENTS = 480" in read(SRC / "LongFormTranslationEngine.kt"), "long-form translation regressed")

    for source in (
        "ModelPackManifest.kt",
        "ModelPackManager.kt",
        "ModelRuntimeContracts.kt",
        "CuratedModelCatalog.kt",
        "SherpaSpeechProviders.kt",
        "LocalSpeechInputController.kt",
        "ImeSpeechController.kt",
        "OrbitAudioPlayer.kt",
        "VoiceReferenceStore.kt",
    ):
        require((SRC / source).is_file(), f"{source} missing")

    pack_manifest = read(SRC / "ModelPackManifest.kt")
    require("modelFamily" in pack_manifest and "runtimeConfig" in pack_manifest, "v0.24+ model runtime manifest fields missing")
    require('android.permission.INTERNET' in pack_manifest, "pack-level INTERNET rejection missing")
    pack_manager = read(SRC / "ModelPackManager.kt")
    require("checksums.sha256" in pack_manager and 'MessageDigest.getInstance("SHA-256")' in pack_manager, "model-pack SHA-256 validation missing")
    require("MAX_ENTRIES = 8192" in pack_manager, "speech-pack file-count safety cap missing/wrong")

    runtime_contracts = read(SRC / "ModelRuntimeContracts.kt")
    for token in ("sherpa_offline_transducer", "sherpa_vits", "sherpa_kokoro", "sherpa_supertonic", "sherpa_zipvoice"):
        require(token in runtime_contracts, f"supported local runtime family missing: {token}")
    require("generic Marian/OPUS-MT" not in runtime_contracts, "invalid validator sentinel")
    # Translation remains explicitly non-executable until an audited Android decoder exists.
    require("尚未内置通用 Marian/OPUS-MT Android 解码器" in runtime_contracts, "neural translation must not be faked as executable")

    sherpa = read(SRC / "SherpaSpeechProviders.kt")
    for token in ("OfflineRecognizer", "OfflineTts", "SherpaAsrProvider", "SherpaTtsProvider", "SherpaZipVoiceProvider"):
        require(token in sherpa, f"sherpa provider missing: {token}")
    capture = read(SRC / "LocalSpeechInputController.kt")
    for token in ("VOICE_RECOGNITION", "MAX_SECONDS = 60", "RECORD_AUDIO", "ShortArray"):
        require(token in capture, f"local ASR capture boundary missing: {token}")
    voice_store = read(SRC / "VoiceReferenceStore.kt")
    for token in ("consentConfirmed", "consent_confirmed", "PCM WAV", "MAX_SECONDS = 30f"):
        require(token in voice_store, f"voice-reference consent/safety gate missing: {token}")

    service = read(SRC / "OrbitInputMethodService.kt")
    for token in ("ImeSpeechController", "handleLocalVoiceInput", "speakCurrentText", "cancelCapture", "LongFormTranslationEngine"):
        require(token in service, f"IME speech/core wiring missing: {token}")
    require("onWindowHidden" in service and "sensitiveMode" in service, "recording cancellation/privacy mode missing")
    main_activity = read(SRC / "MainActivity.kt")
    # Preserve the v0.24-v0.26 settings/runtime capabilities by checking their
    # concrete actions. Do not couple feature-regression checks to an obsolete
    # release-label string in the UI.
    for token in ("requestMicrophonePermission", "previewTts", "confirmAndChooseVoiceReference", "previewVoiceClone"):
        require(token in main_activity, f"historical settings/runtime action missing: {token}")

    for example in (
        "docs/orbitpack-asr-sherpa.example.json",
        "docs/orbitpack-tts-sherpa.example.json",
        "docs/orbitpack-zipvoice.example.json",
        "MODEL_PACKS.md",
    ):
        require((ROOT / example).is_file(), f"model-pack documentation missing: {example}")

    pet_source = read(SRC / "PetRepository.kt")
    pet_count = len(re.findall(r'PetDefinition\("', pet_source))
    outfit_count = len(re.findall(r'OutfitDefinition\("', pet_source))
    require(pet_count >= 16 and outfit_count >= 24, f"pet/outfit catalog regressed: {pet_count}/{outfit_count}")
    mood_count = len(re.findall(r'MoodMeta\(StickerVariant\.', read(SRC / "StickerPack.kt")))
    require(pet_count * mood_count >= 128, "local sticker definitions below 128")

    workflow = read(ROOT / ".github/workflows/build-apk.yml")
    require("workflow_dispatch:" in workflow, "GitHub Actions must remain manually triggered")
    require("orbit-ime-v0.27-debug-apk" in workflow, "v0.27 Actions artifact name missing")

    summary = {
        "status": "PASS",
        "version": "0.27.0",
        "runtime_chinese": counts.get("lexicon", 0),
        "runtime_english": counts.get("english", 0),
        "thuocl_source": stats.get("thuocl_source_entries", 0),
        "thuocl_generated": stats.get("thuocl_generated_entries", 0),
        "association_entries": stats.get("association_entries", 0),
        "association_shards": len(association_shards),
        "ngrams": ngrams,
        "translation_zh": stats.get("translation_zh_entries", 0),
        "translation_en": stats.get("translation_en_entries", 0),
        "candidate_pool": 48,
        "context_sentences": 4,
        "speech_runtime": "sherpa-onnx 1.13.8",
        "record_audio": True,
        "internet_permission": False,
        "asr": "sherpa_offline_transducer",
        "tts": ["sherpa_vits", "sherpa_kokoro", "sherpa_supertonic"],
        "voice_clone": "sherpa_zipvoice",
        "neural_translation": "installable/auditable but not executable",
        "pet_catalog": pet_count,
        "outfit_catalog": outfit_count,
        "local_stickers": pet_count * mood_count,
    }
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
