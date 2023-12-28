package com.openapi.ks.myapp.adapter;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.sound.JzvdPlayerFactory;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.AdsManager;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;

import java.util.List;

import butterknife.BindView;
import cn.jzvd.JzvdStd;


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

//            if(vo.actionUrl.contains("?")){
//                vo.actionUrl = vo.actionUrl+"&t="+ (System.currentTimeMillis()/1000);
//            }else{
//                vo.actionUrl = vo.actionUrl+"?t="+ (System.currentTimeMillis()/1000);
//            }

            holder.jcVideoPlayer.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JzvdPlayerFactory.getPlayManager());
            holder.jcVideoPlayer.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(vo.actionUrl, StringUtils.hasText(vo.baseurl) ? vo.baseurl : vo.actionUrl);
//

            https://vd2.bdstatic.com/mda-pb6cpnvt5v2kjqke/sc/cae_h264/1675761743990346865/mda-pb6cpnvt5v2kjqke.mp4
//            holder.jcVideoPlayer.setUp(vo.actionUrl, vo.title, Jzvd.SCREEN_NORMAL);
//
//            holder.jcVideoPlayer.starsetUpvo.actionUrionUrl,vo.title, Jzvd.SCREEN_NORMAL);
//            holder.jcVideoPlayer.hea = header;
            ImagePhotoLoad.loadCommonImg(context,vo.img,holder.jcVideoPlayer.posterImageView);
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
            JzvdStd jcVideoPlayer;
            @Nullable
            @BindView(R.id.rv_ads)
            RelativeLayout rvAds;
            public VideoHolder(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
                super(itemView, l, longListener);
            }
        }
    }