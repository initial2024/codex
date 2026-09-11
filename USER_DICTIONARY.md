# Orbit IME User Dictionary and Candidate Personalization

This file documents local personalized ranking in Orbit IME `0.15.0`.

## Goal

Repeated user choices should move upward over time without uploading, syncing, or persisting full typed text.

## v0.15 candidate stack

The visible Chinese candidate list can now combine:

1. Exact local user-dictionary matches.
2. Exact packaged `.odict` lexicon matches.
3. Project-authored sentence/shortcut matches.
4. Continuous-Pinyin segmented and beam-generated candidates.
5. Static word/phrase frequency.
6. Local 1/2/3-gram scores.
7. Fuzzy/typo candidates with an explicit penalty.
8. Local user-selection frequency.

The final list is de-duplicated and capped at 12 candidates.

## What gets learned

Orbit IME learns only after the user explicitly commits a Pinyin candidate by:

- tapping a candidate;
- pressing space while a Pinyin buffer exists.

The persistent record is limited to:

```text
pinyin
committed candidate text
frequency
updatedAt
```

It does not store:

- surrounding sentence text;
- app/package name;
- target field identity;
- full input history;
- clipboard contents unless the user separately saves them in Clips.

## Ranking effect

`CandidateRanker` treats local user frequency as a strong ranking feature.

Conceptually:

```text
score =
  static-frequency score
  + N-gram score
  + segmentation score
  + local user-frequency boost
  + source priority
  - fuzzy/typo penalty
```

This means a repeatedly selected candidate can rise above the default packaged order without rewriting the packaged dictionary.

## In-memory cache

v0.15 keeps parsed user-dictionary entries in a process-local memory cache.

Purpose:

- avoid parsing the same `SharedPreferences` JSON for every candidate score;
- reduce candidate latency;
- keep ranking deterministic.

The cache contains only the same four fields already stored persistently. It is replaced after learning, cleared when the user clears the dictionary, and disappears with the app process.

## Storage

Persistent storage remains app-private `SharedPreferences` JSON:

```text
prefs: orbit_user_dictionary
key: entries_json
```

Current quotas remain controlled by `ProGate`.

When quota is exceeded, the store keeps the highest-frequency and most-recent entries.

## Safety filters

A learned mapping must satisfy all current safety checks:

- normalized Pinyin length: 1 to 64 characters;
- candidate text length: 1 to 40 characters;
- candidate contains CJK characters;
- candidate is not identical to raw Pinyin;
- candidate does not look like an OTP, password, token, API key, authorization header, cookie/session value, or long dense secret.

Password-like fields enter privacy mode and do not learn.

## Large packaged dictionaries are separate

User learning is not the same thing as the large packaged lexicon.

The packaged lexicon is generated at build time with:

```text
tools/ime_importer.py
```

and stored as compact `.odict` assets. User selections are never written back into those files.

## Failure behavior

`UserDictionaryStore.candidatesFor()` routes through the v0.15 local IME engine first.

If the new engine returns no candidates or throws because an asset is malformed, the code keeps a legacy static/user-dictionary fallback path so the keyboard does not become unusable.

## Settings controls

The settings page continues to show:

- local learned-entry count;
- cumulative learned selection count;
- current quota;
- a button to clear the local user dictionary.
