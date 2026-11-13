<p align="center">
  <img src="design/banner.png" alt="OpenKeys — Open Source MIDI Platform for Music Performers" width="100%">
</p>

# OpenKeys

OpenKeys is an open-source Android instrument for playing SoundFont sounds from a USB MIDI keyboard. Build a live setup with three voice slots, save it as a preset, and play.

> OpenKeys is under active development.

## Features

- Three independent voices: **Right 1**, **Right 2**, and **Left 1**
- Layer Right 1 and Right 2, or enable Left 1 for an optional per-preset keyboard split
- Choose instruments from your imported SF2/SF3 SoundFonts
- Adjust each voice's volume and octave, plus master reverb and chorus
- Save and recall presets
- Connect using Android MIDI or the Kshoji USB-MIDI driver

SoundFonts are not included. Import your own in **Settings → Sound**.

## Get started

1. Install OpenKeys on an Android device and connect a compatible USB MIDI keyboard.
2. In **Settings → Input**, choose an input driver and connect your keyboard.
3. Import a SoundFont in **Settings → Sound**.
4. Choose instruments on the **Mixer**, configure your voices, and save your setup as a preset.

Enable **Left 1** to activate the split. It plays below the split key; enabled Right voices play at or above it. Set the split key in **Settings → Performance**.

## Build

Requirements: Android SDK 37, NDK `30.0.16248370`, and CMake `4.1.2`.

Run the unit tests, lint, and build a debug APK:

```shell
# macOS/Linux
./gradlew testDebugUnitTest lintDebug assembleDebug

# Windows
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. The project targets Android 10 (API 29) and later.

## Support the project

If OpenKeys is useful to you, you can [buy me a coffee](https://buymeacoffee.com/not_pesto). Thanks for supporting its development!

## License

OpenKeys is licensed under [Apache-2.0](LICENSE). Third-party software and license information are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). SoundFonts are user-provided and are not bundled with the app.
