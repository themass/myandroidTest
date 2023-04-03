package com.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.ks.myapp.bean.vo.InfoListVo;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.StaticDataUtil;
import com.ks.myapp.ui.base.CommonFragmentActivity;
import com.ks.myapp.ui.sound.VideoShowActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class VideoChannelUserItemsListFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_user_tag";
    private RecommendVo revo;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, VideoChannelUserItemsListFragment.class);
        StaticDataUtil.add(Constants.VIDEO_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);

        context.startActivity(intent);
    }
    @Override
    public  boolean showSearchView(){
        return true;
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_USER_ITEM_URL,start,revo.actionUrl,keyword);
    }
    @Override
    public String getNetTag() {
        return VIDEO_TAG;
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
    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        vo.urlToken = revo.urlToken;
        if(Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String)vo.extra)){
            startActivity(VideoShowActivity.class, vo);
//            startActivity(VitamioVideoPlayActivity.class, vo);
        }else{
            super.onCustomerItemClick(v,position);
        }
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        revo = StaticDataUtil.get(Constants.VIDEO_CHANNEL, RecommendVo.class);
        if(revo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.VIDEO_CHANNEL);
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(VIDEO_TAG);
        super.onDestroyView();

    }
}
