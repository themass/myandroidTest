package com.timeline.myapp.adapter;

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
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.VideoUtil;
import com.timeline.vpn.R;

import java.util.List;

import butterknife.BindView;
import cn.jzvd.JZVideoPlayerStandard;


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
            Object[] source = VideoUtil.getVideoSource(vo.actionUrl,false, StringUtils.hasText(vo.param)?vo.param: vo.actionUrl);
            holder.jcVideoPlayer.setUp(source,0, JZVideoPlayerStandard.SCREEN_WINDOW_LIST, vo.title);
//            holder.jcVideoPlayer.hea = header;
            Glide.with(context).load(vo.img).into(holder.jcVideoPlayer.thumbImageView);
            if(Constants.BANNER_ADS_POS.contains(position)){
                if(position%2==1){
                    holder.rvAds.setVisibility(View.VISIBLE);
                    AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
                }else{
                    holder.rvAds.setVisibility(View.VISIBLE);
                    AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
                }
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