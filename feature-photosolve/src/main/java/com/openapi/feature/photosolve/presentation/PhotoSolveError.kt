package com.openapi.feature.photosolve.presentation

/**
 * 拍照解题的错误码。数值与服务端 errno 一一对应（见 testserverV `PhotoSolveErrno`），
 * **不要改数值**：客户端和服务端/docs 靠这些数字对齐。
 */
object PhotoSolveError {
    /** 图片不清晰：提示重拍，也可以让用户选择「仍然提交」。 */
    const val BLURRY = 40001

    /** 不是题目：回到取景/裁剪页重新对准。 */
    const val NOT_A_QUESTION = 40002

    /** 图片过大或格式不支持。 */
    const val BAD_IMAGE = 40003

    /** 这个用户今天的额度用完（或一分钟内请求太多）。 */
    const val QUOTA_EXCEEDED = 42901

    /** 全站当日预算用完（服务端熔断），不是用户的问题。 */
    const val BUDGET_EXHAUSTED = 42902

    /** 模型服务失败/超时，可重试。 */
    const val MODEL_UNAVAILABLE = 50001

    /** 服务端没配置模型。 */
    const val PROVIDER_NOT_CONFIGURED = 50002

    /** 参数错误。 */
    const val BAD_REQUEST = 40000

    /** 面向用户的一句话。 */
    fun userMessage(code: Int): String = when (code) {
        BLURRY -> "图片不够清晰，建议重拍"
        NOT_A_QUESTION -> "没有识别到题目，请重新对准题目"
        BAD_IMAGE -> "图片过大或格式不支持，请换一张图片"
        QUOTA_EXCEEDED -> "今日识别额度已用完"
        BUDGET_EXHAUSTED -> "今日识别服务已满，请明天再来"
        MODEL_UNAVAILABLE -> "识别服务暂时不可用，请重试"
        PROVIDER_NOT_CONFIGURED -> "解题服务尚未配置好，请稍后再试"
        BAD_REQUEST -> "请求有误，请重试"
        else -> "识别失败，请重试"
    }

    /** 这个错误是否值得原地重试一次（网络抖动、模型超时）。 */
    fun isRetryable(code: Int): Boolean = code == MODEL_UNAVAILABLE
}

/** 带服务端错误码的异常，界面按 [code] 分支处理。 */
class PhotoSolveException(val code: Int, message: String) : Exception(message)
