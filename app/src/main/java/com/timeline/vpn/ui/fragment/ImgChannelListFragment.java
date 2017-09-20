package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.ImgChannelListItemsViewAdapter;
import com.timeline.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.vpn.bean.vo.ImgItemsVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.HistoryUtil;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgChannelListFragment extends BasePullLoadbleFragment<ImgItemsVo>{
    private static final String TEXT_TAG = "text_tag";
    private ImgChannelListItemsViewAdapter adapter;
    private RecommendVo vo;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.img);
        StaticDataUtil.add(Constants.IMG_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS, true);
        context.startActivity(intent);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_CHANNEL, RecommendVo.class);
        StaticDataUtil.del(Constants.IMG_CHANNEL);
    }
    protected  BaseRecyclerViewAdapter getAdapter(){
        adapter = new ImgChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
    @Override
    protected InfoListVo<ImgItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getPageWithParam_URL(Constants.API_IMG_ITEMS_URL, infoListVo.pageNum, vo.param), ImgItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, ImgItemsVo data, int postion) {
        super.onItemClick(view,data,postion);
        ImgGalleryFragment.startFragment(getActivity(), data);
        HistoryUtil.addHistory(getActivity(), data.url);
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();

    }
}
