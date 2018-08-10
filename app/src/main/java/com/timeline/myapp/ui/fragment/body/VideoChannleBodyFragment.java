package com.timeline.myapp.ui.fragment.body;


import android.os.Bundle;
import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.base.NativeAdsReadyListener;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.fragment.RecommendFragment;
import com.timeline.myapp.ui.fragment.VideoChannelListFragment;
import com.timeline.myapp.ui.fragment.VideoChannelUserListFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "video_tag";
    private String channel = "";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b  = getArguments();
        channel = ((HashMap<String,String>)b.getSerializable(Constants.CONFIG_PARAM)).get(Constants.CHANNEL);
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_CHANNLE_URL, start,channel);
    }
    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        if(Constants.VIDEO_USER_CHANNEL.equals(vo.actionUrl)){
            VideoChannelUserListFragment.startFragment(getActivity(),vo);
        }else{
            VideoChannelListFragment.startFragment(getActivity(), vo);
        }
    }
    @Override
    protected  boolean getShowParam(){
        return true;
    }
    @Override
    public int getSpanCount() {
        return 2;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }



    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if(data.pageNum==3 || data.pageNum==8){
            AdsManager.getInstans().showNative(getActivity(),this);
        }
    }
}
