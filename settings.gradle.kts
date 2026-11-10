pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kshoji USB-MIDI-Driver is published only in its GitHub-hosted Maven repos.
        maven("https://github.com/kshoji/USB-MIDI-Driver/raw/develop/MIDIDriver/snapshots") {
            content { includeModule("jp.kshoji", "midi-driver") }
        }
        maven("https://github.com/kshoji/javax.sound.midi-for-Android/raw/master/javax.sound.midi/repository") {
            content { includeModule("jp.kshoji", "javax-sound-midi") }
        }
    }
}

rootProject.name = "OpenKeys"
include(":app")
