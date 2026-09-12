# Orbit IME User Dictionary and Candidate Personalization

This file documents local personalized ranking in Orbit IME `0.16.0`.

## Goal

Repeated explicit user choices should move upward over time without uploading, syncing, or persisting full typed text.

## Candidate stack

The visible Chinese candidate list can combine exact local-user matches, packaged `.odict` entries, project-authored sentence/shortcut data, continuous-Pinyin segmented/beam candidates, static frequency, local 1/2/3-gram evidence, lower-confidence fuzzy correction, and explicit local user frequency. The final visible list is de-duplicated and capped at 12 candidates.

## What gets learned

Learning happens after an explicit Pinyin candidate commit such as a candidate tap or space-to-select.

Persistent fields remain only:

```text
pinyin
committed candidate text
frequency
updatedAt
```

Orbit does not store surrounding sentence text, app/package identity, target-field identity, or a full raw input history.

## v0.16 longer personal phrases

To support long-sentence input, a learned mapping may now contain:

```text
normalized Pinyin: 1..192 characters
committed CJK text: 1..96 characters
```

This increases the maximum size of one explicit learned phrase/sentence mapping; it does **not** turn the user dictionary into a persisted keystroke log.

## Ranking effect

Conceptually:

```text
score = static frequency
      + N-gram score
      + segmentation score
      + local user-frequency boost
      + source priority
      - fuzzy/typo penalty
```

A repeatedly selected candidate can therefore rise above packaged defaults.

## Runtime caches

Parsed user records are cached in process memory to avoid decoding the same SharedPreferences JSON during each candidate score. `PinyinImeEngine` also has a bounded recent-query candidate cache. Learning or clearing the user dictionary invalidates the candidate cache so stale ranking is not retained.

Neither cache is a second persistent history.

## Storage and quota

Persistent storage remains app-private SharedPreferences JSON:

```text
prefs: orbit_user_dictionary
key: entries_json
```

Quota is controlled by `ProGate`. When over quota, higher-frequency and more-recent entries are retained.

## Safety

A learned mapping must contain CJK text, must not be identical to the raw Pinyin, must fit the limits above, and must pass the existing secret/OTP/token safety filter. Privacy/password-like fields block learning.

## Packaged data is separate

Build-time AOSP/Jieba/project `.odict` data is immutable application data. User selections are never written back to packaged dictionary assets.

## Failure behavior

`UserDictionaryStore.candidatesFor()` uses the v0.16 local engine first and retains a legacy static/user fallback so malformed or missing development assets do not produce an empty keyboard.

Exact/raw-fallback commits use only exact engine/user matches; prefix/fuzzy suggestions are not forced onto the user when no exact candidate exists.

## Settings

The settings page displays learned-entry count, cumulative selection count, quota, and a local clear button.
