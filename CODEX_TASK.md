# Codex handoff: build Orbit IME v0.23.0 APK only

All non-build implementation is prepared. Build **current main** only and make only the smallest compile/portability repair exposed by the build.

## Confirm version

```text
versionCode = 23
versionName = 0.23.0
```

If local checkout is older, pull `main` first.

## Build

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true`.

## Mandatory preBuild

```text
test_ime_data_pipeline_v020.py
-> test_ime_data_pipeline_v023.py
-> test_model_pack_pipeline.py
-> prepare_mature_ime_data.py
-> AOSP + Jieba + ESDB/SCOWL en_US-large
-> augment_v018_data.py
-> CC-CEDICT + Unicode Emoji 17.0
-> augment_v020_data.py
-> broad English normalization + CC-CEDICT 4-char boost + software vocabulary
-> augment_v023_data.py
-> THUOCL domain vocabulary + FrequencyWords zh/en ranking + 32-shard association asset
-> validate_mature_ime_assets.py
-> Android compilation
```

Do not lower data gates or bypass source/hash/license checks.

## v0.23 input-quality gates

Preserve:

- THUOCL pinned domain sources and MIT notices;
- FrequencyWords zh/en 50k ranking data with CC-BY-SA-4.0 attribution;
- runtime Chinese gate >= 250,000;
- English gate >= 100,000;
- precomputed `ime/association/00.odict..1f.odict` with >= 10,000 association rows;
- `AssociationAsset` LRU shard reader + `NextAssociationEngine` association-first/N-gram-fallback path;
- 32 displayed Chinese/English candidates;
- continuous Pinyin, DP segmentation, adaptive Beam and 1/2/3-gram;
- persistent fuzzy modes `off / standard / enhanced`;
- enhanced Pinyin missing-key/repeated-key/neighbor/transposition/fuzzy recovery;
- `EnglishFuzzyEngine` bounded typo recovery;
- exact candidates always rank ahead of fuzzy candidates;
- long-query Beam remains bounded; do not increase it indiscriminately just to show more candidates.

## v0.23 translation gates

Keep:

```text
FluentLocalTranslationEngine.kt
OfflineTranslationPack.exactOnly(...)
LongFormTranslationEngine.kt
ContextTranslationEngine.kt
```

Required behavior:

- exact local translation first;
- coverage-scored dynamic-programming phrase translation before older greedy fallback;
- uncovered source text is preserved rather than fabricated;
- Pro long-form translation preserves paragraph/line breaks and supports up to 8,000 source chars;
- context preview uses the paragraph-aware local translation path;
- no cloud API/INTERNET permission.

The v0.22 `.orbitpack` manager remains. Imported neural packs are still **non-executable** unless a separately validated Android runtime is actually bundled. Do not fake neural translation output and do not add an unverified native dependency just to satisfy the build.

## Night/appearance gates

Keep `SkinManager` appearance modes:

```text
system
light
dark
amoled
custom
```

Default is `system`; Android `UI_MODE_NIGHT_YES` selects Orbit Dark. Appearance and fuzzy settings persist locally. Choosing a custom skin switches appearance to `custom`.

## Preserve model-pack security

Keep v0.22 `.orbitpack` validation: ACTION_OPEN_DOCUMENT, app-private storage, mandatory manifest/LICENSE/NOTICE/checksums, SHA-256 verification, Zip Slip/duplicate path/size limits, explicit disclaimer, enable/disable/uninstall, and INTERNET-declaring-pack rejection.

## Permissions

Do not add:

```text
INTERNET
RECORD_AUDIO
Accessibility
SYSTEM_ALERT_WINDOW
overlay/floating window
external storage
contacts/SMS/location/camera/notifications
ads/analytics/tracking/cloud sync/cloud translation
background clipboard/input harvesting
```

## Existing features must not regress

Selection-aware replace/delete, composing replacement, custom/disableable quick phrases, file+journal learning (20k Free/100k Pro), Recent/Pinned clipboard, Unicode Emoji, kaomoji, 7-page symbols, long press, 16 pets, 24 outfits, 128 stickers and polished pet rendering must remain.

## If build fails

Only repair the minimum Kotlin/import/API/resource/Gradle/Python/SAF/file-I/O/IME metadata blocker. Do not redesign features, lower data gates or add network/native runtimes.

## Expected output

```text
APK = app/build/outputs/apk/debug/app-debug.apk
artifact = orbit-ime-v0.23-debug-apk
Actions = workflow_dispatch only
```

## Build report

Return:

1. git status and current commit;
2. exact build command;
3. v0.20/v0.23/model-pack Python test PASS/FAIL;
4. every data stage PASS/FAIL;
5. mature-report counts for AOSP/Jieba/CC-CEDICT/THUOCL/FrequencyWords/runtime Chinese/runtime English/association/N-grams/translations;
6. validator PASS/FAIL;
7. any minimum repair file + reason;
8. compile result for `MainActivity`, `OrbitInputMethodService`, `PinyinImeEngine`, `PinyinCorrectionEngine`, `EnglishImeEngine`, `AssociationAsset`, `NextAssociationEngine`, `FluentLocalTranslationEngine`, `LongFormTranslationEngine`, `ContextTranslationEngine`, `SkinManager`, `ModelPackManager`;
9. BUILD SUCCESSFUL/FAILED;
10. APK absolute path and size;
11. version confirmation and prohibited-permission confirmation.
