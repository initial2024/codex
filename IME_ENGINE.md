# Orbit IME v0.15 Local IME Engine

This document defines the offline input-engine architecture introduced in v0.15.0.

## Goals

The six v0.15 engineering goals are:

1. Large dictionary importer.
2. Static frequency ranking.
3. Continuous Pinyin segmentation.
4. Sentence-level candidate generation and ranking.
5. Local N-gram language model.
6. Compact asset format.

The engine must remain offline and must not persist a full typed stream.

## Runtime pipeline

Chinese:

```text
raw Pinyin
  -> normalize
  -> exact user/asset/sentence candidates
  -> PinyinSegmenter
  -> CompactLexiconAsset
  -> bounded phrase beam search
  -> NGramLanguageModel
  -> CandidateRanker
  -> UserDictionaryStore personalization
  -> top 12 candidates
```

English:

```text
raw English composing buffer
  -> CompactEnglishAsset
  -> packaged frequency candidates
  -> EnglishDictionary phrase/typo fallback
  -> EnglishImeEngine ranking
  -> top candidates
```

If the new Chinese engine fails or an asset is malformed, `UserDictionaryStore` retains the previous static candidate fallback path.

## 1. Large dictionary importer

Tool:

```text
tools/ime_importer.py
```

Example:

```text
python tools/ime_importer.py \
  --manifest data/ime_sources/manifest.example.json \
  --output app/src/main/assets/ime
```

Supported source formats:

- `orbit-tsv`: `pinyin<TAB>text<TAB>frequency`
- `cedict`: standard CC-CEDICT text records
- `english-tsv`: `word<TAB>frequency[<TAB>candidate...]`
- `ngram-tsv`: 1-gram, 2-gram, or 3-gram token counts

The importer fails closed when:

- a source file is missing;
- `redistribution_allowed` is false;
- a strict-mode license is not on the allow-list;
- attribution-required data has no attribution metadata.

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

This is an engineering guardrail, not a legal conclusion.

## 2. Frequency ranking

Every imported lexicon/English row has a positive integer frequency. Runtime `.odict` stores counts in base36.

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

Local user frequency has stronger weight than static frequency so repeated choices can move upward without rewriting packaged assets.

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

The scoring includes a per-syllable cost to avoid over-segmentation such as `hao -> ha + o`.

Apostrophes/spaces are hard boundaries when supplied to the engine. Keyboard `v` represents `ü`.

## 4. Sentence-level candidate ranking

Class:

```text
PinyinImeEngine.kt
```

For every segmentation, the engine performs bounded phrase-level beam search. At each syllable position it checks spans up to four syllables.

Example:

```text
ni / hao / ma

span 1: ni -> 你
span 2: nihao -> 你好
span 3: nihaoma -> 你好吗
```

Beam hypotheses retain:

```text
current syllable position
composed text
candidate tokens
aggregate static frequency
partial language-model score
```

Current bounds:

```text
max segmentation paths: 5
max phrase span: 4 syllables
max entries per span: 5
beam width: 36
max internal beam results: 16
visible candidates: 12
```

## 5. Local N-gram language model

Class:

```text
NGramLanguageModel.kt
```

Supported assets:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

The model uses weighted log-count features, not a neural runtime.

Current relative weighting favors:

```text
trigram > bigram > unigram
```

If an N-gram asset is missing, small project-authored fallback tables keep the engine functional.

## 6. Compact asset format

Readers:

```text
CompactLexiconAsset.kt
CompactEnglishAsset.kt
```

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

Lexicon record:

```text
normalized_pinyin<TAB>text<TAB>base36_frequency
```

English record:

```text
normalized_key<TAB>base36_frequency<TAB>optional candidates...
```

N-gram records:

```text
token<TAB>base36_count
```

```text
token1<TAB>token2<TAB>base36_count
```

```text
token1<TAB>token2<TAB>token3<TAB>base36_count
```

Why `.odict` instead of JSON:

- no repeated field names;
- line-streamable;
- easy to validate and regenerate;
- Android `AssetManager` can read it directly;
- dictionaries remain outside Kotlin bytecode.

The Pinyin lexicon is sharded by first normalized Pinyin letter. `CompactLexiconAsset` keeps at most six shards in an access-order LRU cache.

A small unsharded `ime/lexicon.odict` plus `english.odict` and N-gram assets are committed as project-authored development fallbacks. A real large pack should be produced by the importer.

## Local personalization

`UserDictionaryStore` stores only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not persist surrounding sentence, app/package name, field identity, or full typed stream.

Parsed entries are cached in memory so candidate ranking does not repeatedly parse JSON.

## Fuzzy and typo path

`PinyinCorrectionEngine` remains separate from exact segmentation. Fuzzy candidates receive an explicit penalty so valid exact Pinyin is preferred.

Examples:

```text
xhfnivh -> 喜欢你 / 想和你说 / 需要优化
xihvanni -> 喜欢你
nishis -> 你是谁
```

English typo data remains available through `EnglishDictionary`, while imported English frequencies are read through `CompactEnglishAsset` and ranked by `EnglishImeEngine`.

## Data licensing rule

Do not paste arbitrary GitHub dictionaries into the APK.

Use `DATA_SOURCES.md` and a source manifest for every imported dataset. Keep source URL, license, redistribution flag, and required attribution.

## Performance rules

- Never parse dictionary assets in `onDraw`.
- Use lexicon sharding and bounded caches.
- Keep candidate count bounded.
- Keep beam width bounded.
- Cache parsed local-user records.
- Do not persist ranking context strings.
- Do not do network I/O.

## v0.15 acceptance targets

At minimum:

```text
nihaoma -> 你好吗 near top
nishishei -> 你是谁 near top
shurufa -> 输入法 near top
haishiyouwenti -> 还是有问题 near top
xhfnivh -> corrected Chinese candidates available
```

English imported/fallback tests:

```text
build -> build / build failed / build succeeded
translate -> translate / translation
trasnlate -> translate
permision -> permission
```

Repeatedly selecting a valid Chinese candidate should raise it through local user-frequency weighting.

## Next tuning after build

After v0.15 compiles and runs on-device, tune in this order:

1. candidate latency;
2. fixed top-1 accuracy test set;
3. segmentation ambiguity;
4. static-frequency calibration;
5. N-gram weight calibration;
6. larger licensed lexicon import;
7. larger licensed N-gram pack.

Do not add a neural model until these deterministic layers are measured first.
