package com.timeline.vpn.ui.fragment;


import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import com.sspacee.common.helper.OnStartDragListener;
import com.sspacee.common.helper.SimpleItemTouchHelperCallback;
import com.sspacee.common.ui.view.RecycleViewDivider;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.adapter.BasePhotoFlowRecycleViewAdapter;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.config.ConfigActionEvent;

import org.strongswan.android.logic.VpnStateService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by themass on 2015/9/1.
 */
public abstract class RecommendFragment extends BasePullLoadbleFragment<RecommendVo> implements BasePhotoFlowRecycleViewAdapter.OnRecyclerViewItemClickListener, OnStartDragListener, IndexRecommendAdapter.OnEditClickListener {
    public VpnStateService mService;
    public final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((VpnStateService.LocalBinder) service).getService();
        }
    };
    protected ItemTouchHelper mItemTouchHelper;
    protected IndexRecommendAdapter adapter;
    protected BaseService indexService;

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

    @Override
    protected void initPullView() {
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL);
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoListVo.voList, layoutManager, this, this, this, getShowEdit());
        adapter.setNeedShimmer(getNeedShimmer());
        pullView.setListener(this);
        pullView.setAdapter(adapter);
        pullView.getRecyclerView().addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));

        indexService = new BaseService();
        indexService.setup(getActivity());
        switchFlag(false);
        getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapter, getCanMove(), false);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(pullView.getRecyclerView());
    }
    //initPullView 方法被复写，这个函数无用了
    protected BaseRecyclerViewAdapter getAdapter(){
        return null;
    }
    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        Map<String, Object> param = new HashMap<>();
        param.put(Constants.ADS_SHOW_CONFIG, vo.adsShow);
        param.put(Constants.ADS_POP_SHOW_CONFIG, vo.adsPopShow);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), vo.actionUrl, vo.title, param));
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }

    @Override
    public void onLongItemClick(View view, int position) {

    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(getNetTag());
        getActivity().unbindService(mServiceConnection);
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
