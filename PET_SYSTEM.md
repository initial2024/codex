# Orbit IME v0.7 Keyboard Pet MVP

This file documents the accepted v0.7 pet feature scope.

## Goal

Add a low-distraction keyboard-only pet layer that improves retention without weakening the input method's privacy and speed boundaries.

## Boundary

The pet system is local and keyboard-only.

It does not:

- Request overlay / floating-window permission.
- Draw outside the IME surface.
- Send notifications.
- Play sounds.
- Add ad tasks.
- Add paid gacha.
- Upload pet data.
- Sync pet data.
- Persist full typed key streams.
- Learn or grow in password / sensitive fields.
- Add `INTERNET` permission.

## Display modes

Supported display modes:

- `keyboard_only`: Pet appears as a compact chip in the Orbit Hub and can open a small in-keyboard panel.
- `hidden`: Pet is hidden and remains quiet.

System-wide floating mode is intentionally not included.

## First pet pool

Free pool:

- Orbi: orbital core.
- Kuro: data ink drop.
- Pica: prism crystal.
- Voxel: terminal cat.
- Rune: floating rune.

Pro pool placeholder:

- Noctua: clockwork owl.
- Nami: aurora jellyfish.
- Aegis: guardian fox.

## Local growth inputs

The pet grows only from local counters:

- Typing visible characters.
- Committing Pinyin candidates.
- Saving clips after explicit `Save` taps.
- Inserting Translate Preview prompts or Pro offline translation output.
- Daily check-in.

No source sentences, app names, target fields, or full input streams are stored for the pet system.

## Growth model

Stages:

```text
Stage 1: 0-49 EXP
Stage 2: 50-199 EXP
Stage 3: 200-499 EXP
Stage 4: 500+ EXP
```

Typing grants 1 EXP per 20 visible characters. Candidate commit grants 1 EXP. Clip save grants 1 EXP. Translate insertion grants 2 EXP. Daily check-in grants Stars and a small EXP bonus.

## Local storage

The pet profile is stored using app-private `SharedPreferences`:

```text
prefs: orbit_pet
```

Stored fields include pet id, EXP, Stars, streak, local typed character count, display mode, and equipped outfit id.

## Chat boundary

Simple chat is local template output only. It is unlocked when the pet reaches Adult stage.

The pet does not call a model, cloud service, or external API.

## Outfit boundary

Outfit slots are reserved for Gemini-designed assets:

- head
- face
- neck
- back
- aura

v0.7 stores a single `equippedOutfitId` placeholder and does not include complex asset rendering.
