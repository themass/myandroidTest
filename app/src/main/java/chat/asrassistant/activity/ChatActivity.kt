package chat.asrassistant.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import chat.asrassistant.adapter.MyPagerAdapter
import chat.asrassistant.config.Config
import chat.asrassistant.model.ChatMessageBean
import chat.asrassistant.utils.AsrUtil
import chat.asrassistant.utils.HttpUtil
import chat.ui.data.fixtures.MessagesFixtures
import com.blankj.utilcode.util.LogUtils
import com.openapi.ks.moviefree1.R
import com.openapi.ks.myapp.bean.form.ChatLog
import com.openapi.ks.myapp.bean.form.ConnLog
import com.openapi.ks.myapp.data.BaseService
import com.openapi.ks.myapp.data.DBManager
import com.openapi.ks.myapp.ui.base.app.BaseFragmentActivity
import com.ping.greendao.gen.ChatLogDao
import io.vov.vitamio.utils.Log
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.activity_chat_customer.input
import kotlinx.android.synthetic.main.fragment_layout.*
import java.text.SimpleDateFormat
import java.util.*


class ChatActivity : BaseFragmentActivity() {

    lateinit var asrUtil: AsrUtil
    var lastFragment: MyFragment? = null
    var sdf = SimpleDateFormat("HH:mm")
    var scrollState = 0
    val REQUEST_RECORD_AUDIO_PERMISSION = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_customer)
    }

    override fun setupView() {
        super.setupView()
        showToolbar(false)
        asrUtil = AsrUtil(this) { result, isLast ->
            if (isLast) {
                lastFragment?.mChatAdapter?.apply {
                    val time = sdf.format(Date())
                    if (mData.count { it.type == ChatMessageBean.TYPE_SYSTEM && it.content == time } == 0) {
                        addData(ChatMessageBean(ChatMessageBean.TYPE_SYSTEM, null, null, sdf.format(Date())))
                        var relog = ChatLog()
                        relog.content = sdf.format(Date())
                        relog.who = ChatMessageBean.TYPE_SYSTEM
                        DBManager.getInstance().saveChatLog(relog)
                    }
                    addData(ChatMessageBean(ChatMessageBean.TYPE_SEND, "", "", result))
                    val receivedMessage = ChatMessageBean(ChatMessageBean.TYPE_RECEIVED, Config.assistantName, "", "请稍等...")
                    addData(receivedMessage)
                    lastFragment?.rv_chat?.scrollToPosition(mData.size - 1)
                    lastFragment?.rv_chat?.clearOnScrollListeners()
                    lastFragment?.rv_chat?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            scrollState = newState
                        }
                    })
                    var index = 0
                    HttpUtil.chat(result, object : HttpUtil.CallBack {
                        override fun onCallBack(result: String, isLast: Boolean) {
                            runOnUiThread {
                                receivedMessage.content = result
                                if ((scrollState == 0 && index % 3 == 0) || isLast) {
                                    updateData()
                                }
                                if ((scrollState == 0 && ++index % 20 == 0) || isLast) {
                                    lastFragment?.rv_chat?.scrollToPosition(mData.size - 1)
                                }
                            }
                        }
                    })
                }
            }
        }
        initView()
        requestAudioPermission()
        if (Config.apiKey.isNullOrEmpty()) {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
        tv_title.text = Config.assistantName
    }
    private fun requestAudioPermission() {
        val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        val adapter = MyPagerAdapter(supportFragmentManager)
        lastFragment = MyFragment()
        adapter.addFragment(lastFragment, "Fragment 1")
        val viewPager = findViewById<View>(R.id.viewpager) as ViewPager
        viewPager.adapter = adapter
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
//                TODO("Not yet implemented")
            }
            override fun onPageSelected(position: Int) {
//                TODO("Not yet implemented")
            }
            override fun onPageScrollStateChanged(state: Int) {
//                TODO("Not yet implemented")
            }
        })

//        bt_asr.setOnTouchListener { view, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    LogUtils.e("按住结束")
//                    asrUtil.onClick(1)
//                }
//                MotionEvent.ACTION_UP -> {
//                    LogUtils.e("按住结束")
//                    asrUtil.onClick(2)
//                }
//            }
//            true
//        }
//        bt_switch.setOnClickListener {
//            bt_asr.visibility = View.GONE
//            chat_bottom.visibility = View.VISIBLE
//        }
//        chat_voice.setOnClickListener {
//            bt_asr.visibility = View.VISIBLE
//            chat_bottom.visibility = View.GONE
//        }
        input.button.setOnClickListener {
            val result = input.inputEditText.text.toString()
            if (result.isNotBlank()) {
                var log = ChatLog()
                log.content = result
                log.who = ChatMessageBean.TYPE_SEND
                DBManager.getInstance().saveChatLog(log)
                lastFragment?.mChatAdapter?.apply {
                    val time = sdf.format(Date())
                    if (mData.count { it.type == ChatMessageBean.TYPE_SYSTEM && it.content == time } == 0) {
                        addData(ChatMessageBean(ChatMessageBean.TYPE_SYSTEM, null, null, sdf.format(Date())))
                        var relog = ChatLog()
                        relog.content = sdf.format(Date())
                        relog.who = ChatMessageBean.TYPE_SYSTEM
                        DBManager.getInstance().saveChatLog(relog)
                    }
                    addData(ChatMessageBean(ChatMessageBean.TYPE_SEND, "", "", result))
                    val receivedMessage = ChatMessageBean(ChatMessageBean.TYPE_RECEIVED, Config.assistantName, "", getString(R.string.hold_please))
                    addData(receivedMessage)
                    lastFragment?.rv_chat?.scrollToPosition(mData.size - 1)
                    lastFragment?.rv_chat?.clearOnScrollListeners()
                    lastFragment?.rv_chat?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            scrollState = newState
                        }
                    })
                    var index = 0
                    HttpUtil.chatNoStream(result, object : HttpUtil.CallBack {
                        override fun onCallBack(result: String, isLast: Boolean) {
                            runOnUiThread {
                                receivedMessage.content = result
                                if ((scrollState == 0 && index % 3 == 0) || isLast) {
                                    updateData()
                                }
                                if ((scrollState == 0 && ++index % 20 == 0) || isLast) {
                                    lastFragment?.rv_chat?.scrollToPosition(mData.size - 1)
                                }
                                if(isLast){
                                var relog = ChatLog()
                                relog.content = result
                                relog.who = ChatMessageBean.TYPE_RECEIVED
                                DBManager.getInstance().saveChatLog(relog)
                                }
                            }
                        }
                    })
                }

            }
            input.inputEditText.setText("")
        }
        ll_settings.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
    }
}

