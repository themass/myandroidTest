package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.sound.VideoShowActivity;

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
        return 2;
    }
    @Override
    public boolean getShowEdit() {
        return false;
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        vo.urlToken = revo.urlToken;
        if(Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String)vo.extra)){
            startActivity(VideoShowActivity.class, vo);
        }else{
            super.onItemClick(v,position);
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
