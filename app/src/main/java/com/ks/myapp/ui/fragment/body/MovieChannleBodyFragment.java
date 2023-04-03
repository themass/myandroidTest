package com.ks.myapp.ui.fragment.body;


import android.os.Bundle;
import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.sspacee.yewu.ads.base.AdsManager;
import com.ks.myapp.bean.vo.InfoListVo;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.ui.fragment.RecommendFragment;
import com.ks.myapp.ui.fragment.VideoChannelListFragment;
import com.ks.myapp.ui.fragment.VideoChannelUserListFragment;

import java.util.HashMap;

/**
 * Created by themass on 2015/9/1.
 */
public class MovieChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "movie_tag";
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
       else {
            if(Constants.VIDEO_USER_CHANNEL.equals(vo.param)){
                VideoChannelUserListFragment.startFragment(getActivity(),vo);
            }else{
                VideoChannelListFragment.startFragment(getActivity(), vo);
            }
        }
    }
    @Override
    protected  boolean getShowParam(){
        return true;
    }
    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if(data.pageNum==2){
            AdsManager.getInstans().showNative(getActivity(),this);
        }
    }
}
