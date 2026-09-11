# Orbit IME Android

Orbit IME is a privacy-first Android input method with local English candidates, Pinyin 26-key input, local learning, offline phrase translation, clipboard tools, skins, and a keyboard pet module.

## Current version

```text
0.15.0
```

v0.15 introduces the first real local IME engine layer instead of continuing to grow hardcoded Kotlin maps.

## v0.15 local IME engine

The Chinese candidate pipeline is now:

```text
raw Pinyin
-> normalization
-> exact asset / user / sentence candidates
-> dynamic-programming Pinyin segmentation
-> phrase beam search
-> static frequency score
-> local 1/2/3-gram language-model score
-> local user-frequency boost
-> fuzzy/typo penalty
-> top 12 candidates
```

Implementation files:

```text
CompactLexiconAsset.kt
PinyinSegmenter.kt
NGramLanguageModel.kt
CandidateRanker.kt
PinyinImeEngine.kt
UserDictionaryStore.kt
```

Detailed architecture is in `IME_ENGINE.md`.

## 1. Large dictionary importer

Build-time tool:

```text
tools/ime_importer.py
```

Example:

```text
python tools/ime_importer.py --manifest data/ime_sources/manifest.example.json --output app/src/main/assets/ime
```

Supported source formats:

- Orbit TSV: Pinyin + text + frequency.
- CC-CEDICT text format.
- English TSV frequency/candidate data.
- 1-gram, 2-gram, and 3-gram TSV counts.

The importer rejects missing sources, non-redistributable sources, and unknown licenses in strict mode.

Project-authored seed inputs live under:

```text
data/ime_sources/
```

## 2. Frequency ranking

Imported lexicon entries carry integer frequency values. Candidate ranking combines:

```text
static frequency
+ sentence N-gram score
+ Pinyin segmentation quality
+ local user frequency
+ source priority
- fuzzy/typo penalty
```

User selection frequency is deliberately stronger than static frequency so repeated personal choices move upward over time.

## 3. Continuous Pinyin segmentation

`PinyinSegmenter.kt` uses dynamic programming and preserves several high-quality segmentation paths.

Examples:

```text
nihaoma -> ni / hao / ma
nishishei -> ni / shi / shei
shurufa -> shu / ru / fa
```

It also respects apostrophe/space boundaries and uses `v` for keyboard `ü` input.

## 4. Sentence-level candidate generation

`PinyinImeEngine.kt` performs phrase-level beam search over segmented syllables. It can combine single syllables and multi-syllable phrases instead of requiring the entire typed string to exist as one exact hardcoded key.

Current bounded search parameters:

```text
max segmentation paths: 5
max phrase span: 4 syllables
beam width: 36
max internal beam results: 16
visible candidates: 12
```

These bounds prevent combinatorial explosion and keep keyboard latency predictable.

## 5. Local N-gram language model

`NGramLanguageModel.kt` supports:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

It is a deterministic count-based model, not a neural model. Trigram evidence has more weight than bigram evidence, and bigram evidence has more weight than unigram evidence.

The model runs fully offline.

## 6. Compact asset format

Imported runtime assets use `ORBIT_ODICT`.

Preferred large-pack layout:

```text
app/src/main/assets/ime/
  manifest.json
  lexicon/
    a.odict
    b.odict
    ...
    z.odict
  english.odict
  ngram1.odict
  ngram2.odict
  ngram3.odict
```

Lexicon line:

```text
normalized_pinyin<TAB>text<TAB>base36_frequency
```

The lexicon is sharded by first Pinyin letter. `CompactLexiconAsset` keeps only six shards in its LRU memory cache.

A small project-authored unsharded fallback `ime/lexicon.odict` is committed so the engine remains usable before a large dictionary pack is generated.

## Local personalization

`UserDictionaryStore` stores only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not store full chat text, the target app, field identity, or surrounding sentences.

The store now keeps an in-memory cache so ranking does not repeatedly parse JSON on every candidate lookup.

## Fuzzy and typo correction

`PinyinCorrectionEngine.kt` remains a lower-confidence path. Exact candidates rank above fuzzy candidates.

Examples already covered include:

```text
xhfnivh -> 喜欢你 / 想和你说 / 需要优化
xihvanni -> 喜欢你
nishis -> 你是谁
```

## Existing features retained

- English composing buffer and candidate selection.
- Expanded Chinese and English quick phrases.
- Local clipboard panel.
- Local phrase translation with prompt fallback.
- Keyboard pet panel with check-in, hatching, switching, outfits, catalog, and local growth.
- Orbit Dark, Orbit Light, AMOLED Black, Study Blue, and Pro Aurora placeholder skin.
- Privacy mode for password-like fields.

## Privacy boundary

v0.15 intentionally keeps:

- no `INTERNET` permission;
- no analytics SDK;
- no ad SDK;
- no Accessibility permission;
- no overlay / floating-window permission;
- no cloud prediction;
- no cloud dictionary sync;
- no cloud translation;
- no background clipboard harvesting;
- no full typed-stream persistence.

## Data-source rule

Do not copy arbitrary GitHub dictionaries into the APK.

Every bulk source must have:

```text
source name
source URL
explicit license
redistribution permission
required attribution
```

See `DATA_SOURCES.md`.

## Important limitation

v0.15 completes the engine architecture, but the committed fallback asset is intentionally small. Mature commercial-IME coverage still requires importing a large licensed phrase dictionary and large licensed frequency/N-gram data through the importer.

The architecture is now ready for that without rewriting the keyboard service.

## Build policy

Do not build automatically on push. GitHub Actions stays manual-only through `workflow_dispatch`.

When requested, build with:

```text
JDK 17
Android SDK 35
Gradle 8.10.2

gradle assembleDebug --no-daemon
```

Expected APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Manual GitHub Actions artifact:

```text
orbit-ime-v0.15-debug-apk
```

## v0.15 manual acceptance list

Test at minimum:

```text
nihaoma -> 你好吗 near top
nishishei -> 你是谁 near top
shurufa -> 输入法 near top
haishiyouwenti -> 还是有问题 near top
xhfnivh -> Chinese corrected candidates
```

Then select a non-first candidate repeatedly and confirm local learning can move it upward.

Also retest English candidates, translation, Clips, pet panel, skins, privacy mode, and input-method switching.

## Commercial direction

The intended direction remains free base + paid Pro unlock. Do not put ads inside the keyboard input surface. v0.15 contains no billing, advertising, analytics, network, or external translation API implementation.
