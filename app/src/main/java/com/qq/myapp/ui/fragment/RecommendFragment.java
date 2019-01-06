package com.qq.myapp.ui.fragment;


import android.content.Context;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.qq.common.helper.OnStartDragListener;
import com.qq.common.helper.SimpleItemTouchHelperCallback;
import com.qq.common.ui.view.RecycleViewDivider;
import com.qq.common.util.CollectionUtils;
import com.qq.common.util.EventBusUtil;
import com.qq.common.util.ToastUtil;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.ads.base.GdtNativeManager;
import com.qq.yewu.ads.base.NativeAdsReadyListener;
import com.qq.yewu.um.MobAgent;
import com.qq.myapp.adapter.IndexRecommendAdapter;
import com.qq.myapp.adapter.base.BasePhotoFlowRecycleViewAdapter;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.config.ConfigActionEvent;
import com.qq.myapp.ui.base.features.BasePullLoadbleFragment;
import com.qq.myapp.ui.user.LoginActivity;
import com.qq.fq2.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2015/9/1.
 */
public abstract class RecommendFragment extends BasePullLoadbleFragment<RecommendVo> implements BasePhotoFlowRecycleViewAdapter.OnRecyclerViewItemClickListener, OnStartDragListener, IndexRecommendAdapter.OnEditClickListener, NativeAdsReadyListener,  GdtNativeManager.OnLoadListener {
    protected ItemTouchHelper mItemTouchHelper;
    protected IndexRecommendAdapter adapter;
    public GdtNativeManager gdtNativeManager = new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_SIX,Constants.ITEMS_PER_AD_SIX);
    @Override
    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        adapter.notifyDataSetChanged();
    }
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
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoListVo.voList, layoutManager, this, this, this, getShowEdit(),gdtNativeManager);
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
        if (vo.dataType == RecommendVo.dataType_ADS) {
            ((NativeAdInfo) (vo.extra)).onClick(v,(int)v.getX(),(int)v.getY());
            return true;
        }
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
    public boolean onAdRecieved(List<NativeAdInfo> data) {
        if (!CollectionUtils.isEmpty(data)) {
            List<RecommendVo> list = new ArrayList<>();
            for (NativeAdInfo nativeAdInfo : data) {
                RecommendVo vo = new RecommendVo();
                vo.desc = nativeAdInfo.getDescription();
                vo.img = nativeAdInfo.getIconUrl();
                vo.title = nativeAdInfo.getTitle();
                vo.extra = nativeAdInfo;
                nativeAdInfo.onDisplay(new View(getActivity()));
                vo.dataType = RecommendVo.dataType_ADS;
                if (nativeAdInfo.getImageWidth() != 0)
                    vo.rate = nativeAdInfo.getImageHeight() / nativeAdInfo.getImageWidth();
                else {
                    vo.rate = 1f;
                }
                vo.showType = Constants.ShowType.Blur;
                list.add(vo);
            }
            addData(list);
            pullView.notifyDataSetChanged();
        }
        return true;
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
