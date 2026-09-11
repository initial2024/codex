# Orbit IME data source strategy

Orbit IME needs professional-scale Pinyin candidates and frequency data while remaining offline, privacy-first, and suitable for future commercial distribution.

## v0.15 status

Version `0.15.0` completes the first import/runtime architecture:

- `tools/ime_importer.py`: build-time licensed-data importer.
- `data/ime_sources/manifest.example.json`: source/license manifest example.
- `CompactLexiconAsset.kt`: compact lexicon asset reader with shard LRU cache.
- `PinyinSegmenter.kt`: continuous-Pinyin dynamic-programming segmenter.
- `NGramLanguageModel.kt`: local 1/2/3-gram scoring.
- `CandidateRanker.kt`: static/user/N-gram/fuzzy score combiner.
- `PinyinImeEngine.kt`: sentence beam search and candidate generation.
- `UserDictionaryStore.kt`: local personalization with an in-memory read cache.

The committed `.odict` data is still a project-authored seed/fallback pack. A future large pack should be generated through the importer rather than manually pasted into Kotlin.

## Source manifest requirement

Every bulk imported dataset must declare:

```text
name
path
format
license
source URL
redistribution_allowed
attribution
optional default frequency
```

The importer fails closed when redistribution is not explicitly allowed or, in strict mode, when the license is unknown.

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

This allow-list is not a legal conclusion. It is an engineering guardrail. Before shipping third-party data, confirm the specific dataset terms and required notices.

## Public sources reviewed

### Android Open Source Project PinyinIME

Reference:

```text
https://android.googlesource.com/platform/packages/inputmethods/PinyinIME/
```

The AOSP PinyinIME source files reviewed carry Apache License 2.0 headers. This makes its implementation ideas useful as an architectural reference, but each data/binary artifact still needs its own provenance check before redistribution.

Do not assume that every mirror or bundled dictionary file inherits the same license merely because the surrounding code is Apache-licensed.

### CC-CEDICT

Official project/download information:

```text
https://cc-cedict.org/
```

CC-CEDICT is published under Creative Commons Attribution-ShareAlike 4.0. It permits commercial use subject to attribution and share-alike obligations.

The importer supports standard CC-CEDICT text records, but Orbit IME does not silently bundle CC-CEDICT. If it is imported for distribution:

- preserve attribution;
- record the source URL/version/date;
- preserve required license text;
- treat modified/derived dictionary data according to share-alike requirements.

### RIME / related ecosystems

RIME is valuable for architecture and packaging ideas, but the ecosystem contains multiple repositories/data packs with different licenses. Do not copy a RIME schema or dictionary solely because it is publicly visible on GitHub.

Check the exact repository/data-pack license first.

### English frequency data

No third-party English frequency corpus is bundled in v0.15.

For future English frequency/N-gram imports, treat source corpus licensing and generated frequency-statistics licensing as separate questions. Wikipedia/web/news/spoken corpora also represent different domains and should not be merged into one unexplained score.

## Project-authored seed data

Current project-authored import examples:

```text
data/ime_sources/seed_lexicon.tsv
data/ime_sources/seed_english.tsv
data/ime_sources/seed_ngram.tsv
```

These exist to test the importer/runtime path. They are not intended to be the final professional-scale dictionary.

## Import formats

### Orbit lexicon TSV

```text
pinyin<TAB>text<TAB>frequency
```

### CC-CEDICT

Standard lines are parsed into simplified headword + normalized tone-less Pinyin. CC-CEDICT does not provide a general usage frequency, so a separate licensed frequency source is preferable for serious ranking.

### English TSV

```text
word<TAB>frequency<TAB>optional candidate 1<TAB>optional candidate 2...
```

### N-gram TSV

```text
token<TAB>count
```

or

```text
token1<TAB>token2<TAB>count
```

or

```text
token1<TAB>token2<TAB>token3<TAB>count
```

## Generated runtime format

The importer outputs `ORBIT_ODICT` assets.

Preferred large lexicon layout:

```text
app/src/main/assets/ime/lexicon/a.odict
...
app/src/main/assets/ime/lexicon/z.odict
```

Frequency/count values are encoded in base36 to reduce text size. Runtime parsing is streaming and the lexicon reader keeps only a small number of letter shards in memory.

## What not to do

Do not:

- paste an arbitrary GitHub word list into the app;
- import data with no license file;
- assume “open source code” means bundled dictionary data has the same license;
- remove attribution from CC-BY/CC-BY-SA data;
- mix corpora/frequency scores without recording source and methodology;
- store a huge dictionary as a Kotlin `Map`;
- make the keyboard depend on network access for prediction.

## Product boundary

Orbit IME v0.15 still does not request `INTERNET` permission and does not upload user input, dictionary data, clipboard data, translation text, pet data, or local ranking state.
