cmake_minimum_required(VERSION 3.22.1)

project(dococr_jni)

# 公司算法（MNN + libdococr）只提供了 arm64-v8a 的预编译库。
# 其它 ABI 直接跳过，运行时由 DocOcrDetector 安全降级为“不可用”，不影响其余功能。
if(ANDROID_ABI STREQUAL "arm64-v8a")

    add_library(dococr SHARED IMPORTED)
    set_target_properties(
        dococr
        PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libdococr.so"
    )

    add_library(
        dococr_jni
        SHARED
        dococr_jni.cpp
    )

    target_include_directories(
        dococr_jni
        PRIVATE
        "${CMAKE_SOURCE_DIR}/include"
    )

    find_library(log-lib log)
    find_library(jnigraphics-lib jnigraphics)

    target_link_libraries(
        dococr_jni
        PRIVATE
        dococr
        ${log-lib}
        ${jnigraphics-lib}
    )

    # Google Play 16KB 页大小要求：本项目自己编译的 .so 必须 16KB 对齐
    target_link_options(dococr_jni PRIVATE "-Wl,-z,max-page-size=16384")
endif()
