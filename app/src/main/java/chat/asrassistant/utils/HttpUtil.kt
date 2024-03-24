package chat.asrassistant.utils

import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import okhttp3.*
import chat.asrassistant.config.Config
import chat.asrassistant.model.Message
import chat.asrassistant.model.StreamAiAnswer
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.BufferedReader
import java.io.IOException
import java.lang.Exception
import kotlin.collections.ArrayList


object HttpUtil {

    val history: ArrayList<Message> = arrayListOf()
    val gptRequestJson = hashMapOf(
        Pair("model", "gpt-3.5-turbo-16k"),
        Pair("stream", true),
        Pair("messages", history)
    )
    val gptRequestJsonNoStream = hashMapOf(
        Pair("model", "gpt-3.5-turbo-16k"),
        Pair("stream", false),
        Pair("messages", history)
    )
    /**
     * ChatGPT
     */
    fun chat(send: String, callback: CallBack) {
        val url = "http://book.ok123find.top/v1/chat/completions"
        val apiKey = "Bearer ${Config.apiKey}"
        if (!Config.useContext) {
            history.clear()
        }
        history.add(Message().apply {
            role = "user"
            content = send
        })
        LogUtils.d("gptRequestJson", GsonUtils.toJson(gptRequestJson))
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), GsonUtils.toJson(gptRequestJson))
        val request: Request = Request.Builder().url(url).method("POST", body)
            .addHeader("Authorization", apiKey)
            .build()
        OkHttpUtil.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                ToastUtils.showLong("网络请求出错 请检查网络")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body
                    if (responseBody != null) {
                        val message = Message().apply {
                            role = "assistant"
                            content = ""
                        }
                        history.add(message)
                        val bufferedReader = BufferedReader(responseBody.charStream())
                        var line = bufferedReader.readLine()

                        var index = 0
                        val sb = StringBuilder()
                        while (line != null) {
                            val msg = convert(line, "1", index++)
                            if (msg != null) {
                                sb.append(msg.content)
                                message.content = sb.toString()
                                callback.onCallBack(sb.toString(), false)
                            }
                            line = bufferedReader.readLine()
                        }
                        callback.onCallBack(sb.toString(), true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showLong("网络请求出错 请检查配置")
                }
            }
        })
    }
    fun chatNoStream(send: String, callback: CallBack) {
        val url = "http://book.ok123find.top/v1/chat/completions"
        val apiKey = "Bearer ${Config.apiKey}"
        if (!Config.useContext) {
            history.clear()
        }
        history.add(Message().apply {
            role = "user"
            content = send
        })
        LogUtils.d("gptRequestJson", GsonUtils.toJson(gptRequestJsonNoStream))
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), GsonUtils.toJson(gptRequestJsonNoStream))
        val request: Request = Request.Builder().url(url).method("POST", body)
            .addHeader("Authorization", apiKey)
            .build()
        OkHttpUtil.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                ToastUtils.showLong("网络请求出错 请检查网络")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body
                    if (responseBody != null) {
                        val message = Message().apply {
                            role = "assistant"
                            content = ""
                        }
                        history.add(message)
                        var conetent = responseBody.string()
                        LogUtils.i(conetent)
                        val msg = convertNoStream(conetent, "1")
                        LogUtils.i(msg)
                        if (msg != null) {
                            message.content = msg.content
                            callback.onCallBack(msg.content, true)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastUtils.showLong("网络请求出错 请检查配置")
                }
            }
        })
    }
    fun convert(answer: String, questionId: String, index: Int): Message? {
        val msg = Message()
        msg.content = ""
        msg.messageType = "normal"
        msg.id = questionId
        if ("data: [DONE]" != answer) {
            val beanStr = answer.replaceFirst("data: ", "", false)
            LogUtils.d("beanStr===", beanStr)
            try {
                val aiAnswer =
                    GsonUtils.fromJson(beanStr, StreamAiAnswer::class.java) ?: return null
                val choices = aiAnswer.choices
                if (choices.isEmpty()) {
                    return null
                }
                LogUtils.d(choices)
                val stringBuffer = StringBuffer()
                for (choice in choices) {
                    if (choice.finish_reason != "stop") {
                        if (choice.delta.content != null) {
                            stringBuffer.append(choice.delta.content)
                        } else {
                            return null
                        }
                    }
                }
                msg.content = stringBuffer.toString()
                if (index == 0) {
                    if (msg.content == "\n\n") {
                        LogUtils.e("发现开头有两次换行,移除两次换行")
                        return null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            msg.type = "stop"
        }
        msg.index = index
        return msg
    }
    fun convertNoStream(answer: String, questionId: String): Message? {
        val msg = Message()
        msg.content = ""
        msg.messageType = "normal"
        msg.id = questionId
        if ("data: [DONE]" != answer) {
            val beanStr = answer.replaceFirst("data: ", "", false)
            LogUtils.d("beanStr===", beanStr)
            try {
                val aiAnswer =
                    GsonUtils.fromJson(beanStr, StreamAiAnswer::class.java) ?: return null
                val choices = aiAnswer.choices
                if (choices.isEmpty()) {
                    return null
                }
                LogUtils.d(choices)
                val stringBuffer = StringBuffer()
                for (choice in choices) {
                    if (choice.message.content != null) {
                        stringBuffer.append(choice.message.content)
                    }
                }
                msg.content = stringBuffer.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            msg.type = "stop"
        }
        msg.index = 0
        return msg
    }
    interface CallBack {
        fun onCallBack(result: String, isLast: Boolean)
    }
}
