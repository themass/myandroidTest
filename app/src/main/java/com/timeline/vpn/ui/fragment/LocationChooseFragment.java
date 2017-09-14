package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.sspacee.common.ui.view.DividerItemDecoration;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.GsonUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.LocationViewAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.bean.vo.UserInfoVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.LocationUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.LocationChooseEvent;
import com.timeline.vpn.data.sort.LocSortFactor;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;
import com.timeline.vpn.ui.user.LoginActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class LocationChooseFragment extends LoadableFragment<List<LocationVo>> implements BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<LocationVo> {
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
    @Nullable
    @BindView(R.id.loc_rv_location)
    RecyclerView rvLocation;
    private LocationViewAdapter adapter;
    private List<LocationVo> data = new ArrayList<>();
    private int typeIndex = 0;
    private int countryIndex = 0;
    private int feaIndex = 0;

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, LocationChooseFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
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
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new LocationViewAdapter(getActivity(), rvLocation, data, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        rvLocation.setLayoutManager(layoutManager);
//        rvLocation.addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        rvLocation.setAdapter(adapter);
        rvLocation.addItemDecoration(itemDecoration);
        setUp();
    }

    @Override
    protected void onDataLoaded(List<LocationVo> vo) {
        if (!CollectionUtils.isEmpty(vo)) {
            data.clear();
            data.addAll(vo);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected List<LocationVo> loadData(Context context) throws Exception {
        InfoListVo<LocationVo> vo = indexService.getInfoListData(Constants.getUrl(Constants.API_LOCATION_URL), LocationVo.class, LOCATION_TAG);
        return vo != null ? vo.voList : null;
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
                sort(Constants.sort_fea[(++feaIndex) % 2]);
            }
        });
    }

    private void sort(String sortBy) {
        Collections.sort(data, new LocSortFactor(sortBy));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(View view, LocationVo data, int postion) {
        LogUtil.i(postion + "---" + GsonUtils.getInstance().toJson(data));
        PreferenceUtils.setPrefBoolean(getActivity(), Constants.LOCATION_FLAG, true);
        if (data.type != Constants.LOCATION_TYPE_FREE) {
            UserInfoVo vo = UserLoginUtil.getUserCache();
            if (vo == null) {
                Toast.makeText(getActivity(), R.string.need_login, Toast.LENGTH_SHORT).show();
                startActivity(LoginActivity.class);
                return;
            } else {
                if (Constants.LOCATION_TYPE_VIP != vo.level) {
                    Toast.makeText(getActivity(), R.string.need_vip, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        LocationUtil.setLocation(getActivity(), data);
        EventBusUtil.getEventBus().post(new LocationChooseEvent());
        getActivity().finish();
    }
}
