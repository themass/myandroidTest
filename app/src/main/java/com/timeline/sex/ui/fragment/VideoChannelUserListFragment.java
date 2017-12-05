package com.timeline.sex.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.ui.base.CommonFragmentActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class VideoChannelUserListFragment extends RecommendFragment {
    private static final String VIDEO_TAG = "video_user_tag";
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, VideoChannelUserListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.title);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        context.startActivity(intent);
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_VIDEO_USER_URL, start);
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
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
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

    }
}
