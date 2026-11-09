cmake_minimum_required(VERSION 3.22.1)
project(openkeys_synth CXX)

set(FLUIDSYNTH_LIB ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfluidsynth.so)

add_library(openkeys_synth SHARED openkeys_synth.cpp)
target_include_directories(openkeys_synth PRIVATE ${CMAKE_SOURCE_DIR}/fluidsynth/include)
# Linked by path, not as an IMPORTED target, so AGP packages it only once (from jniLibs).
target_link_libraries(openkeys_synth ${FLUIDSYNTH_LIB} log)
target_link_options(openkeys_synth PRIVATE "-Wl,-z,max-page-size=16384")
