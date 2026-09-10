# Orbit IME v0.5 User Dictionary

This file documents the accepted v0.5 feature scope.

## Goal

Add a local user dictionary that improves Pinyin candidate ordering based on explicit candidate selection.

## Boundary

The user dictionary is local learning only.

It does not:

- Upload dictionary entries.
- Sync dictionary entries.
- Persist full typed key streams.
- Learn from password fields.
- Learn from OTP-only text.
- Learn from secret-like text.
- Read background clipboard content.
- Add `INTERNET` permission.
- Add cloud translation.
- Add any external API.

## What gets learned

Orbit IME learns only after the user explicitly commits a Pinyin candidate by:

- Tapping a candidate.
- Pressing space while a Pinyin buffer exists.

The stored record is limited to:

```text
pinyin -> committed candidate text -> frequency -> updatedAt
```

Example:

```text
nihao -> 你好 -> 3 -> 1789020000000
```

It does not store the surrounding sentence, app name, target field, or full input history.

## Candidate ranking

When a Pinyin buffer exists, candidate ranking is:

1. Exact user dictionary matches, ordered by frequency and recency.
2. Built-in static dictionary candidates.
3. Prefix user dictionary matches, ordered by frequency and recency.

The displayed candidate list is de-duplicated and capped at 8 candidates.

## Storage

The dictionary is stored using app-private `SharedPreferences` JSON:

```text
prefs: orbit_user_dictionary
key: entries_json
```

Free quota:

```text
300 entries
```

Pro placeholder quota:

```text
5000 entries
```

When the quota is exceeded, the dictionary keeps the highest-frequency and most-recent entries.

## Safety filters

A candidate is learned only when:

- Pinyin length is 1 to 32 characters.
- Candidate text length is 1 to 20 characters.
- Candidate text contains CJK characters.
- Candidate text is not equal to the raw Pinyin string.
- Candidate text does not look like an OTP, password, token, API key, authorization header, cookie, session value, or long dense secret.

## Settings controls

The settings page shows:

- Local dictionary entry count.
- Total learned selection count.
- Current quota.
- A button to clear the local user dictionary.

## Static dictionary personalization policy

The built-in static dictionary must stay generic. Product-specific or developer-personal terms must not be hardcoded into `PinyinDictionary.kt`. User-specific terms should enter ranking only through the local user dictionary on that user's device.
