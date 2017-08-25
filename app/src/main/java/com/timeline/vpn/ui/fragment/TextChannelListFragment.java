package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.view.DividerItemDecoration;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.ChannelListItemsViewAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.TextItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/12.
 */
public class TextChannelListFragment extends LoadableFragment<InfoListVo<TextItemsVo>> implements  MyPullView.OnRefreshListener,BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<TextItemsVo> {
    private static final String TEXT_TAG = "text_tag";
    @Nullable
    @Bind(R.id.my_pullview)
    MyPullView pullView;
    private BaseService indexService;
    private ChannelListItemsViewAdapter adapter;
    private InfoListVo<TextItemsVo> infoVo = new InfoListVo<>();
    private RecommendVo vo;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
        }
    };
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.text);
        StaticDataUtil.add(Constants.TEXT_CHANNEL,vo);
        intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vo =  StaticDataUtil.get(Constants.TEXT_CHANNEL,RecommendVo.class);
        StaticDataUtil.del(Constants.TEXT_CHANNEL);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState){
        super.setupViews(view,savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        adapter = new ChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoVo.voList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pullView.setLayoutManager(layoutManager);
//        rvLocation.addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        pullView.setAdapter(adapter);
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        pullView.setListener(this);
        if(!UserLoginUtil.isVIP2())
            AdsAdview.interstitialAds(getActivity(), mHandler);
    }
    @Override
    protected void onDataLoaded(InfoListVo<TextItemsVo> data) {
        if(pullView!=null) {
            if (data != null) {
                if (pullView.isLoadMore()) { //上拉加载
                    infoVo.voList.addAll(data.voList);
                } else { //下拉刷新 或者首次
                    infoVo.voList.clear();
                    infoVo.voList.addAll(data.voList);
                }
                infoVo.copy(data);
                data.voList.clear();
                data.voList.addAll(infoVo.voList);
                setData(data);
                LogUtil.i("mData size=" + infoVo.voList.size());
            }
            pullView.notifyDataSetChanged();
        }
    }
    @Override
    protected InfoListVo<TextItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getPageWithParam_URL(Constants.API_TEXT_ITEMS_URL,infoVo.pageNum,vo.param), TextItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, TextItemsVo data, int postion) {
        adapter.notifyDataSetChanged();
        if(StringUtils.hasText(data.fileUrl)){
            TextItemsStrFragment.startFragment(getActivity(),data);
        }else {
            TextItemsFragment.startFragment(getActivity(),data);
        }
    }
    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == MyPullView.OnRefreshListener.FRESH)
            infoVo.pageNum = 0;
        startQuery(false);
    }

    @Override
    public boolean needLoad() {
        return infoVo.hasMore;
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        indexService.cancelRequest(TEXT_TAG);
    }
}
