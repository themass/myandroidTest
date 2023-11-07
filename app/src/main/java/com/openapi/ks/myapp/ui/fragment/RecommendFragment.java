package com.openapi.ks.myapp.ui.fragment;


import android.content.Context;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.view.View;

import com.openapi.commons.common.helper.OnStartDragListener;
import com.openapi.commons.common.helper.SimpleItemTouchHelperCallback;
import com.openapi.commons.common.ui.view.RecycleViewDivider;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.IndexRecommendAdapter;
import com.openapi.ks.myapp.adapter.base.BasePhotoFlowRecycleViewAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ConfigActionEvent;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.user.LoginActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by openapi on 2015/9/1.
 */
public abstract class RecommendFragment extends BasePullLoadbleFragment<RecommendVo> implements BasePhotoFlowRecycleViewAdapter.OnRecyclerViewItemClickListener, OnStartDragListener, IndexRecommendAdapter.OnEditClickListener {
    protected ItemTouchHelper mItemTouchHelper;
    protected IndexRecommendAdapter adapter;

    public void addData(List<RecommendVo> data) {
        infoListVo.voList.addAll(data);
        initSort();
        sortData();
        mData.voList.addAll(data);
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(getUrl(infoListVo.pageNum), RecommendVo.class, getNetTag());
    }

    protected void switchFlag(boolean flag) {
        adapter.switchFlag(flag);
    }

    protected boolean getSwitchFlag() {
        return adapter.getSwitchFlag();
    }
    protected  boolean getShowParam(){
        return true;
    }
    @Override
    protected void initPullView() {
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL);
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoListVo.voList, layoutManager, this, this, this, getShowEdit());
        adapter.setShowParam(getShowParam());
        adapter.setNeedShimmer(getNeedShimmer());
        pullView.setListener(this);
        pullView.setAdapter(adapter);
        pullView.getRecyclerView().addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));

        switchFlag(false);
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapter, getCanMove(), false);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(pullView.getRecyclerView());
    }
    //initPullView 方法被复写，这个函数无用了
    protected BaseRecyclerViewAdapter getAdapter(){
        return null;
    }
    protected boolean checkUserLevel(int type){
        if(type>0){
            if(UserLoginUtil.getUserCache()==null){
                startActivity(LoginActivity.class);
                return false;
            }
            if(UserLoginUtil.getUserCache().level<type){
                ToastUtil.showShort(R.string.vip_tips);
                return false;
            }
        }
        return true;
    }
    public boolean adClick(View v,RecommendVo vo){
        return false;
    }
    public void onCustomerItemClick(View v, int position){
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        Map<String, Object> param = new HashMap<>();
        param.put(Constants.ADS_SHOW_CONFIG, vo.adsShow);
        param.put(Constants.ADS_POP_SHOW_CONFIG, vo.adsPopShow);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), vo.actionUrl, vo.title, param));
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!adClick(v,vo)){
            onCustomerItemClick(v,position);
        }
    }
    @Override
    public void onLongItemClick(View view, int position) {

    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(getNetTag());
        super.onDestroyView();
    }

    protected boolean getNeedShimmer() {
        return true;
    }

    public abstract String getUrl(int start);

    public abstract String getNetTag();

    public abstract int getSpanCount();

    public abstract boolean getShowEdit();

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
    }

    @Override
    public void onItemMove(Object o1, Object o2) {
    }

    @Override
    public void onItemRemove(Object o) {
    }

    public boolean getCanMove() {
        return false;
    }

    @Override
    public void onEditClick(View view, int postion) {

    }
}