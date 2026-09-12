# Orbit IME Android

Orbit IME is a privacy-first Android input method with local Chinese/English prediction, local personalization, clipboard history, offline translation, visual keyboard pets, Emoji/kaomoji, local stickers and skins.

## Current version

```text
0.20.0
```

v0.20 focuses on five real-device experience gaps:

1. optional local context translation while ordinary/free users remain single-sentence only;
2. better sticker compatibility when WeChat/QQ do not expose Android IME image-content insertion;
3. a stronger four-character idiom layer and common software/platform vocabulary;
4. actual post-commit next-word/next-phrase association;
5. substantially larger Chinese/English candidate pools.

## Build-time data pipeline

A normal user-test build runs:

```text
tools/test_ime_data_pipeline_v020.py
-> tools/prepare_mature_ime_data.py
-> AOSP PinyinIME + Jieba + ESDB/SCOWL en_US-large
-> tools/augment_v018_data.py
-> CC-CEDICT lexicon/translation + Unicode Emoji 17.0
-> tools/augment_v020_data.py
-> CC-CEDICT four-character idiom boost + project software vocabulary
-> tools/validate_mature_ime_assets.py
-> Android compilation
```

The historical `augment_v018_data.py` filename remains intentional; its licensed CC-CEDICT/Unicode stage is still mandatory.

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

Exact generated counts are written to:

```text
app/src/main/assets/ime/mature-report.json
```

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

v0.20 expands the short-input internal search pool as well as the UI limit:

```text
MAX_RESULTS = 32
short-input Beam width = 72
Beam results = 32
prefix pool = 64
correction entries per variant = 5
```

Long input still uses adaptive narrower Beam/segmentation limits so candidate depth does not simply trade away typing latency.

Candidate commit continues to replace Android's active composing region rather than appending after raw letters.

## Four-character idioms and software names

v0.20 does not import an additional internet-scraped idiom repository. Instead it derives a higher-priority four-character phrase/idiom layer from the already pinned/audited CC-CEDICT dataset and preserves its CC BY-SA 4.0 data boundary.

A separate project-authored vocabulary adds common software/platform/product names, including categories such as:

```text
微信 / QQ / 支付宝 / 淘宝 / 京东 / 抖音 / 小红书 / 哔哩哔哩
ChatGPT / OpenAI / Codex / DeepSeek / Qwen / Gemini / Claude
GitHub / VS Code / Android Studio / Gradle / Kotlin / Python
Docker / Vercel / Cloudflare / Supabase / Windows / Android / iOS
```

These entries receive curated product-level frequencies rather than pretending an alphabetic word list is a corpus frequency source.

## Next-word / next-phrase association

After a Chinese candidate is committed, Orbit no longer immediately falls back only to fixed quick phrases.

When the composing buffers are empty, the keyboard can read a bounded local text tail before the cursor and combine:

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

Learning uses app-private:

```text
files/orbit-user-dictionary/dictionary.tsv
files/orbit-user-dictionary/journal.tsv
```

Normal selections append small journal rows and periodic compaction rewrites the base file. Old SharedPreferences JSON data is migrated locally once.

Persisted learning fields remain only:

```text
pinyin
candidate text
frequency
updatedAt
```

## English

The mature path uses pinned ESDB/SCOWL `en_US-large`. English stays in Android composing state and now exposes up to 32 candidates. Project-authored common English, phrases and typo overlays remain available above the broad word-list coverage.

## Translation

Base/free translation remains local **single-sentence translation**:

```text
project exact phrase tables
-> CC-CEDICT exact lexical lookup
-> CC-CEDICT sharded longest-match composition
-> local composer
-> explicit unavailable state
```

v0.20 adds an optional **Pro-gated context translation** switch. When available and explicitly enabled, Orbit reads at most the previous two sentences / a bounded cursor tail in memory and generates a local context/block translation preview alongside the current sentence translation. The current sentence translation is still inserted separately so old conversation text is not duplicated into the target app.

This feature is deterministic/local context assistance, not a claim of neural machine-translation parity with cloud systems. Ordinary/free users never read surrounding sentences for translation.

## Stickers and WeChat/QQ compatibility

Orbit has:

```text
16 pets
8 sticker states per pet
128 local PNG sticker definitions
```

Sticker delivery now uses a compatibility ladder:

```text
1. target editor advertises image/png
   -> InputContentInfo / commitContent

2. direct IME image content unsupported
   -> generated PNG content URI copied to system clipboard
   -> temporary read grant to current target package
   -> user is prompted to long-press paste in WeChat/QQ/etc.

3. target app also rejects image clipboard paste
   -> Emoji fallback
```

Android target apps decide whether they accept image-content or URI-image paste, so no keyboard can guarantee the same path works in every WeChat/QQ build. Orbit does not use Accessibility, overlay, external storage or runtime downloading to bypass those app restrictions.

## Emoji / kaomoji / symbols

The expression panel contains Unicode Emoji 17.0, project categories, hundreds of project-authored kaomoji variants, Recent, and pet stickers.

The `123` keyboard has seven symbol pages:

```text
常用 / 标点 / 括号 / 数学 / 货币 / 箭头 / 标记
```

26-key letters retain visible long-press digit/punctuation mappings.

## Clipboard

Clipboard remains `Pinned + Recent`. Text capture is active only while the IME window is visible. Image sticker clipboard URIs are not stored as text history.

## Pets and outfits

Current local catalog:

```text
16 pets
24 outfits
8 sticker states per pet
128 local sticker definitions
```

New catalog pets map to stable visual archetypes; outfit variants map to visible accessory layers. Pet micro-feedback stores only a short action code/timestamp, not surrounding message text.

## Runtime privacy boundary

Orbit IME v0.20 intentionally has:

- no `INTERNET` permission;
- no ads/analytics/tracking;
- no Accessibility permission;
- no overlay/floating-window permission;
- no cloud prediction/dictionary sync/translation;
- no external translation API;
- no background input/clipboard harvesting;
- no full typed-stream persistence;
- no external-storage permission.

## Data licenses

Pinned third-party build data remains independently licensed:

```text
AOSP PinyinIME             Apache-2.0
Jieba frequency data       MIT
CC-CEDICT                  CC BY-SA 4.0
ESDB/SCOWL en_US-large     ESDB redistribution notice
Unicode Emoji 17.0         Unicode License v3
```

The four-character layer is a CC-CEDICT-derived data layer. Common software/platform names are project-authored vocabulary.

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

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Artifact:

```text
orbit-ime-v0.20-debug-apk
```

## Post-build acceptance priorities

1. Confirm raw Pinyin/English is replaced rather than retained before selected candidates.
2. Test common short Pinyin queries and verify substantially more candidate choices are available.
3. Test four-character idioms and common software/platform names.
4. Select a Chinese word and verify the idle bar changes to meaningful next-word/phrase associations.
5. Test long uninterrupted Pinyin latency after the wider short-input Beam tuning.
6. Verify free translation remains single-sentence only.
7. If Pro is enabled in a test build, toggle context translation and verify the previous two sentences appear only as in-memory context/block reference.
8. In WeChat/QQ, test direct sticker commit; when unsupported, verify the PNG is copied and manual long-press image paste is offered before Emoji fallback.
9. Re-test personal learning, clipboard, Emoji/kaomoji, symbols/long-press, pets and privacy mode.
