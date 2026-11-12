<p align="center">
  <img src="design/banner.png" alt="OpenKeys — Open Source MIDI Platform for Music Performers" width="100%">
</p>

# OpenKeys

Android app for live performance: play SoundFont voices from a USB MIDI keyboard through three voice slots (Right 1, Right 2, Left 1), with an optional per-preset keyboard split, presets and master reverb/chorus.

Use **Settings** to manage the MIDI connection and choose the input driver under **Input**, configure keyboard echo under **Output**, and import or remove SF2/SF3 SoundFonts under **Sound**. Each voice slot's instrument dropdown lists imported sounds as **SoundFont name | Instrument name**. Application and licence information is available under **About**.

In **Sound**, select a SoundFont to rename or remove it and browse or search its instruments. Select an instrument to rename it. Names are saved locally for all presets; the SoundFont file and its original names are unchanged. Instrument names can be restored to their originals.

If notes get stuck, use **Settings → Input → All notes off** to silence them.

## Build

Requires the Android SDK with NDK `30.0.16248370` and CMake `4.1.2`.

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Licence

Apache-2.0. Bundles FluidSynth (LGPL-2.1) and the Kshoji USB-MIDI-Driver (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). No SoundFonts are included.
