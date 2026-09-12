# Orbit IME v0.15 Local IME Engine

This document defines the offline engine and mature-data path used by Orbit IME `0.15.0`.

## Six completed engine layers

1. Large dictionary importer.
2. Static frequency ranking.
3. Continuous Pinyin segmentation.
4. Sentence-level candidate generation/ranking.
5. Local 1/2/3-gram language model.
6. Compact sharded asset format.

## Build-time data path

A normal Gradle build runs:

```text
tools/test_ime_data_pipeline.py
-> tools/prepare_mature_ime_data.py
-> tools/ime_importer.py
-> app/src/main/assets/ime
```

The mature preparation uses pinned/hash-verified AOSP PinyinIME Chinese data and ESDB/SCOWL US-English vocabulary. See `DATA_SOURCES.md`.

This is build-time networking only. Runtime IME remains offline and has no `INTERNET` permission.

## Chinese runtime pipeline

```text
raw Pinyin
-> normalize
-> exact user/asset/sentence candidates
-> PinyinSegmenter (DP)
-> CompactLexiconAsset
-> bounded phrase beam search
-> NGramLanguageModel
-> CandidateRanker
-> UserDictionaryStore local boost
-> top 12
```

If a mature shard is missing/malformed, legacy packaged data remains a fail-safe candidate path.

## English runtime pipeline

```text
English composing buffer
-> EnglishImeEngine
-> CompactEnglishAsset
-> EnglishDictionary phrase/typo fallback
-> top candidates
```

When the imported English pack exceeds 10k entries, the importer writes:

```text
ime/english/a.odict ... z.odict
```

`CompactEnglishAsset` loads only the query's first-letter shard and keeps at most four shards in LRU memory. Small development packs may still use `ime/english.odict`.

## Frequency ranking

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

Exact spelling stays higher confidence than fuzzy/typo paths. Explicit repeated user selections receive a strong local boost without rewriting packaged assets.

## Continuous Pinyin segmentation

`PinyinSegmenter` uses dynamic programming and keeps several best paths.

Examples:

```text
nihaoma -> ni / hao / ma
nishishei -> ni / shi / shei
shurufa -> shu / ru / fa
```

A per-syllable cost prevents pathological over-segmentation such as preferring `ha + o` over `hao`. Apostrophes/spaces act as hard boundaries; keyboard `v` represents `ü`.

## Sentence-level beam search

`PinyinImeEngine` considers phrase spans up to four syllables and keeps a bounded beam.

Current limits:

```text
segmentation paths: 5
max phrase span: 4 syllables
entries per span: 5
beam width: 36
internal beam results: 16
visible candidates: 12
```

The lexical lookup merges mature asset entries and legacy/project fallback entries before frequency ranking rather than discarding one source merely because another source matched.

## Local N-gram model

`NGramLanguageModel` reads:

```text
ime/ngram1.odict
ime/ngram2.odict
ime/ngram3.odict
```

The mature preparation derives bounded character N-grams from accepted AOSP phrases and merges them with project-authored word/phrase N-grams.

Ranking evaluates both phrase-token evidence and Chinese-character sequence evidence, using the stronger signal rather than forcing an exact multi-character phrase to behave as one unscored token.

This is deterministic local count scoring, not a neural/cloud model.

## Compact asset format

`ORBIT_ODICT` uses tab-separated records with base36 integer frequency/counts.

Chinese:

```text
ime/lexicon/a.odict ... z.odict
normalized_pinyin<TAB>text<TAB>base36_frequency
```

English:

```text
ime/english/a.odict ... z.odict
normalized_key<TAB>base36_frequency<TAB>optional display candidates...
```

N-gram:

```text
token<TAB>count
token1<TAB>token2<TAB>count
token1<TAB>token2<TAB>token3<TAB>count
```

`CompactLexiconAsset` keeps at most six Chinese shards; `CompactEnglishAsset` keeps at most four English shards.

## Mature source policy

`data/ime_sources/mature_sources.json` pins:

- source URL;
- Git blob SHA-1;
- license identifier;
- attribution;
- conversion policy.

The preparation script refuses changed upstream bytes. The importer also fails closed for non-redistributable/unknown sources.

Third-party notices are copied into:

```text
ime/third_party_notices/
```

## Personalization

`UserDictionaryStore` persists only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not persist surrounding sentence, app/package name, field identity, or a full typed stream. Parsed records are cached in memory to avoid repeatedly decoding JSON during ranking.

## Fuzzy correction

`PinyinCorrectionEngine` is separate from exact segmentation and its candidates receive an explicit penalty.

Compatibility examples include:

```text
xhfnivh -> includes 喜欢你
xihvanni -> 喜欢你
nishis -> 你是谁
```

## Performance rules

- Never parse dictionaries in `onDraw`.
- Keep lexicon/English shard caches bounded.
- Keep beam width and candidate count bounded.
- Keep N-gram asset sizes bounded.
- Cache local-user records.
- Do not persist ranking context.
- Do not perform runtime network I/O.

## Device acceptance after build

At minimum test:

```text
nihaoma -> 你好吗 near top
nishishei -> 你是谁 near top
shurufa -> 输入法 near top
haishiyouwenti -> 还是有问题 near top
xhfnivh -> useful corrected Chinese candidates
```

Also test English composing/candidates, local-learning reorder/clear, translation result insertion, Clips, Pet, privacy mode, skins, and input-method switching.

## Post-build tuning order

After the first v0.15 mature-pack APK is tested on-device, tune only from measurements:

1. candidate latency;
2. top-1/top-3 accuracy on a fixed sentence set;
3. segmentation ambiguity;
4. static-frequency scale;
5. N-gram weights/limits;
6. user-frequency weight;
7. memory use.

Do not add a neural model before these deterministic layers are measured.
