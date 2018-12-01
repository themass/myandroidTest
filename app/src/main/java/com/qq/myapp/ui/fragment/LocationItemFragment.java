package com.qq.myapp.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.qq.common.util.CollectionUtils;
import com.qq.common.util.EventBusUtil;
import com.qq.common.util.GsonUtils;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PreferenceUtils;
import com.qq.common.util.ToastUtil;
import com.qq.myapp.adapter.LocationItemAdapter;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.bean.vo.LocationVo;
import com.qq.myapp.bean.vo.VipLocationVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.LocationUtil;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.config.LocationChooseEvent;
import com.qq.myapp.data.config.PingEvent;
import com.qq.myapp.ui.base.features.BasePullLoadbleFragment;
import com.qq.fq2.R;
import com.qq.myapp.ui.user.LoginActivity;

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
    private static int fon=1;
    private static int foff=0;
    private int status = foff;

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
//        if(!CollectionUtils.isEmpty(vipLocationVo.list)) {
//            InfoListVo<LocationVo> data = new InfoListVo();
//            data.hasMore = false;
//            data.pageNum = 1;
//            data.total = vipLocationVo.list.size();
//            data.voList = vipLocationVo.list;
//            return data;
//        }
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_LOCATION_URL,vipLocationVo.type), LocationVo.class, LOCATION_TAG);
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
        ToastUtil.showShort(data.name+"--"+data.ename);
//        getActivity().finish();
//        pullView.notifyDataSetChanged();
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new LocationItemAdapter(getActivity(),pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
}
