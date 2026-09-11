# Codex handoff task: validate and build Orbit IME v0.15 later

## Status

Orbit IME `0.15.0` Local IME Engine has been prepared.

Do not build until the user explicitly asks to start the APK build.

## Goal

When build is requested, first perform a static/compile review of the six v0.15 engine layers, make only the minimum compilation fixes, then build the debug APK.

Repository:

```text
https://github.com/initial2024/codex
```

## Required environment

```text
JDK 17
Android SDK platform 35
Android build-tools 35.0.0
Gradle 8.10.2 or compatible
Python 3.10+ for importer validation
```

## v0.15 architecture that must remain intact

```text
raw Pinyin
-> PinyinSegmenter
-> CompactLexiconAsset
-> PinyinImeEngine beam search
-> NGramLanguageModel
-> CandidateRanker
-> UserDictionaryStore personalization
-> top candidates
```

Do not replace this with another hardcoded giant Kotlin map.

## Stage A — importer validation

Run from repository root:

```bash
python tools/ime_importer.py \
  --manifest data/ime_sources/manifest.example.json \
  --output build/ime-import-test
```

Confirm the importer:

1. exits successfully with the project-authored seed manifest;
2. creates `manifest.json`;
3. creates sharded `lexicon/*.odict` files;
4. creates `english.odict` when English data is present;
5. creates `ngram1.odict`, `ngram2.odict`, and `ngram3.odict` when those counts are present;
6. writes frequencies/counts in base36;
7. fails closed for a source marked `redistribution_allowed=false`;
8. fails closed in strict mode for a license not on the allow-list;
9. preserves source/license/attribution metadata in the generated manifest.

Do not copy any third-party dictionary into the repo merely to make this test larger.

## Stage B — static Kotlin review

Inspect these v0.15 files:

```text
app/src/main/java/com/ccwu/orbitime/CompactLexiconAsset.kt
app/src/main/java/com/ccwu/orbitime/PinyinSegmenter.kt
app/src/main/java/com/ccwu/orbitime/NGramLanguageModel.kt
app/src/main/java/com/ccwu/orbitime/CandidateRanker.kt
app/src/main/java/com/ccwu/orbitime/PinyinImeEngine.kt
app/src/main/java/com/ccwu/orbitime/UserDictionaryStore.kt
app/src/main/java/com/ccwu/orbitime/PinyinSentenceDictionary.kt
```

Confirm:

1. `CompactLexiconAsset` reads `ORBIT_ODICT` and falls back safely when a shard is absent.
2. Lexicon shards are cached with a bounded LRU, not loaded all at once.
3. `PinyinSegmenter` uses dynamic programming and does not prefer pathological over-segmentation such as `hao -> ha + o`.
4. `nihaoma` can produce segmentation `ni / hao / ma`.
5. `nishishei` can produce `ni / shi / shei`.
6. `shurufa` can produce `shu / ru / fa`.
7. `PinyinImeEngine` uses bounded phrase spans and bounded beam width.
8. `NGramLanguageModel` supports local 1/2/3-gram files and has a safe fallback.
9. `CandidateRanker` combines static frequency, N-gram, segmentation, user frequency, source priority, and correction penalty.
10. Fuzzy/typo candidates are penalized rather than treated as exact spellings.
11. `UserDictionaryStore` routes through the new engine but retains the legacy fallback path.
12. `UserDictionaryStore` does not recursively call its own candidate API from the engine.
13. User-dictionary records remain limited to pinyin/text/frequency/updatedAt.
14. User-dictionary JSON is cached in memory instead of reparsed for every candidate score.

## Stage C — version and privacy checks

Confirm:

```text
versionCode = 15
versionName = 0.15.0
```

Settings page should show only:

```text
About · v0.15.0
```

GitHub Actions artifact:

```text
orbit-ime-v0.15-debug-apk
```

Workflow must remain manual-only:

```text
workflow_dispatch
```

Manifest must not add:

```text
INTERNET
Accessibility
SYSTEM_ALERT_WINDOW / overlay
background-service permissions
advertising identifiers
```

## Stage D — build

Only after the user explicitly asks to build, run:

```bash
gradle assembleDebug --no-daemon
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Stage E — on-device functional acceptance

At minimum verify:

### Continuous Pinyin / segmentation

```text
nihaoma -> 你好吗 near the top
nishishei -> 你是谁 near the top
shurufa -> 输入法 near the top
haishiyouwenti -> 还是有问题 near the top
```

### Existing shortcut / correction behavior

```text
nh -> 你好 / 你好吗
hsywt -> 还是有问题
myfyjg -> 没有翻译结果
bscgfy -> 不是成功翻译
sjkb -> 数据库不够
xhfnivh -> includes 喜欢你
```

### Personal ranking

1. Pick a non-first valid Chinese candidate repeatedly.
2. Re-enter the same Pinyin.
3. Confirm its local user-frequency boost can move it upward.
4. Clear the local user dictionary.
5. Confirm packaged default ordering is restored.

### Regression

Retest:

- English composing/candidates.
- Pinyin correction.
- Clips.
- local translation and prompt fallback.
- keyboard pet panel.
- skin selection.
- privacy mode.
- Android input-method switch button.

## Non-negotiable constraints

Do not add:

- INTERNET permission
- cloud prediction
- cloud dictionary sync
- cloud translation
- external translation API
- ad SDK
- analytics SDK
- Accessibility permission
- overlay / floating-window permission
- background service
- notification spam
- full typed-key-stream persistence
- surrounding-sentence persistence
- app/package-name learning history
- clipboard background harvesting
- AI pet chat
- paid gacha
- Pinyin 9-key
- Wubi
- handwriting recognition
- Canvas keyboard rewrite
- Compose migration
- Room/Realm migration
- billing implementation
- skin marketplace

## Fix policy

If compilation fails, make the minimum necessary Kotlin, resource, Gradle, or IME-metadata repair.

Do not remove the six v0.15 engine layers to make the build pass.
Do not replace the importer with copied unlicensed dictionary data.
Do not expand product scope during build repair.

## Final build report format

Return:

```text
1. git status
2. files changed by Codex
3. importer validation result
4. Kotlin/static review result
5. gradle assembleDebug command executed or not
6. build success/failure
7. APK path if successful
8. key error + minimal fix if failed
9. confirmation that prohibited permissions/features were not added
```
