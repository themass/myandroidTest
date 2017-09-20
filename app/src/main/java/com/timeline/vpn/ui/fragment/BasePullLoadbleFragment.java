package com.timeline.vpn.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.view.DividerItemDecoration;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.ui.base.LoadableFragment;

import butterknife.BindView;


/**
 * Created by themass on 2016/8/12.
 */
public abstract class BasePullLoadbleFragment<T> extends LoadableFragment<InfoListVo<T>> implements MyPullView.OnRefreshListener, BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<T> {
    @Nullable
    @BindView(R.id.my_pullview)
    MyPullView pullView;
    InfoListVo<T> infoListVo = new InfoListVo<T>();
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        initPullView();
    }
    protected void initPullView(){
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pullView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        pullView.setAdapter(getAdapter());
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        pullView.setListener(this);
    }
    @Override
    protected void onDataLoaded(InfoListVo<T> data) {
        if (pullView != null) {
            if (data != null) {
                if (pullView.isLoadMore()) { //上拉加载
                    loadMoreData(data);
                } else { //下拉刷新 或者首次
                    freshData(data);
                }
                infoListVo.copy(data);
                initSort();
                sortData();
                data.voList.clear();
                data.voList.addAll(infoListVo.voList);
                setData(data);
                LogUtil.i("mData size=" + infoListVo.voList.size());
            }
            pullView.notifyDataSetChanged();
        }
    }
    protected void sortData() {}

    protected void initSort() {}
    protected  void loadMoreData(InfoListVo<T> data){
        infoListVo.voList.addAll(data.voList);
    }
    protected  void freshData(InfoListVo<T> data){
        infoListVo.voList.clear();
        infoListVo.voList.addAll(data.voList);
    }
    @Override
    public void onItemClick(View view, T data, int postion) {
        pullView.notifyDataSetChanged();
    }

    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == MyPullView.OnRefreshListener.FRESH)
            infoListVo.pageNum = 0;
        startQuery(false);
    }

    @Override
    public boolean needLoad() {
        return infoListVo.hasMore;
    }

    protected abstract BaseRecyclerViewAdapter getAdapter();

}
