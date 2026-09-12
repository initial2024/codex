# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.22.0`.

## Network

Orbit IME `0.22.0` does not request `INTERNET` permission. The installed app does not upload input text, clipboard text, composing buffers, translation source/result text, candidate-ranking state, personal dictionary entries, pet data, expression history, skins, saved clips, custom phrases, generated sticker images, imported model files or model-pack metadata.

Public dictionary/Emoji files are downloaded only by the build machine, hash-verified, converted to packaged offline assets, and read locally by the installed IME.

The v0.22 model-pack manager also does not download models itself. Source buttons may open the user's external browser. Orbit receives a model pack only after the user explicitly chooses a local file through Android's system file picker.

## Advertising and analytics

Orbit contains no ad SDK, analytics SDK, tracking SDK, or remote-configuration SDK.

## Selection and composing

Android cursor selection and composing state are independent. Orbit explicitly checks editor selection before Backspace. A non-empty selection is deleted by committing an empty replacement; only when no selection exists does Orbit delete the previous Unicode code point. Normal text, space, candidates, paste, Emoji, quick phrases and translated text use Android commit/composing APIs that replace the selected region.

No selected text is persisted merely because it was selected or replaced.

## Input engine and next-phrase association

Local candidate generation uses the mature local assets and ranking pipeline. Next-word/phrase association reads only a bounded text tail before the cursor and is optional. The user can turn association off; that setting is stored locally.

## Persistent user preferences and custom phrases

Orbit remembers local user choices such as the last Chinese/English input mode, quick-phrase visibility, built-in phrase visibility, next-association toggle, context-translation toggle, skin, pet/outfit state and Pro activation state.

Custom Chinese/English quick phrases are stored locally in `SharedPreferences` as user-authored text. They can be disabled or cleared by the user. They are never uploaded or used as hidden training data.

## Personal learning library

Local learning supports 20,000 entries for Free and 100,000 for Pro. The store uses app-private `dictionary.tsv + journal.tsv` files and periodic compaction. Persistent learning records remain limited to Pinyin, committed candidate text, frequency and updatedAt. Orbit does not persist full conversations, app/package identity, target-field identity or a complete typed stream.

## Clipboard

Orbit provides local Recent and Pinned sections. The text clipboard listener exists only while the IME window is visibly shown. There is no background clipboard-harvesting service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

Sticker image clipboard fallback is separate from text history and only occurs after explicit user action.

## Emoji / kaomoji / stickers

Orbit keeps Unicode Emoji 17.0, project-authored kaomoji and 128 local pet sticker definitions. Sticker PNGs are generated into app-private cache. `OrbitStickerProvider` remains `exported=false` with `grantUriPermissions=true`. No external-storage permission or runtime sticker download is used.

## Pet and outfit state

The pet module stores only local pet/progression/outfit state and short feedback event codes. The polished local renderer keeps lightweight idle float/breath animation and a coherent accessory overlay. No overlay permission, screenshot capture, camera input, cloud pet service or AI pet chat is used.

## Translation

Free translation remains one sentence and fully local.

Pro can optionally enable:

- previous-context translation using up to the previous two sentences in memory;
- selected-text long-form translation, up to 8,000 characters, split sentence-by-sentence locally.

Segments that the offline dictionaries cannot translate are preserved as source text rather than being falsely reported as translated.

v0.22 introduces model-pack infrastructure for future optional neural translation, but **does not execute a neural translation model yet**. Imported model files are inert data until a later runtime explicitly supports them.

## Local model packs

Model-pack import/management is Pro-only and uses Android Storage Access Framework. Orbit does not request broad external-storage permission.

A `.orbitpack` must contain:

```text
manifest.json
LICENSE.txt
NOTICE.txt
checksums.sha256
model/...
```

Before installation Orbit validates package paths, rejects path traversal/absolute paths, limits file count and packed/unpacked size, parses the manifest, requires an offline-only privacy declaration, rejects packs that require `INTERNET`, and verifies SHA-256 for every regular file. The package is verified again while extracting to app-private storage.

Installed packs live under:

```text
files/orbit-model-packs/<pack_id>/
```

The user must explicitly accept a disclaimer showing source, license, commercial-use/redistribution claims, languages, size/RAM requirements, declared future permissions and warnings before installation.

Orbit's integrity checks do not provide legal advice and cannot guarantee that a third-party pack author described an upstream license correctly. Users remain responsible for complying with the actual third-party model/license terms.

Non-commercial/research-only packs may be imported for permitted personal/research use but are clearly marked and must not be treated as commercial-safe defaults. In particular, the Meta NLLB-200 distilled 600M model is documented as CC-BY-NC-4.0 and is not Orbit's intended commercial default.

v0.22 defines future translation/ASR/TTS/voice-clone provider interfaces but marks them non-executable. Importing a speech model does not start recording and does not request microphone access.

## Voice/voice-clone safety boundary

Future speech packs can carry model/runtime metadata, but v0.22 does not execute them. Voice cloning, when implemented later, must require a separate explicit authorization/consent flow. It must not be presented as permission to imitate another person's voice without authorization, and must not be used for impersonation, fraud, harassment or rights infringement.

## Pro activation

Debug builds include a local tester-code path to exercise Pro features. That tester code is rejected by release builds.

For production, the intended design is a signed activation/license token: the APK contains only a public verification key, while the private signing key stays outside the repository and APK. Orbit intentionally does not use a plain reusable invite code as the production security boundary.

No payment, account login or license server is implemented in v0.22.

## Sensitive fields

For password-like/no-personalized-learning fields, Orbit hides extra tools, detaches clipboard listening, clears composing/translation state, disables pet growth and blocks personal-dictionary learning.

## Permissions intentionally not requested

Orbit v0.22 does not request Internet, microphone, Accessibility, overlay/floating window, contacts, SMS, location, camera, external storage, or notifications.

Future ASR will require `RECORD_AUDIO` only when the user explicitly enables and invokes voice input. Merely importing an ASR pack must never add microphone permission or start recording.

## Imported data assets

Normal builds keep the audited AOSP PinyinIME, Jieba, CC-CEDICT, ESDB/SCOWL and Unicode Emoji sources and packaged notices. Optional neural model packs are user-supplied app-private data and retain their own independent LICENSE/NOTICE files.
