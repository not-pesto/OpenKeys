<p align="center">
  <img src="design/banner.png" alt="OpenKeys — Open Source MIDI Platform for Music Performers" width="100%">
</p>

# OpenKeys

Android app for live performance: play SoundFont voices from a USB MIDI keyboard through three voice slots (Right 1, Right 2, Left 1), with an optional per-preset keyboard split, presets and master reverb/chorus.

## Build

Requires the Android SDK with NDK `30.0.16248370` and CMake `4.1.2`.

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Licence

Apache-2.0. Bundles FluidSynth (LGPL-2.1) and the Kshoji USB-MIDI-Driver (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). No SoundFonts are included.
