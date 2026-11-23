#include <android/bitmap.h>
#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "include/yolo_detector_mnn.h"

namespace {

void throw_java_exception(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

bool ensure_bitmap_is_rgba8888(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info) {
    if (AndroidBitmap_getInfo(env, bitmap, info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "Unable to read bitmap info");
        return false;
    }
    if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "Bitmap config must be ARGB_8888");
        return false;
    }
    return true;
}

std::vector<uint8_t> rgba_to_bgr(const AndroidBitmapInfo& info, void* pixels) {
    const auto* src = static_cast<uint8_t*>(pixels);
    std::vector<uint8_t> bgr(static_cast<size_t>(info.width) * static_cast<size_t>(info.height) * 3U);
    for (uint32_t y = 0; y < info.height; ++y) {
        const uint8_t* row = src + y * info.stride;
        for (uint32_t x = 0; x < info.width; ++x) {
            const uint8_t* rgba = row + x * 4U;
            uint8_t* out = &bgr[(static_cast<size_t>(y) * info.width + x) * 3U];
            out[0] = rgba[2];
            out[1] = rgba[1];
            out[2] = rgba[0];
        }
    }
    return bgr;
}

// Lock bitmap pixels, convert RGBA→BGR, unlock. Returns empty vector on failure.
std::vector<uint8_t> bitmap_to_bgr(JNIEnv* env, jobject bitmap, const AndroidBitmapInfo& info) {
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
        throw_java_exception(env, "java/lang/IllegalStateException", "Unable to lock bitmap pixels");
        return {};
    }
    std::vector<uint8_t> bgr = rgba_to_bgr(info, pixels);
    AndroidBitmap_unlockPixels(env, bitmap);
    return bgr;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aimath_paizhao_photosolve_cropassist_company_DocOcrNativeBridge_nativeCreate(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jstring orient_model_path,
    jint input_size,
    jint num_threads,
    jfloat conf_threshold,
    jfloat nms_threshold,
    jfloat overlap_weight,
    jfloat orient_min_score,
    jfloat orient_rotate_180_min_score,
    jfloat orient_max_aspect_ratio,
    jint orient_input_size,
    jint orient_num_threads) {
    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (path_chars == nullptr) {
        return 0L;
    }

    const char* orient_chars = nullptr;
    if (orient_model_path != nullptr) {
        orient_chars = env->GetStringUTFChars(orient_model_path, nullptr);
    }

    YoloConfig config = yolo_default_config();
    config.input_size = input_size;
    config.num_threads = num_threads;
    config.conf_threshold = conf_threshold;
    config.nms_threshold = nms_threshold;
    config.overlap_weight = overlap_weight;
    config.orient_config.min_score = orient_min_score;
    config.orient_config.rotate_180_min_score = orient_rotate_180_min_score;
    config.orient_config.max_aspect_ratio = orient_max_aspect_ratio;
    config.orient_config.input_size = orient_input_size;
    config.orient_config.num_threads = orient_num_threads;

    void* detector = yolo_mnn_detector_create(path_chars, orient_chars, &config);

    env->ReleaseStringUTFChars(model_path, path_chars);
    if (orient_chars != nullptr) {
        env->ReleaseStringUTFChars(orient_model_path, orient_chars);
    }

    return reinterpret_cast<jlong>(detector);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aimath_paizhao_photosolve_cropassist_company_DocOcrNativeBridge_nativeDestroy(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong handle) {
    if (handle != 0L) {
        yolo_mnn_detector_destroy(reinterpret_cast<void*>(handle));
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_aimath_paizhao_photosolve_cropassist_company_DocOcrNativeBridge_nativeDetectBitmap(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jobject bitmap,
    jfloat rec_x1,
    jfloat rec_y1,
    jfloat rec_x2,
    jfloat rec_y2) {
    if (handle == 0L) {
        throw_java_exception(env, "java/lang/IllegalStateException", "Detector handle is null");
        return nullptr;
    }

    AndroidBitmapInfo info{};
    if (!ensure_bitmap_is_rgba8888(env, bitmap, &info)) {
        return nullptr;
    }

    std::vector<uint8_t> bgr = bitmap_to_bgr(env, bitmap, info);
    if (bgr.empty()) {
        return nullptr;
    }

    YoloBox rec_box{rec_x1, rec_y1, rec_x2, rec_y2, 0.0f, 0};
    YoloResult result = yolo_mnn_detect(
        reinterpret_cast<void*>(handle),
        bgr.data(),
        static_cast<int>(info.height),
        static_cast<int>(info.width),
        &rec_box);

    const jsize output_size = static_cast<jsize>(result.count * 6);
    jfloatArray output = env->NewFloatArray(output_size);
    if (output == nullptr) {
        yolo_mnn_result_free(&result);
        return nullptr;
    }

    std::vector<jfloat> flattened(static_cast<size_t>(output_size));
    for (int i = 0; i < result.count; ++i) {
        const YoloBox& box = result.boxes[i];
        const size_t base = static_cast<size_t>(i) * 6U;
        flattened[base + 0U] = box.x1;
        flattened[base + 1U] = box.y1;
        flattened[base + 2U] = box.x2;
        flattened[base + 3U] = box.y2;
        flattened[base + 4U] = box.confidence;
        flattened[base + 5U] = static_cast<jfloat>(box.class_id);
    }
    env->SetFloatArrayRegion(output, 0, output_size, flattened.data());
    yolo_mnn_result_free(&result);
    return output;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aimath_paizhao_photosolve_cropassist_company_DocOcrNativeBridge_nativeOrientPredict(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jobject bitmap) {
    if (handle == 0L) {
        throw_java_exception(env, "java/lang/IllegalStateException", "Detector handle is null");
        return 0;
    }

    AndroidBitmapInfo info{};
    if (!ensure_bitmap_is_rgba8888(env, bitmap, &info)) {
        return 0;
    }

    std::vector<uint8_t> bgr = bitmap_to_bgr(env, bitmap, info);
    if (bgr.empty()) {
        return 0;
    }

    return yolo_mnn_orient_predict(
        reinterpret_cast<void*>(handle),
        bgr.data(),
        static_cast<int>(info.height),
        static_cast<int>(info.width));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aimath_paizhao_photosolve_cropassist_company_DocOcrNativeBridge_nativeClassNameForDetector(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jint class_id) {
    const char* name = nullptr;
    if (handle != 0L) {
        name = yolo_mnn_class_name_for_detector(reinterpret_cast<void*>(handle), class_id);
    }
    if (name == nullptr) {
        name = yolo_mnn_class_name(class_id);
    }
    return env->NewStringUTF(name != nullptr ? name : "Unknown");
}
