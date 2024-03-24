package chat.asrassistant.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_layout.*
import com.openapi.ks.moviefree1.R
import chat.asrassistant.adapter.ChatAdapter
import chat.asrassistant.model.ChatMessageBean
import chat.ui.data.fixtures.MessagesFixtures
import com.openapi.ks.myapp.data.DBManager
import java.util.ArrayList

class MyFragment : Fragment() {
    lateinit var mChatAdapter: ChatAdapter
    var messageBeen: ArrayList<ChatMessageBean> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_layout, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity ?: return
        //初始化 chatlog， 从数据库表里取出来填充
        var chatlogs = DBManager.getInstance().getDaoSession().chatLogDao.loadAll()
        for (log in chatlogs){
            messageBeen.add(ChatMessageBean(log.who, "哈哈", "https://t7.baidu.com/it/u=1819248061,230866778&fm=193&f=GIF", log.content))
        }
        mChatAdapter = ChatAdapter(activity, messageBeen)
        mChatAdapter.setHasStableIds(true)
        rv_chat.itemAnimator?.changeDuration = 0
        rv_chat.layoutManager = LinearLayoutManager(context)
        rv_chat.adapter = mChatAdapter
        mChatAdapter?.apply {
            rv_chat?.scrollToPosition(mData.size - 1)
        }
    }
}