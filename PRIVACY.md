# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.21.0`.

## Network

Orbit IME `0.21.0` does not request `INTERNET` permission. The installed app does not upload input text, clipboard text, composing buffers, translation source/result text, candidate-ranking state, personal dictionary entries, pet data, expression history, skins, saved clips, custom phrases or generated sticker images.

Public dictionary/Emoji files are downloaded only by the build machine, hash-verified, converted to packaged offline assets, and read locally by the installed IME.

## Advertising and analytics

Orbit contains no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Selection and composing

Android cursor selection and composing state are independent. Orbit v0.21 explicitly checks editor selection before Backspace. A non-empty selection is deleted by committing an empty replacement; only when no selection exists does Orbit delete the previous Unicode code point. Normal text, space, candidates, paste, Emoji, quick phrases and translated text use Android commit/composing APIs that replace the selected region.

No selected text is persisted merely because it was selected or replaced.

## Input engine and next-phrase association

Local candidate generation uses the existing v0.20 mature assets and ranking pipeline. Next-word/phrase association reads only a bounded text tail before the cursor and is optional. The user can turn association off; that setting is stored locally.

## Persistent user preferences and custom phrases

v0.21 remembers local user choices such as the last Chinese/English input mode, quick-phrase visibility, built-in phrase visibility, next-association toggle, context-translation toggle, skin, pet/outfit state and Pro activation state.

Custom Chinese/English quick phrases are stored locally in `SharedPreferences` as user-authored text. They can be disabled or cleared by the user. They are never uploaded or used as hidden training data.

## Personal learning library

Local learning supports 20,000 entries for Free and 100,000 for Pro. The store uses app-private `dictionary.tsv + journal.tsv` files and periodic compaction. Persistent learning records remain limited to Pinyin, committed candidate text, frequency and updatedAt. Orbit does not persist full conversations, app/package identity, target-field identity or a complete typed stream.

## Clipboard

Orbit provides local Recent and Pinned sections. The text clipboard listener exists only while the IME window is visibly shown. There is no background clipboard-harvesting service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

Sticker image clipboard fallback is separate from text history and only occurs after explicit user action.

## Emoji / kaomoji / stickers

Orbit keeps Unicode Emoji 17.0, project-authored kaomoji and 128 local pet sticker definitions. Sticker PNGs are generated into app-private cache. `OrbitStickerProvider` remains `exported=false` with `grantUriPermissions=true`. No external-storage permission or runtime sticker download is used.

## Pet and outfit state

The pet module stores only local pet/progression/outfit state and short feedback event codes. v0.21 uses a polished local renderer with a lightweight idle float/breath animation. Stable pet silhouettes remain; the old outfit layer is suppressed in the v0.21 view and replaced with a coherent local accessory overlay. No overlay permission, screenshot capture, camera input, cloud pet service or AI pet chat is used.

## Translation

Free translation remains one sentence and fully local.

Pro can optionally enable:

- previous-context translation using up to the previous two sentences in memory;
- selected-text long-form translation, up to 8,000 characters, split sentence-by-sentence locally.

Long-form translation is activated only after the user explicitly selects/all-selects text and taps the Pro long-form action. The selected block is not saved by the translation module. Segments that the offline dictionaries cannot translate are preserved as source text rather than being falsely reported as translated.

All translation modes remain local and use project tables + CC-CEDICT + local composition. Orbit has no cloud translation endpoint or external translation API.

## Pro activation

v0.21 debug builds include a local tester-code path to exercise Pro features. That tester code is rejected by release builds.

For production, the intended design is a signed activation/license token: the APK contains only a public verification key, while the private signing key stays outside the repository and APK. Orbit intentionally does not use a plain reusable invite code as the production security boundary.

No payment, account login or license server is implemented in v0.21.

## Sensitive fields

For password-like/no-personalized-learning fields, Orbit hides extra tools, detaches clipboard listening, clears composing/translation state, disables pet growth and blocks personal-dictionary learning.

## Permissions intentionally not requested

Orbit does not request Internet, Accessibility, overlay/floating window, contacts, SMS, location, camera, microphone, external storage, or notifications.

## Imported data assets

Normal builds keep the audited AOSP PinyinIME, Jieba, CC-CEDICT, ESDB/SCOWL and Unicode Emoji sources and packaged notices. v0.21 does not add a new scraped third-party corpus.
