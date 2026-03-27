cmake_minimum_required(VERSION 3.22.1)

project(seekdb_android_jni)

add_library(
        seekdb_android_jni
        SHARED
        seekdb_android_jni.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(
        seekdb_android_jni
        ${log-lib}
        ${dl-lib}
)
