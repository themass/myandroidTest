#ifndef YOLO_DETECTOR_MNN_H
#define YOLO_DETECTOR_MNN_H

#ifdef _WIN32
    #ifdef YOLO_EXPORT_BUILDING
        #define YOLO_EXPORT_API __declspec(dllexport)
    #else
        #define YOLO_EXPORT_API __declspec(dllimport)
    #endif
#else
    #define YOLO_EXPORT_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    float x1, y1, x2, y2;
    float confidence;
    int class_id;
} YoloBox;

typedef struct {
    YoloBox* boxes;
    int count;
} YoloResult;

/**
 * Orientation correction configuration for PP-LCNet_x1_0_doc_ori.
 * Defaults mirror python/image_rotate/src/mnn_rotate_correct_stb.cpp.
 */
typedef struct {
    float min_score;             /* default 0.5  */
    float rotate_180_min_score;  /* default 0.9  */
    float max_aspect_ratio;      /* default 10.0; <=0 disables aspect guard */
    int   input_size;            /* default 224  */
    int   num_threads;           /* default 2    */
} YoloOrientConfig;


typedef struct {
    int input_size;        /* model input side length; default 640 (Ultralytics). PP-DocLayout often 480. */
    int num_threads;       /* inference threads, default 2 */
    float conf_threshold;  /* default 0.25 (matches infer_detect.py CONF) */
    float nms_threshold;   /* NMS IoU; default 0.7 (matches infer_detect.py IOU) */
    /** E2E 选框：overlap 分权重 x，score = overlap*x + center*(1-x)；默认 0.8 */
    float overlap_weight;

    /* Thresholds for the optional orientation correction model.
     * Consulted only when an orient model path is provided to
     * yolo_mnn_detector_create. Fields left at 0 fall back to module defaults
     * (see yolo_orient_default_config). max_aspect_ratio<=0 disables the
     * aspect-ratio guard. */
    YoloOrientConfig orient_config;
} YoloConfig;

/**
 * Create a YOLO detector instance.
 *
 * If orient_model_path is non-NULL, the PP-LCNet_x1_0_doc_ori orientation
 * classifier is loaded alongside the layout model so the caller can invoke
 * yolo_mnn_orient_predict() to obtain the rotation angle. The detector itself
 * does NOT rotate the image — the caller is responsible for rotating the
 * image and adjusting any rec_box before calling yolo_mnn_detect().
 *
 * Failing to load the orient model does NOT fail the whole detector; a
 * warning is logged and yolo_mnn_orient_predict will return a zeroed result.
 *
 * @param model_path        Path to a .mnn: Paddle PP-DocLayout (inputs image+scale_factor)
 *                          or Ultralytics YOLO export (single NCHW input, 3D detect head).
 * @param orient_model_path Optional; pass NULL to disable orientation correction
 * @param config            Optional config (also carries orient thresholds);
 *                          pass NULL for defaults (640 / conf=0.25 / nms=0.7).
 * @return Opaque detector handle, or NULL on failure
 */
YOLO_EXPORT_API void* yolo_mnn_detector_create(const char* model_path,
                                          const char* orient_model_path,
                                          const YoloConfig* config);

/**
 * Destroy a YOLO detector instance and free all resources.
 */
YOLO_EXPORT_API void yolo_mnn_detector_destroy(void* detector);

/**
 * Run detection on a BGR image.
 *
 * NOTE: yolo_mnn_detect does NOT apply rotation correction. If the caller
 * wants rotation handling, they should first call yolo_mnn_orient_predict(),
 * rotate the image and rec_box themselves, then pass the rotated inputs to
 * this function. The returned boxes are always expressed in the SAME
 * coordinate system as the image / rec_box passed in.
 *
 * @param detector  Handle returned by yolo_detector_create
 * @param bgr_data  BGR image pixel data (HWC layout, uint8)
 * @param height    Image height
 * @param width     Image width
 * @param box       Recommended box (same coord space as bgr_data)
 * @return Detection result; caller must free with yolo_result_free
 */
YOLO_EXPORT_API YoloResult yolo_mnn_detect(void* detector,
                                      const unsigned char* bgr_data,
                                      int height, int width,
                                      YoloBox* box);

/**
 * Predict document rotation angle using the orientation classifier loaded at
 * create time, applying the configured decision rules (min_score,
 * rotate_180_min_score, max_aspect_ratio) on top of the raw model output.
 *
 * Returns the CCW angle that should actually be applied to rotate the image
 * upright, one of {0, 90, 180, 270}. 0 means "do not rotate" — either the
 * document is already upright, or the prediction did not meet the decision
 * thresholds, or the detector was created without an orient_model_path, or
 * the input is invalid. The raw predicted angle / score / decision reason are
 * still logged internally for diagnostics.
 *
 * Image rotation itself is the caller's responsibility — see sample_yolo.cpp
 * for reference helpers (rotate_hwc3_ccw, rotate_box_forward/inverse).
 *
 * @param detector  Handle returned by yolo_detector_create
 * @param bgr_data  BGR image pixel data (HWC layout, uint8)
 * @param height    Image height
 * @param width     Image width
 * @return applied_angle ∈ {0, 90, 180, 270}.
 */
YOLO_EXPORT_API int yolo_mnn_orient_predict(void* detector,
                                            const unsigned char* bgr_data,
                                            int height, int width);

/**
 * Free detection result memory.
 */
YOLO_EXPORT_API void yolo_mnn_result_free(YoloResult* result);

/**
 * Get DocLayNet class name by class ID.
 * Returns "Unknown" for invalid IDs.
 */
YOLO_EXPORT_API const char* yolo_mnn_class_name(int class_id);

/**
 * Class label for a detector instance: DocLayNet names for PP models;
 * for Ultralytics YOLO, single-class models use "text", else "cls_%d".
 */
YOLO_EXPORT_API const char* yolo_mnn_class_name_for_detector(void* detector, int class_id);

/**
 * Default config: input_size=640, conf=0.25, nms=0.7, overlap_weight=0.8.
 */
YOLO_EXPORT_API YoloConfig yolo_default_config(void);

/**
 * Default orientation thresholds (min_score=0.5, rotate_180_min_score=0.9,
 * max_aspect_ratio=10.0, input_size=224, num_threads=2). Use this to seed
 * YoloConfig.orient_config before enabling orientation correction via
 * YoloConfig.orient_model_path.
 */
YOLO_EXPORT_API YoloOrientConfig yolo_orient_default_config(void);

#ifdef __cplusplus
}
#endif

#endif // YOLO_DETECTOR_MNN_H
