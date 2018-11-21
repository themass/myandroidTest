package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.qq.sexfree.R;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.myapp.bean.vo.ImgItemsVo;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.HistoryUtil;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.ui.base.CommonFragmentActivity;


/**
 * Created by themass on 2016/8/12.
 */
public class ImgChannelImgListFragment extends RecommendFragment {
    private static final String IMG_TAG = "img_tag";
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgChannelImgListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.img);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        StaticDataUtil.add(Constants.IMG_CHANNEL, vo);
        context.startActivity(intent);
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_IMG_ITEMS_IMG_URL, start, vo.param,keyword);
    }

    @Override
    public String getNetTag() {
        return IMG_TAG;
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
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_CHANNEL, RecommendVo.class);
        if(vo==null){
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.IMG_CHANNEL);
    }
    @Override
    protected boolean showSearchView(){
        return true;
    }
    @Override
    public boolean getShowParam(){
        return true;
    }
    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo revo = infoListVo.voList.get(position);
        if(!checkUserLevel(revo.type)){
            return;
        }
        ImgItemsVo imgItemsVo = new ImgItemsVo();
        imgItemsVo.name = revo.title;
        imgItemsVo.url = revo.actionUrl;
        ImgItemFragment.startFragment(getActivity(), imgItemsVo);
        HistoryUtil.addHistory(getActivity(), imgItemsVo.url);
        mSearchView.clearFocus();
    }
    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if(data.pageNum==3){
            AdsManager.getInstans().showNative(getActivity(),this);
        }
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(IMG_TAG);
        super.onDestroyView();

    }
}
