#include <jni.h>
#include <android/log.h>
#include <fluidsynth.h>

namespace {

constexpr const char *TAG = "OpenKeysSynth";

struct Engine {
    fluid_settings_t *settings;
    fluid_synth_t *synth;
    fluid_audio_driver_t *driver;
};

fluid_synth_t *synthOf(jlong handle) {
    return reinterpret_cast<Engine *>(handle)->synth;
}

void setStr(fluid_settings_t *s, const char *name, const char *value) {
    if (fluid_settings_setstr(s, name, value) != FLUID_OK) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Unsupported setting %s=%s", name, value);
    }
}

void setInt(fluid_settings_t *s, const char *name, int value) {
    if (fluid_settings_setint(s, name, value) != FLUID_OK) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Unsupported setting %s=%d", name, value);
    }
}

}  // namespace

#define JNI_FN(name) Java_io_github_openkeys_audio_FluidSynthEngine_##name

extern "C" {

JNIEXPORT jlong JNICALL JNI_FN(nativeCreate)(JNIEnv *, jclass, jint sampleRate) {
    fluid_settings_t *settings = new_fluid_settings();
    if (!settings) return 0;
    setStr(settings, "audio.driver", "oboe");
    setStr(settings, "audio.oboe.performance-mode", "LowLatency");
    setStr(settings, "audio.oboe.sharing-mode", "Exclusive");
    setInt(settings, "audio.period-size", 64);
    if (sampleRate > 0) fluid_settings_setnum(settings, "synth.sample-rate", sampleRate);
    setInt(settings, "synth.polyphony", 256);

    fluid_synth_t *synth = new_fluid_synth(settings);
    if (!synth) {
        delete_fluid_settings(settings);
        return 0;
    }
    fluid_audio_driver_t *driver = new_fluid_audio_driver(settings, synth);
    if (!driver) {
        delete_fluid_synth(synth);
        delete_fluid_settings(settings);
        return 0;
    }
    return reinterpret_cast<jlong>(new Engine{settings, synth, driver});
}

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv *, jclass, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    delete_fluid_audio_driver(engine->driver);
    delete_fluid_synth(engine->synth);
    delete_fluid_settings(engine->settings);
    delete engine;
}

JNIEXPORT jint JNICALL JNI_FN(nativeLoadSoundFont)(JNIEnv *env, jclass, jlong handle, jstring path) {
    const char *cPath = env->GetStringUTFChars(path, nullptr);
    if (!cPath) return FLUID_FAILED;
    int id = fluid_synth_sfload(synthOf(handle), cPath, 0);
    env->ReleaseStringUTFChars(path, cPath);
    return id;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeProgramSelect)(JNIEnv *, jclass, jlong handle, jint chan, jint sfontId,
                                                       jint bank, jint program) {
    return fluid_synth_program_select(synthOf(handle), chan, sfontId, bank, program) == FLUID_OK;
}

JNIEXPORT void JNICALL JNI_FN(nativeNoteOn)(JNIEnv *, jclass, jlong handle, jint chan, jint key, jint vel) {
    fluid_synth_noteon(synthOf(handle), chan, key, vel);
}

JNIEXPORT void JNICALL JNI_FN(nativeNoteOff)(JNIEnv *, jclass, jlong handle, jint chan, jint key) {
    fluid_synth_noteoff(synthOf(handle), chan, key);
}

JNIEXPORT void JNICALL JNI_FN(nativeControlChange)(JNIEnv *, jclass, jlong handle, jint chan, jint ctrl, jint value) {
    fluid_synth_cc(synthOf(handle), chan, ctrl, value);
}

JNIEXPORT void JNICALL JNI_FN(nativePitchBend)(JNIEnv *, jclass, jlong handle, jint chan, jint value) {
    fluid_synth_pitch_bend(synthOf(handle), chan, value);
}

JNIEXPORT void JNICALL JNI_FN(nativeAllSoundsOff)(JNIEnv *, jclass, jlong handle) {
    fluid_synth_all_sounds_off(synthOf(handle), -1);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetReverb)(JNIEnv *, jclass, jlong handle, jdouble level) {
    fluid_synth_reverb_on(synthOf(handle), -1, level > 0.0);
    fluid_synth_set_reverb_group_level(synthOf(handle), -1, level);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetChorus)(JNIEnv *, jclass, jlong handle, jdouble level) {
    fluid_synth_chorus_on(synthOf(handle), -1, level > 0.0);
    fluid_synth_set_chorus_group_level(synthOf(handle), -1, level);
}

}  // extern "C"
