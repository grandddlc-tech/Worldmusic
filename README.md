# World Music 🎧

A native Android offline music library with a country-oriented artist view.

## Features
- Scans music stored on the device using Android MediaStore.
- Plays local audio files.
- Looks up artist country metadata through MusicBrainz when online; unresolved results remain **Unknown** instead of guessing from song language.
- Lets users manually correct an artist's country in the current app session.
- Dark Persian-first interface.

## Build
Run `gradle assembleDebug` with JDK 17 and Android SDK installed. GitHub Actions builds the debug APK on every push and uploads it as an artifact.

## Note
Country attribution is based on MusicBrainz artist metadata where available. Artist-country metadata can be ambiguous or incomplete; verify and correct manually when needed. The current first version keeps manual corrections in app memory only.
