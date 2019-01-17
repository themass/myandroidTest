package com.timeline.myapp.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.natives.NativeAdInfo;
import com.qq.sexfree.R;
import com.sspacee.common.ui.view.widgets.MusicVisualizer;

import com.sspacee.common.util.CollectionUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.SoundItemsVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.ui.inte.MusicStateListener;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsViewMusicAdapter extends BaseRecyclerViewAdapter<SoundItemsViewMusicAdapter.SoundItemView, SoundItemsVo>   {
    public int lastPosition = -1;
    MusicStateListener mService = null;
    GdtNativeManager gdtNativeManager;
    public SoundItemsViewMusicAdapter(Context context, RecyclerView recyclerView, List<SoundItemsVo> data, OnRecyclerViewItemClickListener<SoundItemsVo> listener,GdtNativeManager gdtNativeManager) {
        super(context, recyclerView, data, listener);
        this.gdtNativeManager =gdtNativeManager;
    }
    public void setPlayServise(MusicStateListener service){
        mService = service;
    }
    @Override
    public SoundItemsViewMusicAdapter.SoundItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SoundItemsViewMusicAdapter.SoundItemView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        SoundItemsViewMusicAdapter.SoundItemView holder = (SoundItemsViewMusicAdapter.SoundItemView)h;
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
        if(gdtNativeManager!=null){
            if(!gdtNativeManager.showAds(position,holder.natvieView)){
                holder.natvieView.setVisibility(View.GONE);
            }
        }else{
            holder.natvieView.setVisibility(View.GONE);
        }
        if(Constants.BANNER_ADS_POS.contains(position)){
            if(position%2==1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1);
            }else{
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            }
        }else{
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
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
        @Nullable
        @BindView(R.id.rv_ads)
        RelativeLayout rvAds;
        @Nullable
        @BindView(R.id.natvieView)
        RelativeLayout natvieView;
        @Nullable
        @BindView(R.id.desc)
        TextView desc;
        @Nullable
        @BindView(R.id.icon)
        ImageView icon;
        public SoundItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
