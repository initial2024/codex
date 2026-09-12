# Orbit IME Privacy

Orbit IME is designed as a local-first input method.

## Version

This policy applies to Orbit IME `0.26.0`.

## Network

The installed Orbit IME does **not** request `INTERNET`. Input text, clipboard text, composing buffers, translation text, ranking state, personal dictionary entries, pet data, custom phrases, generated stickers, imported model packs, ASR audio, TTS text/audio and authorized voice references are not uploaded by Orbit.

Build-time dictionary/frequency/Emoji sources are pinned/hash-verified and converted to packaged offline assets. The model-pack manager itself does not download models: source buttons open an external browser, and Orbit only receives files explicitly selected by the user through Android Storage Access Framework.

## Advertising and analytics

No ad SDK, analytics, tracking, remote configuration, cloud prediction or cloud translation is included.

## Mature input/ranking

Orbit keeps selection-aware editing, composing replacement, local frequency/N-gram/context ranking, 48-candidate Chinese/English pools, adaptive long-Pinyin Beam search, three-level fuzzy correction and the local association asset. Bounded cursor context used for ranking/association is not persisted by those engines.

## Persistent settings and personal learning

Input mode, quick-phrase settings, association/fuzzy settings, translation context, appearance, skins, pet/outfit and Pro state are local preferences.

Free personal learning supports 20,000 entries and Pro 100,000. The app-private `dictionary.tsv + journal.tsv` store persists only Pinyin, committed candidate text, frequency and updatedAt. Full conversations, target-app identity and a complete raw typed stream are not stored.

## Clipboard

Recent/Pinned clipboard listening exists only while the IME is visible. There is no background clipboard service. Suspicious OTP/password/API-key/session-like text is rejected before persistence.

## Translation

Free single-sentence translation and Pro context/long-form translation remain local. Context translation reads at most a bounded previous-text window (up to four previous sentences) for the current operation; selected long-form translation is capped and preserves uncovered source rather than inventing a translation.

Third-party neural translation packs may be installed/audited, but v0.26 does not claim generic Marian/OPUS-MT neural output because that Android decoder is not bundled.

## Pro local model packs

`.orbitpack` files require manifest/LICENSE/NOTICE/checksums/model payloads. Orbit validates paths, duplicate entries, archive limits, offline-only declaration and SHA-256 coverage before app-private installation. Packs declaring `android.permission.INTERNET` are rejected.

Users see source, license, commercial/redistribution declarations, resource requirements and disclaimers before installation. Integrity validation is not legal advice; third-party license obligations remain tied to the actual model/checkpoint/voice.

## Local ASR — v0.24+

Orbit v0.24+ declares `RECORD_AUDIO` solely for explicit local voice input.

- Orbit does not start microphone capture in the background.
- The user explicitly taps the keyboard Voice action to start capture and taps again to stop/recognize.
- Capture is mono PCM16 at 16 kHz, kept in RAM only, capped at 60 seconds and not written to an ASR-history file.
- Hiding the IME cancels an active capture.
- Sensitive/password-like fields hide speech tools and cancel capture.
- Importing an ASR model pack does not itself start recording.

The enabled ASR model is user-installed in app-private storage. Recognition runs locally through the supported sherpa-onnx adapter.

## Local TTS — v0.25+

Local TTS runs only after a user action. From the keyboard, Orbit reads the current selected text first; if there is no selection, it reads only the previous sentence for the requested playback. Settings preview uses only text typed into the preview field.

Generated PCM is played locally and is not uploaded or automatically persisted as an audio history.

## Authorized voice clone — v0.26+

Voice cloning is Pro-only and user-triggered. Orbit currently executes only the supported sherpa ZipVoice adapter.

A reference voice is saved only after the user explicitly confirms that it is their own voice or a voice for which they have explicit authorization. The reference must be a 2–30 second mono PCM16 WAV with the matching reference transcript. The WAV and metadata are copied to app-private storage and can be deleted from settings.

Orbit does not record a reference voice silently. Voice cloning must not be used for impersonation, fraud, harassment, deceptive content or unauthorized commercial voice use. Generated speech may contain errors or distortions.

## Audio8 / other experimental models

Curated source entries do not mean every model is executable or commercially safe. Audio8 0.6B and 0.1B, NLLB and other candidates keep their distinct upstream licenses. If no audited Android adapter exists, Orbit marks the installed pack non-executable instead of faking model output.

## Emoji, stickers and pets

Unicode Emoji, project kaomoji and local pet stickers remain local. Sticker PNGs are generated in app-private cache. `OrbitStickerProvider` remains non-exported with temporary URI grants. Pet state and feedback codes stay local; no overlay/cloud pet service is used.

## Sensitive fields

Password-like/no-personalized-learning fields hide extra tools, detach clipboard listening, cancel active microphone capture, clear composing/translation state, disable pet growth and block personal learning.

## Permissions

Orbit v0.26 intentionally requests:

```text
android.permission.BIND_INPUT_METHOD
android.permission.RECORD_AUDIO
```

`RECORD_AUDIO` is only for explicit local ASR as described above.

Orbit intentionally does **not** request:

```text
INTERNET
Accessibility Service
SYSTEM_ALERT_WINDOW / overlay
external storage
contacts
SMS
location
camera
POST_NOTIFICATIONS
```
