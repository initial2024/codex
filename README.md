# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Chinese/English prediction, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local stickers and skins.

## Current version

```text
0.20.0
```

v0.20 focuses on five real-device experience gaps:

1. optional local context/block translation while ordinary/free users remain single-sentence only;
2. better sticker compatibility when WeChat/QQ do not expose Android IME image-content insertion;
3. a stronger four-character idiom layer and common software/platform vocabulary;
4. actual post-commit next-word/next-phrase association;
5. substantially larger Chinese/English candidate pools.

## Build-time data pipeline

A normal user-test build runs:

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> AOSP PinyinIME + Jieba + pinned ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> broad ESDB-large normalization
-> CC-CEDICT four-character phrase/idiom boost
-> project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

The historical `augment_v018_data.py` filename remains intentional; that licensed CC-CEDICT/Unicode stage is still mandatory.

Required minimums include:

```text
AOSP Chinese >= 40,000
Jieba-derived additions >= 40,000
CC-CEDICT >= 110,000
CC-CEDICT four-character layer >= 3,000
project software/platform vocabulary >= 100
combined runtime Chinese >= 150,000
English >= 100,000
Unicode Emoji >= 3,000
ZH->EN translation >= 80,000
EN->ZH translation >= 50,000
1-gram >= 5,000
2-gram >= 30,000
3-gram >= 30,000
```

Exact generated counts are written to `app/src/main/assets/ime/mature-report.json`.

## Chinese input and candidate depth

```text
continuous Pinyin
-> exact packaged/user candidates
-> DP segmentation
-> compact sharded lexicon
-> adaptive Beam Search
-> frequency + 1/2/3-gram
-> personal frequency boost
-> prefix association
-> lower-confidence fuzzy/keyboard-typo recovery
-> up to 32 visible candidates
```

v0.20 expands both the UI and short-input internal search pool:

```text
MAX_RESULTS = 32
short-input Beam width = 72
Beam results = 32
prefix pool = 64
correction entries per variant = 5
```

Long input still uses narrower adaptive Beam/segmentation limits, avoiding a simple candidate-count-for-latency trade.

Candidate commit continues to replace Android's active composing region rather than appending after raw letters.

## Four-character idioms and software names

v0.20 does not import a separate internet-scraped idiom repository. It derives a higher-priority four-character phrase/idiom layer from the already pinned CC-CEDICT dataset, preserving its CC BY-SA 4.0 data boundary.

A project-authored vocabulary adds common software/platform/product names, including:

```text
微信 / QQ / 支付宝 / 淘宝 / 京东 / 抖音 / 小红书 / 哔哩哔哩
ChatGPT / OpenAI / Codex / DeepSeek / Qwen / Gemini / Claude
GitHub / VS Code / Android Studio / Gradle / Kotlin / Python
Docker / Vercel / Cloudflare / Supabase / Windows / Android / iOS
```

The final ESDB/SCOWL `en_US-large` pass now accepts valid uppercase proper-name/acronym spellings under normalized lowercase lookup keys, fixing the old path that could stop around 81k entries by discarding all non-lowercase rows.

## Next-word / next-phrase association

After a Chinese candidate is committed, Orbit no longer returns only to fixed quick phrases.

When composing buffers are empty, the keyboard reads a bounded local text tail and combines:

```text
project-authored high-confidence associations
+
packaged bigram/trigram counts
+
short Beam continuation generation
```

This produces up to 24 next-word/short-phrase associations. Surrounding text is used in memory only and is not persisted into the personal dictionary.

## Personalization

```text
Free base: 20,000 learned entries
Future Pro capacity placeholder: 100,000 entries
```

Learning uses app-private `dictionary.tsv + journal.tsv`, with periodic compaction and local migration from old SharedPreferences data. Persistent fields remain only Pinyin, candidate text, frequency and timestamp.

## English

The mature path uses pinned ESDB/SCOWL `en_US-large`. English stays in Android composing state and exposes up to 32 candidates. Project-authored common English, phrase and typo overlays remain stronger than broad SCOWL coverage.

## Translation

Base/free translation remains local **single-sentence translation**:

```text
project exact phrase tables
-> CC-CEDICT exact lexical lookup
-> CC-CEDICT sharded longest-match composition
-> local composer
-> explicit unavailable state
```

v0.20 adds optional **Pro-gated local context/block translation**. When explicitly enabled, Orbit reads at most the previous two sentences / a bounded cursor tail in memory and shows a context/block translation reference alongside the current sentence translation. The current sentence remains the independently inserted translation so old conversation text is not duplicated.

This is deterministic local context/block assistance, not neural semantic disambiguation comparable to a cloud NMT/LLM system. Ordinary/free users never read previous sentences for translation.

## Stickers and WeChat/QQ compatibility

Orbit has 16 pets × 8 sticker states = 128 local PNG sticker definitions.

Sticker delivery uses:

```text
1. image/png supported -> InputContentInfo / commitContent
2. unsupported -> PNG content URI copied to system clipboard + temporary read grant to current target package
3. if image clipboard preparation also fails -> Emoji fallback
```

For path 2, Orbit prompts the user to long-press paste in WeChat/QQ/etc. The target app ultimately controls whether image-content or URI-image clipboard paste is accepted, so this is a compatibility path rather than a guaranteed bypass. Orbit does not use Accessibility, overlay, external storage or runtime downloading to circumvent target-app restrictions.

## Emoji / kaomoji / symbols

The expression panel contains Unicode Emoji 17.0, project categories, hundreds of project-authored kaomoji variants, Recent, and pet stickers.

The `123` keyboard has seven symbol pages: 常用 / 标点 / 括号 / 数学 / 货币 / 箭头 / 标记. Letter keys retain visible long-press digit/punctuation mappings.

## Clipboard

Clipboard remains `Pinned + Recent`. Text capture is active only while the IME window is visible. Image sticker clipboard URIs are not stored as text history.

## Pets and outfits

Current local catalog: 16 pets, 24 outfits, 128 local sticker definitions. New pets map to stable renderer archetypes and outfits map to visible accessory layers. Pet micro-feedback stores only a short action code/timestamp, not surrounding message text.

## Runtime privacy boundary

Orbit IME v0.20 intentionally has no INTERNET, ads/analytics/tracking, Accessibility, overlay/floating-window, cloud prediction/translation, external translation API, background input/clipboard harvesting, full typed-stream persistence, or external-storage permission.

## Data licenses

```text
AOSP PinyinIME             Apache-2.0
Jieba frequency data       MIT
CC-CEDICT                  CC BY-SA 4.0
ESDB/SCOWL en_US-large     ESDB redistribution notice
Unicode Emoji 17.0         Unicode License v3
```

The four-character layer is CC-CEDICT-derived; common software/platform names are project-authored vocabulary.

## Build

GitHub Actions remains manual-only through `workflow_dispatch`.

```text
JDK 17
Python 3.12 recommended
Android SDK 35
Android build-tools 35.0.0
Gradle 8.10.2
```

Command:

```text
gradle assembleDebug --no-daemon
```

Do not use `-PorbitSkipMatureImeData=true` for the user-test APK.

Expected APK: `app/build/outputs/apk/debug/app-debug.apk`

Artifact: `orbit-ime-v0.20-debug-apk`

## Post-build acceptance priorities

1. confirm raw Pinyin/English is replaced rather than retained;
2. test common short queries and verify substantially more candidate choices;
3. test four-character phrases/idioms and common software/platform names;
4. select Chinese words and verify meaningful next-word/phrase association;
5. test long uninterrupted Pinyin latency after wider short-input tuning;
6. verify free translation remains single-sentence only;
7. in a Pro-enabled test configuration, toggle context mode and verify only in-memory previous-two-sentence context/block reference;
8. test direct sticker commit in WeChat/QQ; when unsupported, test PNG clipboard + long-press paste before Emoji fallback;
9. re-test personal learning, clipboard, Emoji/kaomoji, symbols/long-press, pets and privacy mode.
