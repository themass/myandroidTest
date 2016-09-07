package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.IWannaVo;
import com.timeline.vpn.task.IWannaLikeTask;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by froger_mcs on 05.11.14.
 */
public class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final String ACTION_LIKE_BUTTON_CLICKED = "action_like_button_button";
    public static final String ACTION_LIKE_IMAGE_CLICKED = "action_like_image_button";

    public static final int VIEW_TYPE_DEFAULT = 1;
    public static final int VIEW_TYPE_LOADER = 2;
    private final List<IWannaVo> feedItems;
    private OnFeedItemClickListener listener;
    private Context context;
    private OnFeedItemClickListener onFeedItemClickListener;

    private boolean showLoadingView = false;

    public FeedAdapter(Context context, List<IWannaVo> data, OnFeedItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.feedItems = data;
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
                IWannaLikeTask.start(context,feedItems.get(adapterPosition).id);
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
        @Bind(R.id.iv_heart)
        public ImageView imageView;
        @Bind(R.id.tv_content)
        public TextView tvContent;
        @Bind(R.id.tsLikesCounter)
        public TextSwitcher tsLikesCounter;
        public IWannaVo feedItem;

        public CellFeedViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }

        public void bindView(IWannaVo feedItem) {
            this.feedItem = feedItem;
            tsLikesCounter.setCurrentText(tvContent.getResources().getQuantityString(
                    R.plurals.likes_count, feedItem.likes, feedItem.likes
            ));
            tvContent.setText(feedItem.content);
            if (feedItem.like) {
                imageView.setImageResource(R.drawable.ic_heart_small_red);
            } else {
                imageView.setImageResource(R.drawable.ic_heart_small_blue);
            }
        }

        public IWannaVo getFeedItem() {
            return feedItem;
        }
    }
}
