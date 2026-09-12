# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.23.0`.

## Network

The installed Orbit IME does not request `INTERNET`. Input text, clipboard text, composing buffers, translation text, ranking state, personal dictionary entries, pet data, custom phrases, appearance settings, generated stickers and imported model packs are not uploaded.

Dictionary/frequency/Emoji sources are downloaded only by the build machine, hash-verified and converted to packaged offline assets. v0.23 adds pinned THUOCL domain data and HermitDave/FrequencyWords usage-frequency data to the existing AOSP/Jieba/CC-CEDICT/ESDB/Unicode pipeline. Their notices are packaged with the generated assets.

The model-pack manager still does not download models. Source buttons open an external browser; Orbit receives a pack only after the user explicitly selects a local file through Android Storage Access Framework.

## Advertising and analytics

No ad SDK, analytics, tracking, remote configuration or cloud prediction is included.

## Selection, composing and input ranking

Orbit keeps selection-aware replace/delete behavior and composing replacement. Local candidate ranking uses static frequency, packaged N-grams, bounded cursor context and local user selection frequency.

v0.23 adds a packaged 32-shard association index. After a candidate is committed, Orbit reads only a bounded text tail before the cursor, looks up the last 1–4 Chinese characters and combines those local suggestions with bounded N-gram continuation. The context is not persisted by the association engine.

Fuzzy correction can be Off, Standard or Enhanced. The selected level is stored locally. Fuzzy candidate generation does not upload misspellings or create a persistent record of raw typing.

## Persistent settings and appearance

Local preferences include input mode, quick-phrase visibility, built-in phrase visibility, association toggle, fuzzy level, translation context toggle, appearance mode, selected skin, pet/outfit state and Pro state.

Appearance supports Follow system, Light, Dark, AMOLED black and Custom skin. Following the Android night flag requires no network or account information.

## Personal learning

Free supports 20,000 local entries; Pro supports 100,000. The app-private store uses `dictionary.tsv + journal.tsv` and compaction. Persistent learning records remain limited to Pinyin, committed candidate text, frequency and updatedAt. Full conversations, target-app identity and complete typed streams are not stored.

## Clipboard

Recent/Pinned clipboard listening exists only while the IME window is visible. There is no background clipboard service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

## Translation

Free single-sentence translation stays fully local. v0.23 adds a bounded dynamic-programming local fallback that prefers longer exact bilingual fragments and reports/uses coverage rather than pretending every fragment was translated.

Pro may enable previous-context translation and selected long-form translation up to 8,000 characters. Long-form processing preserves paragraph/line structure; uncovered segments remain source text. Translation context exists only for the current operation and is not added to the personal dictionary.

Imported neural translation packs remain non-executable until a separately validated Android neural runtime is bundled. v0.23 does not claim neural output when that runtime is absent.

## Local model packs

Pro `.orbitpack` management from v0.22 remains. Packs require `manifest.json`, `LICENSE.txt`, `NOTICE.txt`, `checksums.sha256` and model files. Orbit checks paths, duplicate entries, size limits, offline-only privacy declaration and SHA-256 before app-private installation. Packs declaring `android.permission.INTERNET` are rejected.

The user explicitly accepts source/license/performance/privacy disclaimers before installation. Integrity validation is not legal advice; third-party model terms remain the user's responsibility.

## Emoji, stickers and pets

Unicode Emoji, project kaomoji and 128 local pet sticker definitions remain local. Sticker PNGs are generated in app-private cache. `OrbitStickerProvider` remains non-exported with temporary URI grants. Pet state and small feedback event codes stay local; no overlay or cloud pet service is used.

## Sensitive fields

Password-like/no-personalized-learning fields hide extra tools, detach clipboard listening, clear composing/translation state, disable pet growth and block personal learning.

## Permissions intentionally not requested

Orbit v0.23 does not request Internet, microphone, Accessibility, overlay/floating-window, contacts, SMS, location, camera, external storage or notifications.

Future ASR can request `RECORD_AUDIO` only in a later version with a separate explicit user-controlled voice-input flow. Importing an ASR model pack alone must not request microphone access.
