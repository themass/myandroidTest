package com.timeline.myapp.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.sspacee.common.ui.view.widgets.MusicVisualizer;
import com.timeline.vpn.R;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.SoundItemsVo;
import com.timeline.myapp.ui.inte.MusicStateListener;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsViewMusicAdapter extends BaseRecyclerViewAdapter<SoundItemsViewMusicAdapter.SoundItemView, SoundItemsVo>   {
    public int lastPosition = -1;
    MusicStateListener mService = null;
    public SoundItemsViewMusicAdapter(Context context, RecyclerView recyclerView, List<SoundItemsVo> data, OnRecyclerViewItemClickListener<SoundItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }
    public void setPlayServise(MusicStateListener service){
        mService = service;
    }
    @Override
    public SoundItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SoundItemView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        SoundItemView holder = (SoundItemView)h;
        SoundItemsVo vo = data.get(position);
        holder.title.setText(vo.name);
        holder.artist.setText(vo.fileDate);
//        holder.albumArt.setImageResource(R.drawable.ic_empty_music2);
        if (position == getSelected()) {
            holder.title.setTextColor(context.getResources().getColor(R.color.song_select_title));
            holder.visualizer.setColor(context.getResources().getColor(R.color.song_red));
            holder.visualizer.setVisibility(View.VISIBLE);
            holder.visualizer.startAnima();
//            if(mService!=null && mService.isPlaying()){
//                holder.visualizer.startAnima();
//            }else{
//                holder.visualizer.stopAnima();
//            }
            setAnimation(holder.itemView, position);
        } else {
            holder.title.setTextColor(context.getResources().getColor(R.color.main_dark));
            holder.visualizer.setVisibility(View.GONE);
        }
    }
    private void setAnimation(View viewToAnimate, int position) {
        // If the bound view wasn't previously displayed on screen, it's animated
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.abc_slide_in_bottom);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }



    static class SoundItemView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<SoundItemsVo> {
        @Nullable
        @BindView(R.id.song_title)
        protected TextView title;
        @Nullable
        @BindView(R.id.song_artist)
        protected TextView artist;
        @Nullable
        @BindView(R.id.albumArt)
        protected ImageView albumArt;
        @Nullable
        @BindView(R.id.visualizer)
        protected MusicVisualizer visualizer;
        public SoundItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
