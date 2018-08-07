package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.ui.base.CommonFragmentActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class VideoChannelUserListFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_user_tag";
    RecommendVo vo;
    String token;
    Thread t ;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, VideoChannelUserListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.PARAM,vo);
        context.startActivity(intent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = (RecommendVo)getArguments().getSerializable(CommonFragmentActivity.PARAM);
        LogUtil.i(vo.title);
        if("蜜蜂资源".equals(vo.title)){
            t = new Thread(new Runnable() {
                @Override
                public void run() {
                    int count = 0;
                    while (count<3) {
                        try {
                            LogUtil.i("请求蜜蜂token"+vo.baseurl +"/getToken.php");
                            token = indexService.getStringData(vo.baseurl +"/getToken.php", "token");
                            LogUtil.i("蜜蜂token="+token);
                            break;
                        } catch (Exception e) {
                            LogUtil.e(e);
                            count++;
                            continue;
                        }
                    }
                }
            });
            t.start();
        }
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_USER_URL, vo.param,start);
    }


    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if(data.pageNum==2){
            AdsManager.getInstans().showNative(getActivity(),this);
        }
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
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        vo.urlToken = token;
        VideoChannelUserItemsListFragment.startFragment(getActivity(),vo);
    }
    @Override
    protected  boolean getShowParam(){
        return true;
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(VIDEO_TAG);
        super.onDestroyView();
        if(t!=null && t.isAlive()){
            t.stop();
            t = null;
        }
    }
}
