package com.qq.vpn.ui.fragment;


import android.content.Context;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;

import com.qq.MobAgent;
import com.qq.ext.util.EventBusUtil;
import com.qq.ext.view.view.RecycleViewDivider;
import com.qq.vpn.adapter.RecommendAdapter;
import com.qq.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.qq.vpn.adapter.base.PhotoFlowRecycleViewAdapter;
import com.qq.vpn.domain.res.InfoListVo;
import com.qq.vpn.domain.res.RecommendVo;
import com.qq.vpn.support.config.ConfigActionEvent;
import com.qq.vpn.ui.base.fragment.BasePullLoadbleFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2015/9/1.
 */
public abstract class RecommendFragment extends BasePullLoadbleFragment<RecommendVo> implements PhotoFlowRecycleViewAdapter.OnRecyclerViewItemClickListener {
    protected RecommendAdapter adapter;

    public void addData(List<RecommendVo> data) {
        infoListVo.voList.addAll(data);
        initSort();
        sortData();
        mData.voList.addAll(data);
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        return api.getInfoListData(getUrl(infoListVo.pageNum), RecommendVo.class, getNetTag());
    }


    protected  boolean getShowParam(){
        return true;
    }
    @Override
    protected void initPullView() {
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL);
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new RecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoListVo.voList, layoutManager, this);
        adapter.setShowParam(getShowParam());
        adapter.setNeedShimmer(getNeedShimmer());
        pullView.setListener(this);
        pullView.setAdapter(adapter);
        pullView.getRecyclerView().addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));
    }
    //initPullView 方法被复写，这个函数无用了
    protected BaseRecyclerViewAdapter getAdapter(){
        return null;
    }
    public void onCustomerItemClick(View v, int position){
        RecommendVo vo = infoListVo.voList.get(position);
        Map<String, Object> param = new HashMap<>();
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), vo.actionUrl, vo.title, param));
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        onCustomerItemClick(v,position);
    }

    @Override
    public void onLongItemClick(View view, int position) {

    }

    @Override
    public void onDestroyView() {
        api.cancelRequest(getNetTag());
        super.onDestroyView();
    }

    protected boolean getNeedShimmer() {
        return true;
    }

    public abstract String getUrl(int start);

    public abstract String getNetTag();

    public abstract int getSpanCount();


}
