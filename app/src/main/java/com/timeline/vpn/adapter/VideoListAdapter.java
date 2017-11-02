package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import java.util.List;

import butterknife.BindView;
import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerStandard;

import static com.kuaiyou.g.a.getActivity;

public class VideoListAdapter extends BaseRecyclerViewAdapter<VideoListAdapter.VideoHolder, RecommendVo> {

    public VideoListAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener<RecommendVo> listener) {
        super(context, recyclerView, data, listener);
    }
    @Override
    public VideoHolder onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_videoview, parent, false);
        return new VideoHolder(view, this, this);
    }
        @Override
        public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
            //This is the point
            VideoHolder holder = (VideoHolder)h;
            RecommendVo vo = data.get(position);
            LogUtil.i("jcVideoPlayer="+holder.jcVideoPlayer.toString());
            holder.jcVideoPlayer.setUp(vo.actionUrl, JZVideoPlayer.SCREEN_LAYOUT_LIST, vo.title);
            Glide.with(getActivity()).load(vo.img).into(holder.jcVideoPlayer.thumbImageView);
            if(position== Constants.BANNER_ADS_POS_1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_3);
            }else if(position== Constants.BANNER_ADS_POS_2){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_1);
            }else if(position== Constants.BANNER_ADS_POS_3){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_3);
            }else if(position== Constants.BANNER_ADS_POS_4){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_1);
            }else{
                holder.rvAds.removeAllViews();
                holder.rvAds.setVisibility(View.GONE);
            }
        }

        public  static class VideoHolder  extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<RecommendVo>{
            @Nullable
            @BindView(R.id.videoplayer)
            JZVideoPlayerStandard jcVideoPlayer;
            @Nullable
            @BindView(R.id.rv_ads)
            RelativeLayout rvAds;
            public VideoHolder(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
                super(itemView, l, longListener);
            }
        }
    }