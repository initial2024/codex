# Orbit IME data source strategy

Orbit IME needs better Pinyin candidates, English candidates, and local phrase translation, but the app must remain privacy-first and offline.

## v0.14 status

Version `0.14.0` expands the maintainable local data layer:

- `PinyinDictionary.kt` keeps the core syllable dictionary.
- `PinyinBoostData.kt` keeps earlier project-authored Pinyin phrase data.
- `PinyinExpandedData.kt` adds more project-authored common words, shorthand, input-method feedback phrases, study phrases, development phrases, and sentence candidates.
- `PinyinSentenceDictionary.kt` merges sentence shortcuts, expanded data, and fuzzy correction candidates.
- `PinyinCorrectionEngine.kt` adds small local typo/fuzzy matching, including direct handling for cases such as `xhfnivh`.
- `EnglishDictionary.kt` adds English word, phrase, shorthand, and typo-correction candidates.
- `TranslationBoostData.kt` keeps earlier local exact phrase translation and conservative token translation.
- `ProfessionalTranslationData.kt` keeps the larger project-authored local phrase translation table.
- `TranslationExpansionData.kt` adds more local phrase translations for feedback, input-method, development, and study scenarios.
- `OfflineTranslationPack.kt` checks professional/expanded/local translation data before falling back to prompt generation.

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

Version `0.14.0` does not directly import a full public dictionary. The bundled boost data remains project-authored to avoid licensing ambiguity.

## Future professional path

A professional-quality offline IME requires:

1. A large phrase dictionary.
2. Word frequency data.
3. Pinyin segmentation for continuous input.
4. Ranking using static frequency + local user frequency.
5. Compact asset storage instead of huge hardcoded Kotlin maps.
6. Build-time importer scripts that generate app-private assets.
7. Runtime lookup from indexed assets or SQLite-like compact tables.

Version `0.14.0` improves local coverage, but the next professional step should be a licensed-data importer instead of manually growing Kotlin maps forever.

## Product boundary

Orbit IME still does not request `INTERNET` permission and still does not upload user input, dictionary data, clipboard data, translation text, or pet data.
