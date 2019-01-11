package com.qq.myapp.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.qq.kuaibo.R;
import com.sspacee.common.util.DateUtils;
import com.sspacee.common.util.StringUtils;

import com.qq.myapp.bean.vo.IWannaVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.task.IWannaLikeTask;

import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by froger_mcs on 05.11.14.
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final String ACTION_LIKE_BUTTON_CLICKED = "action_like_button_button";
    public static final String ACTION_LIKE_IMAGE_CLICKED = "action_like_image_button";
    public static final String style1 = "<span style=\"background:#BDD3F7\"> @All </span>";
    public static final  String style2 = "<span style=\"background:#BDD3F7\"> %s </span>";

    public static final int VIEW_TYPE_DEFAULT = 1;
    public static final int VIEW_TYPE_LOADER = 2;
    private final List<IWannaVo> feedItems;
    private OnFeedItemClickListener listener;
    private Context context;
    private OnFeedItemClickListener onFeedItemClickListener;

    private boolean showLoadingView = false;
    private String likeUrl;
    public FeedAdapter(Context context, List<IWannaVo> data, OnFeedItemClickListener listener,String likeUrl) {
        this.context = context;
        this.listener = listener;
        this.feedItems = data;
        this.likeUrl = likeUrl;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_iwanna_feed_item, parent, false);
        CellFeedViewHolder cellFeedViewHolder = new CellFeedViewHolder(view);
        setupClickableViews(view, cellFeedViewHolder);
        return cellFeedViewHolder;
    }

    private void setupClickableViews(final View view, final CellFeedViewHolder cellFeedViewHolder) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adapterPosition = cellFeedViewHolder.getAdapterPosition();
//                if(!feedItems.get(adapterPosition).isLike){
                feedItems.get(adapterPosition).like = true;
                feedItems.get(adapterPosition).likes++;
                notifyItemChanged(adapterPosition, ACTION_LIKE_IMAGE_CLICKED);
                if (listener != null) {
                    listener.onCommentsClick(view, adapterPosition);
                }
                IWannaLikeTask.start(context, likeUrl,feedItems.get(adapterPosition).id);
//                }

            }
        });
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ((CellFeedViewHolder) viewHolder).bindView(feedItems.get(position));
    }

    @Override
    public int getItemCount() {
        return feedItems.size();
    }

    public interface OnFeedItemClickListener {
        void onCommentsClick(View v, int position);
    }

    public static class CellFeedViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.iv_heart)
        public ImageView imageView;
        @BindView(R.id.tv_content)
        public TextView tvContent;
        @BindView(R.id.tsLikesCounter)
        public TextSwitcher tsLikesCounter;
        public IWannaVo feedItem;
        @BindView(R.id.iv_ok)
        ImageView ivOk;
        @BindView(R.id.tv_app_name)
        public TextView appName;
        @BindView(R.id.tv_where)
        public TextView tvWhere;
        @BindView(R.id.tv_name)
        public TextView name;
        @BindView(R.id.tv_time)
        public TextView time;
        public CellFeedViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        public void bindView(IWannaVo feedItem) {
            this.feedItem = feedItem;
            tsLikesCounter.setCurrentText(tvContent.getResources().getQuantityString(
                    R.plurals.likes_count, feedItem.likes, feedItem.likes
            ));
            tvContent.setText(Html.fromHtml(handleContent(feedItem.content)));
//            tvContent.setText(feedItem.content);
            name.setText(feedItem.name);
            if (feedItem.like) {
                imageView.setImageResource(R.drawable.ic_heart_small_red);
            } else {
                imageView.setImageResource(R.drawable.ic_heart_small_blue);
            }
            appName.setText(feedItem.appName);
            tvWhere.setText(feedItem.where);
            time.setText(DateUtils.format(new Date(feedItem.time),DateUtils.DATETIME_FORMAT));
            if (feedItem.finished || Constants.ADMIN.equals(feedItem.name)) {
                ivOk.setVisibility(View.VISIBLE);
            } else {
                ivOk.setVisibility(View.GONE);
            }
        }

        public IWannaVo getFeedItem() {
            return feedItem;
        }
    }
    private static String handleContent(String content){
        if(!StringUtils.hasText(content))
            return "";
        content = content.replaceAll("@All",style1).replaceAll("@all",style1);
        if(UserLoginUtil.getUserCache()!=null){
            String name = "@"+UserLoginUtil.getUserCache().name;
            content = content.replaceAll(name,String.format(style2,name));
        }
        return content;
    }
}
