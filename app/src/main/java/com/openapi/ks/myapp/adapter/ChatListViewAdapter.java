package com.openapi.ks.myapp.adapter;

import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.openai.commonutil.LogUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.AppInfo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import java.util.List;

import butterknife.BindView;

/**
 * Created by openapi on 2016/8/12.
 */
public class ChatListViewAdapter extends BaseRecyclerViewAdapter<ChatListViewAdapter.SessionItemView, ChatSessionLog> {
    public ChatListViewAdapter(FragmentActivity context, RecyclerView recyclerView, List<ChatSessionLog> data, OnRecyclerViewItemClickListener<ChatSessionLog> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public SessionItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_chat_item, parent, false);
        return new SessionItemView(view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        SessionItemView holder = (SessionItemView) h;
        ChatSessionLog vo = data.get(position);
        holder.tvName.setText(vo.name);
        LogUtil.i("name:"+vo.name);
    }

    public static class SessionItemView extends BaseRecyclerViewHolder<ChatSessionLog> {
        @Nullable
        @BindView(R.id.session_name)
        TextView tvName;
        public SessionItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
