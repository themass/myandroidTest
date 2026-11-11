package com.openapi.myapp.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.openapi.common.util.CollectionUtils;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.myapp.adapter.LocationItemAdapter;
import com.openapi.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.myapp.bean.vo.InfoListVo;
import com.openapi.myapp.data.LocationFilter;
import com.openapi.myapp.bean.vo.LocationVo;
import com.openapi.myapp.bean.vo.VipLocationVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.LocationUtil;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.data.config.LocationChooseEvent;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.free1.R;
import com.openapi.myapp.ui.user.LoginActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationItemFragment extends BasePullLoadbleFragment<LocationVo> {
    public static final String LOCATION_TAG = "location_tag";
    LocationItemAdapter adapter;
    VipLocationVo vipLocationVo;
    int index;
    private static int fon=1;
    private static int foff=0;
    private int status = foff;
    private List<LocationVo> nativeData = new ArrayList<>();
    public static int getFragmentTitle() {
        return R.string.location_choose_title;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        vipLocationVo = (VipLocationVo)getSerializable();
        index = vipLocationVo.type;
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
//        AdsManager.getInstans().showNative(getActivity(),this, AdsContext.getIndex(index));

    }
//    public boolean onAdRecieved(List<NativeAdInfo> data){
//        nativeData.clear();
//        for(NativeAdInfo item:data){
//            LocationVo vo = LocationVo.fromNative(item);
//            item.onDisplay(new View(
//                    getActivity()));
//            nativeData.add(vo);
//        }
//        adapter.notifyDataSetChanged();
//        return true;
//    }
    @Override
    protected InfoListVo<LocationVo> loadData(Context context) throws Exception {
        if(!pullView.isRefreshing() && !CollectionUtils.isEmpty(vipLocationVo.list) ) {
            return rebuildFromLocal();
        }
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_LOCATION_URL,vipLocationVo.type), LocationVo.class, LOCATION_TAG);
    }

    /** “Best / Auto” 占位节点的 id，不会与真实节点冲突。 */
    public static final int BEST_ID = -1;

    private InfoListVo<LocationVo> rebuildFromLocal() {
        InfoListVo<LocationVo> data = new InfoListVo<>();
        data.hasMore = false;
        data.pageNum = 1;
        // 先按延迟排，再把收藏和最近连接的提到前面（Best 仍然固定第一行）
        List<LocationVo> sorted = com.openapi.myapp.data.NodeShortcuts.arrange(
                LocationFilter.sortByPing(vipLocationVo.list),
                com.openapi.myapp.data.NodeShortcuts.favorites(getActivity()),
                com.openapi.myapp.data.NodeShortcuts.recent(getActivity()));
        sorted.add(0, bestPlaceholder());
        data.total = sorted.size();
        data.voList = sorted;
        return data;
    }

    private LocationVo bestPlaceholder() {
        LocationVo best = new LocationVo();
        best.id = BEST_ID;
        best.ename = "Best / Auto";
        best.name = "Best route";
        best.type = vipLocationVo.type;
        best.category = "auto";
        return best;
    }


    @Override
    public void onResume() {
        super.onResume();
        status = fon;
    }

    @Override
    public void onPause() {
        super.onPause();
        status = foff;
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(LOCATION_TAG);
        super.onDestroyView();
        EventBusUtil.getEventBus().unregister(this);
    }
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void setPing(PingEvent event) {
        if(status==fon) {
            adapter.setNeedPing(true);
            adapter.notifyDataSetChanged();
        }
    }
    @Subscribe(threadMode= ThreadMode.MAIN)
    public void setLocation(LocationChooseEvent event) {
        adapter.initSelectLocation();
        adapter.notifyDataSetChanged();
    }
    @Override
    public void onItemClick(View view, LocationVo data, int postion) {
        if (data.id == BEST_ID) {
            LocationVo best = LocationFilter.bestOf(vipLocationVo.list);
            if (best == null) {
                ToastUtil.showShort(R.string.ping_wait);
                EventBusUtil.getEventBus().post(new PingEvent());
                return;
            }
            // Best 只是挑选器，选出的仍是真实节点，后面的 VIP 检查与保存流程完全不变。
            data = best;
        }
        if(UserLoginUtil.getUserCache()==null){
            if(vipLocationVo.type!=0){
                ToastUtil.showRedLong(vipLocationVo.desc);
                startActivity(LoginActivity.class);
                return;
            }
        }else if(UserLoginUtil.getUserCache().level<vipLocationVo.type){
            ToastUtil.showRedLong(vipLocationVo.desc);
            return;
        }
        LogUtil.i(postion + "---" + GsonUtils.getInstance().toJson(data));
        PreferenceUtils.setPrefBoolean(getActivity(), Constants.LOCATION_FLAG, true);
        LocationUtil.setLocation(getActivity(), data);
        EventBusUtil.getEventBus().postSticky(new LocationChooseEvent());
        ToastUtil.showShort(data.ename);
//        getActivity().finish();
//        pullView.notifyDataSetChanged();
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new LocationItemAdapter(getActivity(),pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setFavoriteListener(new LocationItemAdapter.FavoriteListener() {
            @Override
            public void onFavoriteChanged() {
                com.openapi.myapp.data.UsageReport.feature(getActivity(),
                        com.openapi.myapp.data.UsageReport.FEATURE_FAVORITE);
                // 收藏变了：就地重排，不重新请求网络
                if (vipLocationVo.list != null && !vipLocationVo.list.isEmpty()) {
                    InfoListVo<LocationVo> data = rebuildFromLocal();
                    infoListVo.voList.clear();
                    infoListVo.voList.addAll(data.voList);
                    adapter.notifyDataSetChanged();
                }
            }
        });
        return adapter;
    }
}
