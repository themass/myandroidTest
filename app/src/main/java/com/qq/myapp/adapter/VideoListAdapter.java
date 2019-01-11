package com.qq.myapp.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.qq.kuaibo.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.VideoUtil;

import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import cn.jzvd.JZDataSource;
import cn.jzvd.JzvdStd;


public class VideoListAdapter extends BaseRecyclerViewAdapter<VideoListAdapter.VideoHolder, RecommendVo> {
    GdtNativeManager gdtNativeManager;
    public VideoListAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener<RecommendVo> listener,GdtNativeManager gdtNativeManager) {
        super(context, recyclerView, data, listener);
        this.gdtNativeManager = gdtNativeManager;
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
            HashMap<String,String> header = new HashMap<>();
            header.put(Constants.REFERER, StringUtils.hasText(vo.baseurl)?vo.baseurl: vo.actionUrl);
            JZDataSource source = new JZDataSource(vo.actionUrl ,vo.title);
            source.headerMap = header;
            holder.jcVideoPlayer.setUp(source,JzvdStd.SCREEN_WINDOW_LIST);
//            Object[] source = VideoUtil.getVideoSource(vo.actionUrl,false,StringUtils.hasText(vo.param)?vo.param: vo.actionUrl);
//            holder.jcVideoPlayer.setUp(StringUtils.hasText(vo.param)?vo.param: vo.actionUrl,vo.title, JzvdStd .SCREEN_WINDOW_LIST);
//            holder.jcVideoPlayer.hea = header;
            Glide.with(context).load(vo.img).into(holder.jcVideoPlayer.thumbImageView);
            if(gdtNativeManager!=null){
                if(!gdtNativeManager.showAds(position,holder.natvieView)){
                    holder.natvieView.setVisibility(View.GONE);
                }
            }else{
                holder.natvieView.setVisibility(View.GONE);
            }
            if(Constants.BANNER_ADS_POS.contains(position)){
                if(position%3==0){
                    holder.rvAds.setVisibility(View.VISIBLE);
                    AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
                }if(position%3==1){
                    holder.rvAds.setVisibility(View.VISIBLE);
                    AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
                }else{
                    holder.rvAds.setVisibility(View.VISIBLE);
                    AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1);
                }
            }else{
                holder.rvAds.removeAllViews();
                holder.rvAds.setVisibility(View.GONE);
            }
        }

        public  static class VideoHolder  extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<RecommendVo>{
            @Nullable
            @BindView(R.id.videoplayer)
            JzvdStd jcVideoPlayer;
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
            public VideoHolder(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
                super(itemView, l, longListener);
            }
        }
    }