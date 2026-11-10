# Third-Party Notices

OpenKeys is licensed under Apache-2.0 (see `LICENSE`). It redistributes the following
unmodified, dynamically linked native libraries for `arm64-v8a` and `x86_64`, taken from
the official FluidSynth Android release `fluidsynth-v2.6.2-android24.zip`
(SHA-256 `f29d1fe9c46c8528c65d22b2bfcd15997960f908370531a5a9427a73ab9e5313`).
Binaries live in `app/src/main/jniLibs/`; headers in `app/src/main/cpp/fluidsynth/include/`.

| Library | Version | License | Source |
|---|---|---|---|
| FluidSynth (`libfluidsynth.so`) | 2.6.2 | LGPL-2.1-or-later | https://github.com/FluidSynth/fluidsynth/tree/v2.6.2 |
| libsndfile (`libsndfile.so`) | 1.2.2 | LGPL-2.1-or-later | https://github.com/libsndfile/libsndfile |
| Oboe (`liboboe.so`) | 1.10.0 | Apache-2.0 | https://github.com/google/oboe |
| FLAC (`libFLAC.so`) | 1.4.3 | BSD-3-Clause | https://github.com/xiph/flac |
| libogg (`libogg.so`) | 1.3.6 | BSD-3-Clause | https://github.com/xiph/ogg |
| libvorbis (`libvorbis.so`, `libvorbisenc.so`) | 1.3.7 | BSD-3-Clause | https://github.com/xiph/vorbis |
| Opus (`libopus.so`) | as bundled | BSD-3-Clause | https://github.com/xiph/opus |

## Java/Kotlin libraries

| Library | Version | License | Source |
|---|---|---|---|
| Kshoji USB-MIDI-Driver (`jp.kshoji:midi-driver`), Copyright K.Shoji | 0.1.12 | Apache-2.0 | https://github.com/kshoji/USB-MIDI-Driver |
| javax.sound.midi for Android (`jp.kshoji:javax-sound-midi`), Copyright K.Shoji | 0.0.6 | Apache-2.0 | https://github.com/kshoji/javax.sound.midi-for-Android |

The Kshoji driver is used unmodified as a Gradle dependency; OpenKeys only adapts its
callbacks in `app/src/main/kotlin/io/github/openkeys/midi/KshojiMidiInput.kt`.

## LGPL obligations

- Full license text: `licenses/LGPL-2.1.txt`.
- The LGPL libraries are unmodified and loaded as separate shared objects, so users can
  replace them with their own builds. Corresponding source is available at the links above.

## Artwork

- Instrument artwork (`app/src/main/res/drawable-nodpi/instrument_*.webp`) was generated with
  OpenAI image generation for this project. Under the OpenAI Terms of Use the output is owned
  by the project owner, who distributes it under the project licence (Apache-2.0).
  Provenance notes: `design/instrument_art/README.txt`.

## Open items before first release

- Opus license text is taken from upstream v1.5.2; confirm against the exact bundled Opus version.
- SoundFonts are user-imported; none are bundled.

Full license texts are in `licenses/` and are shown in the app under Settings → About & licences.
