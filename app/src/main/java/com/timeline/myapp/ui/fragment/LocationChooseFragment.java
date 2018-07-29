package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.GsonUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.myapp.adapter.LocationViewAdapter;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.LocationVo;
import com.timeline.myapp.bean.vo.UserInfoVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.LocationUtil;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.data.config.LocationChooseEvent;
import com.timeline.myapp.data.sort.LocSortFactor;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.base.features.BasePullLoadbleFragment;
import com.timeline.myapp.ui.user.LoginActivity;
import com.timeline.vpn.R;

import java.util.Collections;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class LocationChooseFragment extends BasePullLoadbleFragment<LocationVo>{
    private static final String LOCATION_TAG = "location_tag";
    @Nullable
    @BindView(R.id.loc_btn_type)
    Button tvType;
    @Nullable
    @BindView(R.id.loc_btn_country)
    Button tvCountry;
    @Nullable
    @BindView(R.id.loc_btn_features)
    Button tvFeature;
    private int typeIndex = 0;
    private int countryIndex = 0;
    private Boolean needFinish = true;
    LocationViewAdapter  adapter;
    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, LocationChooseFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static int getFragmentTitle() {
        return R.string.location_choose_title;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_location_choose_fragment, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        needFinish=(Boolean) getSerializable();
        setUp();
    }
    @Override
    protected InfoListVo<LocationVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrl(Constants.API_LOCATION_URL), LocationVo.class, LOCATION_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(LOCATION_TAG);
        super.onDestroyView();
    }

    private void setUp() {
        tvType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sort(Constants.sort_type[(++typeIndex) % 2]);
            }
        });
        tvCountry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sort(Constants.sort_country[(++countryIndex) % 2]);
            }
        });
        tvFeature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                sort(Constants.sort_fea[(++feaIndex) % 2]);
                adapter.setNeedPing(true);
                adapter.notifyDataSetChanged();
            }
        });

    }

    private void sort(String sortBy) {
        Collections.sort(infoListVo.voList, new LocSortFactor(sortBy));
        adapter.notifyDataSetChanged();
    }
    @Override
    public void onItemClick(View view, LocationVo data, int postion) {
        LogUtil.i(postion + "---" + GsonUtils.getInstance().toJson(data));
        PreferenceUtils.setPrefBoolean(getActivity(), Constants.LOCATION_FLAG, true);
        if (data.type != Constants.LOCATION_TYPE_FREE) {
            UserInfoVo vo = UserLoginUtil.getUserCache();
            if (vo == null) {
                ToastUtil.showShort(R.string.need_login);
                startActivity(LoginActivity.class);
                return;
            } else {
                if (Constants.LOCATION_TYPE_VIP > vo.level) {
                    ToastUtil.showShort( R.string.need_vip);
                    return;
                }
            }
        }
        LocationUtil.setLocation(getActivity(), data);
        EventBusUtil.getEventBus().postSticky(new LocationChooseEvent());
        if(needFinish==null || Boolean.TRUE.equals(needFinish)){
            getActivity().finish();
        }
        pullView.notifyDataSetChanged();
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new LocationViewAdapter(getActivity(),pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
}
