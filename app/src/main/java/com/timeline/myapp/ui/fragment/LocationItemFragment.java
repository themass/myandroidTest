package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.GsonUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.timeline.myapp.adapter.LocationItemAdapter;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.LocationVo;
import com.timeline.myapp.bean.vo.VipLocationVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.LocationUtil;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.data.config.LocationChooseEvent;
import com.timeline.myapp.data.config.PingEvent;
import com.timeline.myapp.ui.base.features.BasePullLoadbleFragment;
import com.timeline.myapp.ui.user.SettingActivity;
import com.timeline.vpn.R;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationItemFragment extends BasePullLoadbleFragment<LocationVo> {
    public static final String LOCATION_TAG = "location_tag";
    LocationItemAdapter adapter;
    VipLocationVo vipLocationVo;
    int index;

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
        LogUtil.i("location args="+getSerializable().toString());
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
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
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_LOCATION_URL,vipLocationVo.type), LocationVo.class, LOCATION_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(LOCATION_TAG);
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
        if(data.type!=Constants.UserLevel.LEVEL_FREE &&UserLoginUtil.getUserCache()==null){
            if(data.type==Constants.UserLevel.LEVEL_VIP){
                ToastUtil.showShort(R.string.login_song);
                return;
            }else  if(data.type==Constants.UserLevel.LEVEL_VIP2){
                ToastUtil.showShort(R.string.login_song_qq);
                SystemUtils.copy(getActivity(), StaticDataUtil.get(Constants.QQ,String.class));
                ToastUtil.showShort(getString(R.string.menu_copy_qq)+" : "+StaticDataUtil.get(Constants.QQ,String.class));
                return;
            }
        }
        PreferenceUtils.setPrefBoolean(getActivity(), Constants.LOCATION_FLAG, true);
        LocationUtil.setLocation(getActivity(), data);
        EventBusUtil.getEventBus().postSticky(new LocationChooseEvent());
        getActivity().finish();
        pullView.notifyDataSetChanged();
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new LocationItemAdapter(getActivity(),pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
}
