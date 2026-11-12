<p align="center">
  <img src="design/banner.png" alt="OpenKeys — Open Source MIDI Platform for Music Performers" width="100%">
</p>

# OpenKeys

Android app for live performance: play SoundFont voices from a USB MIDI keyboard through three voice slots (Right 1, Right 2, Left 1), with an optional per-preset keyboard split, presets and master reverb/chorus.

Use **Settings** to manage the MIDI connection and choose the input driver under **Input**, configure keyboard echo under **Output**, and import or remove SF2/SF3 SoundFonts under **Sound**. Each voice slot's instrument dropdown lists imported sounds as **SoundFont name | Instrument name**. Application and licence information is available under **About**.

In **Sound**, select a SoundFont to rename or remove it and browse or search its instruments. Select an instrument to rename it. Names are saved locally for all presets; the SoundFont file and its original names are unchanged. Instrument names can be restored to their originals.

If notes get stuck, use **Settings → Input → All notes off** to silence them.

## OpenKeys – UI Navigation and Feature Roadmap

The five tabs remain available while performing:

- **Mixer:** shows MIDI connection status, a device-wide transpose indicator, saved preset shortcuts, and the Left 1, Right 1 and Right 2 instrument cards. Tap a card's header to toggle its voice, or its central instrument area to open the instrument picker. Volume and octave controls are separate from the instrument selection area.
- **Effects:** adjusts the current preset's master reverb and chorus. Delay, EQ, and per-instrument effects routing are planned, not yet implemented.
- **List Presets:** lists numbered saved presets; select one to recall it or delete one with confirmation. Numbers reflect the current list order, not permanent IDs.
- **Save Preset:** name the current setup, save or update it, or save it as a new preset. Presets retain slot settings, split, reverb and chorus.
- **Settings:** configure the performance setup, MIDI input/output and SoundFonts, and view licence information.

In **Settings → Performance**, adjust the global transpose in semitones, enable the per-preset keyboard split, and choose its split point. Per-slot volume and octave controls are on the Mixer. With split enabled, Left 1 plays below the split point and the enabled Right slots play at or above it; without split, the Right slots cover the keyboard and Left 1 is inactive. Global transpose persists on the device independently of presets and is applied after zone selection, alongside each slot's octave setting.

In **Settings → Input**, choose Android MIDI or the Kshoji USB driver, connect a device and use All notes off. Input currently accepts all MIDI channels; slot output channels are fixed (Right 1: 1, Right 2: 2, Left 1: 3). **Output** controls MIDI echo. **Sound** manages user-imported SoundFonts and instrument names. FluidSynth/Oboe audio configuration is automatic.

Future opportunities include configurable MIDI channels and audio settings, delay, EQ, and instrument-specific effects routing. These are not available yet.

## Build

Requires the Android SDK with NDK `30.0.16248370` and CMake `4.1.2`.

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Licence

Apache-2.0. Bundles FluidSynth (LGPL-2.1) and the Kshoji USB-MIDI-Driver (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). No SoundFonts are included.
