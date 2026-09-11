# Orbit IME v0.15 Local IME Engine

This document defines the offline input-engine architecture introduced in v0.15.0.

## Goals

The engine must improve Chinese candidate quality without adding network access, cloud prediction, analytics, background input harvesting, or full typed-stream persistence.

The six v0.15 engineering goals are:

1. Large dictionary importer.
2. Static frequency ranking.
3. Continuous Pinyin segmentation.
4. Sentence-level candidate generation and ranking.
5. Local N-gram language model.
6. Compact asset format.

## Runtime pipeline

```text
raw Pinyin
  -> normalize
  -> exact user/asset/sentence candidates
  -> PinyinSegmenter (top segmentation paths)
  -> phrase lookup from CompactLexiconAsset
  -> beam candidate generation
  -> NGramLanguageModel
  -> CandidateRanker
  -> local UserDictionaryStore boost
  -> top 12 candidates
```

If the new engine fails or an asset is malformed, `UserDictionaryStore` falls back to the previous static candidate path so the keyboard still returns candidates.

## 1. Large dictionary importer

Tool:

```text
tools/ime_importer.py
```

Example invocation:

```text
python tools/ime_importer.py \
  --manifest data/ime_sources/manifest.example.json \
  --output app/src/main/assets/ime
```

Supported source formats:

- `orbit-tsv`: `pinyin<TAB>text<TAB>frequency`
- `cedict`: standard CC-CEDICT text records
- `english-tsv`: `word<TAB>frequency[<TAB>candidate...]`
- `ngram-tsv`: 2-gram or 3-gram token counts

The importer fails closed when:

- source file is missing;
- `redistribution_allowed` is false;
- a strict-mode license is not on the allow-list;
- attribution-required data does not include attribution metadata.

Current strict allow-list:

```text
PROJECT
Apache-2.0
MIT
BSD-2-Clause
BSD-3-Clause
CC-BY-4.0
CC-BY-SA-4.0
```

Adding a license to this list is a legal/product decision, not an engineering shortcut.

## 2. Frequency ranking

Every imported lexicon row has a positive integer frequency. Runtime `.odict` stores it in base36 to reduce text size.

`CandidateRanker` combines:

```text
static frequency
+ N-gram score
+ segmentation score
+ local user frequency
+ source priority
+ small length bonus
- correction penalty
```

Current scoring intent:

- Static frequency prevents rare words from dominating.
- Local user frequency is stronger than static frequency but cannot create arbitrary text by itself.
- Exact asset/user matches outrank fuzzy matches.
- Fuzzy/typo candidates remain available but pay an explicit penalty.

## 3. Pinyin segmentation

Class:

```text
PinyinSegmenter.kt
```

The segmenter uses dynamic programming and keeps several best paths.

Examples:

```text
nihaoma -> ni / hao / ma
nishishei -> ni / shi / shei
shurufa -> shu / ru / fa
```

Apostrophes and spaces are hard boundaries.

The syllable inventory is tone-less Hanyu Pinyin. Keyboard `v` represents `ü`.

## 4. Sentence-level candidate ranking

Class:

```text
PinyinImeEngine.kt
```

For every segmentation, the engine performs phrase-level beam search.

At each syllable position it considers spans up to four syllables. Example:

```text
ni / hao / ma

span 1: ni -> 你
span 2: nihao -> 你好
span 3: nihaoma -> 你好吗
```

Beam hypotheses keep:

```text
current syllable position
composed Chinese text
word/token list
aggregate static frequency
partial language-model score
```

Only the strongest hypotheses survive each beam step. This avoids the combinatorial explosion of enumerating every phrase combination.

Current constants:

```text
max segmentation paths: 5
max phrase span: 4 syllables
max entries per span: 5
beam width: 36
max beam results: 16
final visible candidates: 12
```

These values are tuning parameters, not user-facing settings.

## 5. Local N-gram language model

Class:

```text
NGramLanguageModel.kt
```

Supported packaged assets:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

The model uses weighted log-count features rather than a neural network.

Why:

- deterministic;
- very small;
- no model runtime dependency;
- no network;
- fast enough for keyboard latency;
- easy to regenerate from licensed corpora.

Current relative weighting favors trigram > bigram > unigram evidence.

If N-gram assets are absent, small project-authored fallback tables are used.

## 6. Compact asset format

Runtime reader:

```text
CompactLexiconAsset.kt
```

Preferred imported lexicon layout:

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

Lexicon record:

```text
normalized_pinyin<TAB>text<TAB>base36_frequency
```

Example:

```text
nihao<TAB>你好<TAB>l068
```

N-gram record:

```text
token1<TAB>token2<TAB>base36_count
```

or

```text
token1<TAB>token2<TAB>token3<TAB>base36_count
```

Why `.odict` instead of JSON:

- no repeated field names;
- line-streamable;
- easy to validate;
- easy to generate;
- Android `AssetManager` can read it directly;
- source dictionaries remain outside Kotlin bytecode.

The lexicon is sharded by the first normalized Pinyin letter. `CompactLexiconAsset` keeps at most six shards in an access-order LRU cache.

A small unsharded `ime/lexicon.odict` is committed as a development fallback. A real large-pack import should generate `ime/lexicon/a.odict` through `z.odict`.

## Local personalization

`UserDictionaryStore` remains the personalization layer.

It stores only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not store:

- surrounding sentence;
- app/package name;
- target field identity;
- full typed stream;
- clipboard history unless explicitly saved in Clips.

The engine reads the learned frequency only for ranking.

## Fuzzy and typo path

`PinyinCorrectionEngine` remains separate from exact segmentation.

This is intentional: fuzzy candidates must not silently become equivalent to exact spelling. They are added with a correction penalty so an exact valid Pinyin candidate wins when available.

Examples include:

```text
xhfnivh -> 喜欢你 / 想和你说 / 需要优化
xihvanni -> 喜欢你
nishis -> 你是谁
```

## Data source and licensing rule

Do not paste arbitrary GitHub dictionaries into the APK.

Use `DATA_SOURCES.md` and a manifest entry for every imported dataset. Keep attribution and source URL. Share-alike data must be handled according to its license.

## Performance rules

- Never parse dictionary assets in `onDraw`.
- Keep asset parsing off repeated per-key allocations where possible.
- Use lexicon sharding and a small LRU cache.
- Keep candidate limit bounded.
- Keep beam width bounded.
- Do not persist context strings for ranking.
- Do not do network I/O.

## v0.15 acceptance targets

At minimum:

```text
nihaoma -> 你好吗 near top
nishishei -> 你是谁 near top
shurufa -> 输入法 near top
haishiyouwenti -> 还是有问题 near top
xhfnivh -> Chinese corrected candidates are available
```

Local learning should move repeatedly selected candidates upward without changing the packaged dictionary.

## Next tuning work after build

After v0.15 compiles and runs on-device, tune in this order:

1. candidate latency;
2. top-1 accuracy for a fixed test list;
3. segmentation ambiguity;
4. static frequency calibration;
5. N-gram weight calibration;
6. larger licensed dictionary import;
7. larger licensed N-gram pack.

Do not add a neural model until these deterministic layers are measured first.
