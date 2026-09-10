# Orbit IME Privacy Policy Draft

Effective version: `0.1.0` MVP.

## Core statement

Orbit IME is designed as a local-first Android input method. Version `0.1.0` does not collect, upload, sell, or share user input.

## Permissions

Version `0.1.0` does not request:

- Internet access
- Accessibility service
- Contacts
- SMS
- Location
- Camera
- Microphone
- External storage
- Account access

## Keyboard input

Orbit IME sends key presses to the active text field through Android's input method APIs.

Version `0.1.0` does not store typed key streams.

## Clipboard vault

Orbit IME has a local clipboard vault. It works only when the user taps `Save` inside the keyboard toolbar.

Saved clipboard items are stored locally in Android `SharedPreferences` as JSON.

Orbit IME filters content that looks like:

- One-time verification codes
- Password labels
- API keys
- Bearer tokens
- Cookies
- Session IDs
- Long dense secret-like tokens

This filter is conservative but not perfect. Users should not save sensitive secrets into any clipboard manager.

## Password and sensitive fields

When the active field looks like a password field, number password field, web password field, or requests no personalized learning, Orbit IME enters privacy mode.

Privacy mode hides Hub functions and clipboard vault UI.

## Ads and analytics

Version `0.1.0` contains:

- No ad SDK
- No analytics SDK
- No tracking SDK
- No remote logging

## Future Pro features

The code contains a `ProGate` placeholder. It is not connected to payment, subscriptions, ads, or network logic in version `0.1.0`.

Before adding any paid, synced, or AI features, update this policy and keep privacy-sensitive keyboard behavior opt-in and explicit.
