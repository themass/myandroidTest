package com.timeline.vpn.ui.maintab;


import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.data.config.ConfigActionEvent;
import com.timeline.vpn.ui.base.LoadableTabFragment;
import com.timeline.vpn.ui.view.MyPullView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;

/**
 * Created by themass on 2015/9/1.
 */
public class TabVipContentFragment extends LoadableTabFragment<InfoListVo<RecommendVo>> implements  IndexRecommendAdapter.ItemClickListener, MyPullView.OnRefreshListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "index_tag";
    @Bind(R.id.my_pullview)
    MyPullView pullView;
    private IndexRecommendAdapter adapter;
    private Handler handler = new Handler();
    private LinearInterpolator lir = null;
    private BaseService indexService;
    private boolean isFirst = false;
    private InfoListVo<RecommendVo> infoVo = new InfoListVo<>();

    @Override
    protected int getTabHeaderViewId() {
        return TabBaseFragment.NULL_VIEW;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirst = true;
        infoVo.voList = new ArrayList<>();
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent) {
        inflater.inflate(R.layout.layout_recommd, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
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

    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        LogUtil.i("loadData:" + mData);
        return indexService.getInfoListData(Constants.getVIP_URL(infoVo.pageNum), RecommendVo.class, INDEX_TAG);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoVo.voList, this, layoutManager);
        lir = new LinearInterpolator();
        indexService = new BaseService();
        indexService.setup(getActivity());
        pullView.setListener(this);
        pullView.setAdapter(adapter);
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
    public void onResume() {
        super.onResume();
        if (isFirst) {
            isFirst = false;
            next();
        }
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoVo.voList.get(position);
        Map<String, Object> param = new HashMap<>();
        param.put(Constants.ADS_SHOW_CONFIG, vo.adsShow);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), vo.actionUrl,vo.title, param));
        MobAgent.onEventRecommond(getActivity(),vo.title);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        indexService.cancelRequest(INDEX_TAG);
    }
}
