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

import com.sspacee.common.ui.view.DividerItemDecoration;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.SoundItemsViewAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.SoundItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsFragment extends LoadableFragment<InfoListVo<SoundItemsVo>> implements BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<SoundItemsVo>,MyPullView.OnRefreshListener {
    private static final String SOUND_TAG = "SOUND_TAG";

    @Nullable
    @Bind(R.id.my_pullview)
    MyPullView pullView;
    private BaseService indexService;
    private  SoundItemsViewAdapter adapter;
    protected InfoListVo<SoundItemsVo> infoVo = new InfoListVo<>();
    private String channel;
    private RecommendVo vo;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, SoundItemsFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.sound);
        intent.putExtra(CommonFragmentActivity.PARAM, vo);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vo = (RecommendVo)getArguments().get(CommonFragmentActivity.PARAM);
        channel = vo.param;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_sounditems, parent);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        adapter = new SoundItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoVo.voList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pullView.setLayoutManager(layoutManager);
//        rvLocation.addItemDecoration(new RecycleViewDivider(mContext, LinearLayoutManager.VERTICAL));
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        pullView.setAdapter(adapter);
        pullView.setListener(this);
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
    }

    @Override
    protected void onDataLoaded(InfoListVo<SoundItemsVo> data) {
        if(pullView!=null) {
            if (data != null) {
                if (pullView.isLoadMore()) { //上拉加载
                    infoVo.voList.addAll(data.voList);
                } else { //下拉刷新 或者首次
                    infoVo.voList.clear();
                    infoVo.voList.addAll(data.voList);
                }
                infoVo.copy(data);
                data.voList.clear();
                data.voList.addAll(infoVo.voList);
                setData(data);
                LogUtil.i("mData size=" + infoVo.voList.size());
            }
            pullView.notifyDataSetChanged();
        }
    }
    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == MyPullView.OnRefreshListener.FRESH)
            infoVo.pageNum = 0;
        startQuery(false);
    }
    @Override
    protected InfoListVo<SoundItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getPageWithParam_URL(Constants.API_SOUND_ITEMS_URL,infoVo.pageNum,channel), SoundItemsVo.class, SOUND_TAG);
    }
    @Override
    public boolean needLoad() {
        return infoVo.hasMore;
    }
    @Override
    public void onItemClick(View view, SoundItemsVo data, int postion) {
        LogUtil.i(data.file);
    }
}
