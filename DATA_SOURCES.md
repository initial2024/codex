# Orbit IME data source strategy

Orbit IME needs better Pinyin candidates, English candidates, and local phrase translation, but the app must remain privacy-first and offline.

## v0.12 status

Version `0.12.0` adds a maintainable data layer:

- `PinyinDictionary.kt` keeps the core syllable dictionary.
- `PinyinSentenceDictionary.kt` adds common shorthand and sentence-level Pinyin candidates.
- `EnglishDictionary.kt` adds English word, phrase, and shorthand candidates.
- `TranslationBoostData.kt` keeps earlier local exact phrase translation and conservative token translation.
- `ProfessionalTranslationData.kt` adds a larger project-authored local phrase translation table.
- `OfflineTranslationPack.kt` checks professional/local translation data before falling back to prompt generation.

## Public data sources reviewed

These sources are useful references for future import pipelines:

- CC-CEDICT: Chinese-English dictionary with simplified/traditional headwords, pinyin, and English glosses. License: Creative Commons Attribution-ShareAlike.
- Android Open Source Project Pinyin IME: historical Android Pinyin IME implementation and ideas under Android/AOSP licensing.
- RIME / Trime ecosystem: mature open-source IME architecture and dictionary packaging ideas. Some RIME-related port metadata reports GPLv3 licensing, so direct copying is not assumed safe for a future commercial app.
- phrase-pinyin-data / pinyin-data style datasets: useful for phrase-to-pinyin expansion only when license and attribution requirements are clear.

## License rule

Do not copy arbitrary GitHub dictionary data into this repository unless the license is explicit and compatible with redistribution.

For CC-CEDICT-like sources, attribution and share-alike requirements must be preserved if bundled data is imported.

For MIT/Apache-licensed data, include attribution in this file and keep the original license notice when required.

## Future professional path

A professional-quality offline IME requires:

1. A large phrase dictionary.
2. Word frequency data.
3. Pinyin segmentation for continuous input.
4. Ranking using static frequency + local user frequency.
5. Compact asset storage instead of huge hardcoded Kotlin maps.
6. Build-time importer scripts that generate app-private assets.
7. Runtime lookup from indexed assets or SQLite-like compact tables.

Version `0.12.0` does not yet import a full public dictionary. It prepares the codebase so future bulk data can be added without rewriting the keyboard service.

## Product boundary

Orbit IME still does not request `INTERNET` permission and still does not upload user input, dictionary data, clipboard data, translation text, or pet data.
