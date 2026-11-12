# OpenKeys — GitHub Copilot Development Brief

> **Purpose:** Product requirements and implementation guidance for developing OpenKeys in a GitHub Copilot coding session. Treat this document as the initial project specification. Where a detail is explicitly marked as an assumption or open implementation choice, propose a solution and document it before making a product-level change.

## 1. Product Overview

**OpenKeys** (working name) is an open-source Android live-performance instrument app for playing a USB MIDI keyboard through an Android tablet or phone.

The app should provide a straightforward live instrument setup with multiple layered voices, an optional keyboard split, user-managed presets, and basic master effects. It is not intended to be a DAW, sequencer, recorder, arranger, or general-purpose MIDI editor.

### Core user scenario

A musician connects a USB-B MIDI keyboard to an Android device with a USB-C port using a compatible cable/adapter. They select SoundFont-based instrument patches, configure up to three voice slots, optionally enable a split point, save the setup as a preset, and switch presets while performing.

## 2. Initial Release Scope — Required

All items in this section are requirements for the initial release, not deferred roadmap ideas.

- Android app with USB MIDI input support.
- Three independent performance voice slots: **Right 1**, **Right 2**, and **Left 1**.
- Optional split activated by enabling Left 1, with a split point configured separately for each preset.
- Layering of Right 1 and Right 2 when enabled.
- Enabling Left 1 assigns it to the lower keyboard range and activates the split.
- Per-slot independent volume and transpose/octave settings.
- Automatic MIDI channel handling initially; user channel configuration is not required for the first release.
- User-imported SoundFonts; do not bundle SoundFont assets in the initial release.
- Basic master reverb and chorus controls.
- Preset save, recall, and switching.
- Real-time MIDI synthesis and performance-oriented audio output.
- CI workflow that builds and tests the app and publishes debug APK artifacts.
- Release workflow that builds a signed APK for tagged releases, with signing secrets protected.
- Documentation and licensing controls for community contributions, especially sound assets.

## 3. Functional Requirements

### 3.1 MIDI device connection

- Detect compatible USB MIDI devices through Android's MIDI APIs.
- Allow the user to select/connect a detected MIDI input device.
- Show clear connection state and useful error states (for example, device unavailable or permission denied).
- Handle disconnect/reconnect without requiring an app restart where practical.
- Prioritize USB MIDI keyboard input. BLE MIDI may be considered later unless implementation is straightforward and does not compromise the core use case.
- The keyboard is an external controller; the app does not need an on-screen piano keyboard for the initial release.

### 3.2 Voice slots and routing

The app has exactly three user-facing voice slots:

| Slot | Role | Expected routing |
|---|---|---|
| Right 1 | Primary right-hand voice | Plays notes in the upper range when Left 1 is enabled; otherwise across the keyboard |
| Right 2 | Layered right-hand voice | Plays alongside Right 1 in the upper range when Left 1 is enabled; otherwise across the keyboard |
| Left 1 | Left-hand voice | Enabling it activates the split and plays the lower range |

Each slot should support:
- Enable/disable.
- SoundFont selection and patch/program selection.
- Independent volume.
- Independent transpose/octave adjustment.
- A clear display of the currently selected instrument/patch.

**Split behavior:**
- Enabling Left 1 activates split routing; disabling Left 1 lets enabled Right slots cover the full keyboard.
- The split point is stored in that preset.
- Notes below the split point route to Left 1.
- Notes at or above the split point route to enabled Right 1 and Right 2 slots.
- Keep split-note adjustment in Settings and display only the current key value beside the Left 1 header.
- Avoid duplicate or stuck notes when changing split configuration during performance.

This routing definition is a product default and can be refined if needed, but Copilot should not silently change it.

### 3.3 SoundFont and patch management

- Initial sound source format: user-imported SF2 and/or SF3 SoundFonts, subject to engine support.
- The app should provide a system file picker/import flow.
- Imported files should remain available across app launches.
- The user can choose a SoundFont and a patch/program for each voice slot.
- Handle missing, unreadable, or unsupported SoundFonts with an understandable error.
- Avoid requiring network access to load or play local SoundFonts.
- Do not package or redistribute third-party SoundFonts unless their license explicitly permits redistribution and the project has recorded that permission.
- Keep SoundFont handling separate from preset metadata so presets can report a missing asset gracefully.

### 3.4 Presets

Presets represent a complete performance setup and should include, at minimum:

- Preset name and stable identifier.
- Split note when enabled.
- For each of Right 1, Right 2, and Left 1:
  - enabled state
  - SoundFont reference
  - patch/program selection
  - independent volume
  - transpose/octave setting
- Master reverb and chorus settings.
- Any additional settings required to reproduce the sound consistently.

Required preset operations:
- Create/save a preset.
- Rename a preset.
- Recall/select a preset.
- Duplicate a preset if practical.
- Delete a user preset with confirmation.
- Persist presets across app restarts.
- Clearly handle a preset whose SoundFont is no longer available.

Split is **not mandatory** for every preset. A preset with Left 1 disabled uses the full keyboard for enabled Right slots.

### 3.5 Effects

The initial release should include basic master-level:
- Reverb control.
- Chorus control.

Keep the controls understandable and performance-friendly. Do not require advanced effect routing, per-slot effect sends, or a large effects rack for the initial release unless the chosen synthesis engine makes these nearly free and simple.

### 3.6 Live performance and audio

- Low-latency audio is a priority.
- Avoid blocking the audio/rendering path with UI work, file operations, or preset serialization.
- Avoid clicks, abrupt cuts, stuck notes, and voice leaks during preset changes or MIDI disconnects where practical.
- When changing presets, use a safe transition strategy. Do not cut sustained notes abruptly if a reasonable implementation can preserve or release them cleanly.
- Handle MIDI Note On, Note Off, sustain pedal (CC64), and common performance controls where supported by the engine.
- Keep audio and MIDI processing robust under repeated notes, layered voices, and rapid preset changes.
- Include a way to silence all voices / recover from stuck notes.

## 4. Recommended Technical Direction

These are recommended starting points, not permission to add unnecessary complexity. Validate current library versions and Android compatibility during implementation.

### Android app

- Kotlin.
- Jetpack Compose for UI.
- Android MIDI APIs (`MidiManager` and associated MIDI device/input APIs) for USB MIDI connectivity.
- A foreground or appropriately managed service/component for continued live audio operation, where justified by Android lifecycle behavior.

### Synthesis and audio

- **FluidSynth** as the initial SoundFont synthesis engine, supporting SF2/SF3 where compatible.
- **Oboe** or an appropriate low-latency Android audio output layer.
- Keep the synthesizer abstraction sufficiently isolated that the engine could be replaced later without rewriting preset/UI logic.
- Confirm native build, ABI, packaging, and license requirements for the chosen engine before integrating it.
- Keep native audio operations on appropriate threads and avoid UI-thread calls that can disrupt performance.

### Architecture suggestion

Prefer clear separations, such as:

- `app` — application shell and navigation.
- `midi` — device discovery, connection, and MIDI event input.
- `audio` / `synth` — engine integration, voice allocation, audio output, effects.
- `presets` — preset model, storage, serialization, validation.
- `soundfonts` — import, local asset references, metadata, and patch discovery.
- `ui` — Compose screens and controls.

A single-module implementation is acceptable for an early scaffold if it keeps boundaries clear; do not introduce module complexity solely for its own sake.

## 5. UI / UX Direction

The interface should be simple, legible on a tablet, and suitable for quick adjustments while playing.

Core views or sections:
1. **Performance screen**
   - MIDI connection state.
   - Right 1, Right 2, Left 1 controls.
   - Instrument/patch selection.
   - Slot enable state and independent volume/transpose.
   - Left 1 enable control activates the optional split; the split key is adjusted in Settings and only its current value appears beside the Left 1 header.
   - Master reverb and chorus.
   - Preset selection and save action.
2. **SoundFont management**
   - Import via Android file picker.
   - List imported SoundFonts.
   - Remove imported entries safely, with checks for presets referencing them.
3. **Preset management**
   - Create, rename, duplicate (if included), recall, and delete presets.

Do not add recording, timeline, multitrack, arranger, notation, or DAW-style features to the initial release.

## 6. Preset Data and Persistence

- Use a versioned, maintainable preset schema.
- Store references to imported SoundFonts rather than copying large files into every preset.
- Ensure imported file references remain valid across process restarts and device reboots, using Android's supported persistent URI permission or app-managed storage approach as appropriate.
- Include schema versioning and migration strategy from the beginning.
- Validate preset data before loading it into the audio engine.
- Keep serialization/storage work off latency-sensitive threads.

Example conceptual model (not a mandated code schema):

```kotlin
data class PerformancePreset(
    val id: String,
    val name: String,
    val splitNote: Int?,
    val right1: VoiceSlot,
    val right2: VoiceSlot,
    val left1: VoiceSlot,
    val masterReverb: EffectSetting,
    val masterChorus: EffectSetting,
    val schemaVersion: Int
)

data class VoiceSlot(
    val enabled: Boolean,
    val soundFontId: String?,
    val bank: Int,
    val program: Int,
    val volume: Float,
    val transposeSemitones: Int
)
```

The exact model should follow the selected synthesis engine's bank/program conventions and a clearly documented MIDI note-number convention.

## 7. Community Project, Licensing, and Asset Policy

The project should be welcoming to community contributions while ensuring all included assets can legally be redistributed.

### Requirements

- Select and document an open-source license for the app source code. **Apache-2.0 is a suggested default**, subject to maintainer approval.
- Preserve and comply with all third-party library licenses. FluidSynth is LGPL-licensed; verify the precise version, notices, linking/distribution requirements, and source-offer obligations as applicable.
- Keep third-party license notices in the repository and in the distributed app where required.
- SoundFonts are user-imported in the initial release. Do not include third-party sound banks by default.
- Do not accept SoundFonts, samples, presets, artwork, or other contributions without clear evidence of the rights and license needed for the intended use.
- Require contributors to identify the origin and license of submitted assets.
- Include a documented asset review process and a `THIRD_PARTY_NOTICES` or equivalent inventory.
- Do not assume that an asset being freely downloadable means it is open-source or redistributable.
- Choose and document a license for original project presets and documentation. Suggested options: CC0 or CC-BY for presets, and CC-BY for documentation; confirm with maintainers.

Suggested repository documents:
- `LICENSE`
- `THIRD_PARTY_NOTICES.md`
- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`
- `SECURITY.md` (if appropriate)
- `README.md`
- SoundFont / asset contribution policy

## 8. GitHub Repository and CI/CD

### Repository expectations

Include:
- Android Gradle project and Gradle wrapper.
- Clear build and run instructions.
- README covering purpose, features, supported MIDI setup, SoundFont import, development setup, and licensing.
- Issue templates and pull-request template where useful.
- CI workflow.
- Release workflow.
- License and third-party notices.

### Continuous integration — debug APK

On pushes to the main development branch, pull requests targeting it, and manual workflow dispatch:

1. Check out source.
2. Set up a supported JDK (Java 17 is the suggested baseline; confirm Gradle/Android Gradle Plugin compatibility).
3. Set up Gradle.
4. Run unit tests and relevant checks.
5. Build the debug APK.
6. Upload the debug APK as a GitHub Actions artifact, with a defined retention period.

A starter workflow shape:

```yaml
name: Android CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    name: Build and Test Android
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - uses: gradle/actions/setup-gradle@v4

      - name: Build and test
        run: |
          chmod +x ./gradlew
          ./gradlew test assembleDebug

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: OpenKeys-debug-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
          retention-days: 14
```

Copilot should adapt this to the actual project structure, build variants, native dependencies, and test tasks. Pin or update third-party GitHub Actions according to the repository's security policy.

### Tagged release — signed APK

- Trigger release packaging from a version tag (for example, `v1.0.0`).
- Build the release APK.
- Sign it using a release keystore and credentials stored in GitHub Actions secrets or a similarly secure mechanism.
- Never commit the keystore, passwords, signing keys, or credentials.
- Verify the signature and build output.
- Attach the signed APK and relevant release notes to a GitHub Release.
- Keep release signing separate from ordinary pull-request and debug builds.
- Document how maintainers provision and rotate signing credentials.

## 9. Quality and Acceptance Criteria

The initial release is ready for community testing when:

- The app builds from a clean checkout using documented instructions.
- A compatible USB MIDI keyboard can be connected and its notes trigger sound.
- Right 1 can play alone across the keyboard.
- Right 1 and Right 2 can layer across the keyboard.
- Enabling Left 1 activates the per-preset split; disabling it lets enabled Right slots play across the full keyboard.
- With Left 1 enabled, it plays below the split note and enabled Right slots play at/above it.
- Each slot has independent volume and transpose/octave controls.
- Presets persist, can be recalled, and reproduce their voice, split, and effect settings.
- Users can import a supported SoundFont and select patches from it.
- Master reverb and chorus are adjustable.
- Disconnect/reconnect and all-notes-off handling do not leave persistent stuck notes under normal test conditions.
- CI builds/tests and provides a downloadable debug APK artifact.
- A tagged release can produce a signed APK without exposing signing credentials.
- License and third-party notices are present, and no asset with unclear redistribution rights is included.

## 10. Suggested Development Milestones

Copilot should implement incrementally and keep each milestone buildable.

1. **Repository scaffold**
   - Kotlin/Compose project, Gradle setup, README, license placeholders, CI baseline.
2. **MIDI connectivity**
   - Device discovery, permission/connection lifecycle, incoming MIDI event logging, basic tests.
3. **Audio engine integration**
   - Synthesis output, one imported SoundFont, basic patch playback, note-off and all-notes-off handling.
4. **Voice slots**
   - Three slots, layering, volume, transpose/octave, initial routing tests.
5. **Optional split**
   - Per-preset split state and split-note routing; tests for boundary behavior.
6. **SoundFont and preset management**
   - File import, patch selection, persistence, preset CRUD and schema versioning.
7. **Effects and performance stability**
   - Master reverb/chorus, latency checks, preset transition behavior, disconnect recovery.
8. **Release automation**
   - Debug artifact CI, secure tagged-release signing, release attachment.
9. **Community readiness**
   - Contribution instructions, asset policy, notices, documentation, issue templates.

## 11. Implementation Guidance for GitHub Copilot

- Start by inspecting the existing repository. Do not overwrite an existing project or change its chosen architecture without explaining why.
- Implement the specification in small, testable steps.
- Before adding native synthesis code, confirm the current build setup, Android ABI targets, library availability, and license requirements.
- Use current stable compatible dependencies rather than blindly copying version numbers from this brief.
- Add unit tests for preset serialization, split routing, and MIDI event routing. Add instrumented tests where Android device APIs require them.
- Keep UI state, preset domain logic, MIDI routing, and synthesis engine responsibilities separate.
- Do not fake MIDI or audio functionality in production code. If a mock is needed for UI development, isolate it and label it clearly.
- Do not include unlicensed sample assets or SoundFonts.
- Document significant decisions and any deviations from this brief.
- If a requirement is ambiguous, ask a focused question or record a conservative assumption before implementing behavior that changes the user experience.
- Keep the first release focused on live performance and the defined three-slot design.

## 12. Explicitly Out of Scope for Initial Release

- DAW, sequencer, arranger, or timeline.
- Audio recording or multitrack recording.
- MIDI recording/editing/export.
- On-screen piano as a required feature.
- Built-in bundled SoundFont library.
- Cloud account, cloud preset sync, or mandatory online access.
- Advanced per-slot effects chains or complex effect routing.
- User-configurable MIDI channels as a required feature.
- Broad MIDI controller mapping/editor, unless a simple mapping is necessary for the defined performance controls.

---

**Specification status:** Initial release requirements agreed in brainstorming. Open implementation details (engine integration specifics, UI layout, exact effect parameter ranges, supported Android versions, and exact preset serialization format) should be decided during development and documented in the repository.
