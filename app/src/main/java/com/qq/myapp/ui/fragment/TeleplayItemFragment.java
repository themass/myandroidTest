package com.qq.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.qq.kb.R;
import com.sspacee.yewu.ads.base.AdsContext;

import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.HistoryUtil;
import com.qq.myapp.data.StaticDataUtil;
import com.qq.myapp.data.VideoUtil;
import com.qq.myapp.ui.base.CommonFragmentActivity;
import com.qq.myapp.ui.sound.VideoShowActivity;
import com.qq.myapp.ui.sound.VitamioVideoPlayActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class TeleplayItemFragment extends RecommendFragment {
    private static final String IMG_TAG = "tv_channel_tag";
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TeleplayItemFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.tv);
        StaticDataUtil.add(Constants.TV_CHANNEL, vo);
        context.startActivity(intent);
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_TV_ITEM_URL, start, vo.param);
    }

    @Override
    public String getNetTag() {
        return IMG_TAG;
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
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.TV_CHANNEL, RecommendVo.class);
        if(vo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.TV_CHANNEL);
    }
    @Override
    public boolean getShowParam(){
        return true;
    }
    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo revo = infoListVo.voList.get(position);
        HistoryUtil.addHistory(getActivity(), revo.actionUrl);
        if(Constants.VIDEO_TYPE_NORMAL.equalsIgnoreCase((String)revo.extra)){
            if(VideoUtil.isVitamioExt(vo.actionUrl)){
                startActivity(VitamioVideoPlayActivity.class, revo);
            }else {
                startActivity(VideoShowActivity.class, revo);
            }
        }else{
            super.onCustomerItemClick(v,position);
        }
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(IMG_TAG);
        super.onDestroyView();

    }
}
