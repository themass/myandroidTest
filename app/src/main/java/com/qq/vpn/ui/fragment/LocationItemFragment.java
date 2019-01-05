package com.qq.vpn.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kyview.natives.NativeAdInfo;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ads.base.GdtNativeManager;
import com.qq.ads.base.NativeAdsReadyListener;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.ext.util.CollectionUtils;
import com.qq.ext.util.EventBusUtil;
import com.qq.ext.util.GsonUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PreferenceUtils;
import com.qq.vpn.adapter.LocationItemAdapter;
import com.qq.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.qq.vpn.domain.res.InfoListVo;
import com.qq.vpn.domain.res.LocationVo;
import com.qq.vpn.domain.res.VipLocationVo;
import com.qq.Constants;
import com.qq.vpn.support.LocationUtil;
import com.qq.vpn.support.config.LocationChooseEvent;
import com.qq.vpn.support.config.PingEvent;
import com.qq.vpn.ui.base.fragment.BasePullLoadbleFragment;
import com.qq.network.R;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationItemFragment extends BasePullLoadbleFragment<LocationVo>implements  GdtNativeManager.OnLoadListener  {
    public static final String LOCATION_TAG = "location_tag";
    LocationItemAdapter adapter;
    VipLocationVo vipLocationVo;
    int index;
    public GdtNativeManager gdtNativeManager = new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_SIX,Constants.ITEMS_PER_AD_SIX);
    public static int getFragmentTitle() {
        return R.string.location_select;
    }
    @Override
    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        adapter.notifyDataSetChanged();
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.base_mypage_view, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        vipLocationVo = (VipLocationVo)getSerializable();
        index = vipLocationVo.type;
        LogUtil.i("location args="+getSerializable().toString());
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
        gdtNativeManager.loadData(getActivity());

    }
    @Override
    protected InfoListVo<LocationVo> loadData(Context context) throws Exception {
        if(!CollectionUtils.isEmpty(vipLocationVo.list)) {
            InfoListVo<LocationVo> data = new InfoListVo();
            data.hasMore = false;
            data.pageNum = 1;
            data.total = vipLocationVo.list.size();
            data.voList = vipLocationVo.list;
            return data;
        }
        return api.getInfoListData(Constants.getUrlWithParam(Constants.API_LOCATION_URL,vipLocationVo.type), LocationVo.class, LOCATION_TAG);
    }

    @Override
    public void onDestroyView() {
        api.cancelRequest(LOCATION_TAG);
        super.onDestroyView();
        EventBusUtil.getEventBus().unregister(this);
    }
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void setPing(PingEvent event) {
        adapter.setNeedPing(true);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(View view, LocationVo data, int postion) {
        LogUtil.i(postion + "---" + GsonUtils.getInstance().toJson(data));
        PreferenceUtils.setPrefBoolean(getActivity(), Constants.LOCATION_FLAG, true);
        LocationUtil.setLocation(getActivity(), data);
        EventBusUtil.getEventBus().postSticky(new LocationChooseEvent());
        getActivity().finish();
        pullView.notifyDataSetChanged();
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new LocationItemAdapter(getActivity(),pullView.getRecyclerView(), infoListVo.voList, this,gdtNativeManager);
        return adapter;
    }
}
