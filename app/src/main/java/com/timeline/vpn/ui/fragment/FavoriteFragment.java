package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.view.DividerItemDecoration;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.ModelUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.FavoriteViewAdapter;
import com.timeline.vpn.bean.vo.FavoriteVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.TextItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.FavoriteUtil;
import com.timeline.vpn.data.config.FavoriteChangeEvent;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import butterknife.BindView;


/**
 * Created by themass on 2016/8/12.
 */
public class FavoriteFragment extends LoadableFragment<InfoListVo<FavoriteVo>> implements MyPullView.OnRefreshListener, BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener<FavoriteVo> {
    private static final String TEXT_TAG = "text_tag";
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    @Nullable
    @BindView(R.id.my_pullview)
    MyPullView pullView;
    private FavoriteViewAdapter adapter;
    private InfoListVo<FavoriteVo> infoVo = new InfoListVo<>();
    private RecommendVo vo;

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, FavoriteFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_favorite);
        intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        context.startActivity(intent);
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        adapter = new FavoriteViewAdapter(getActivity(), pullView.getRecyclerView(), infoVo.voList, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        pullView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(getActivity(), LinearLayoutManager.VERTICAL, R.drawable.divider_item);
        pullView.setAdapter(adapter);
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        pullView.setListener(this);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    protected void onDataLoaded(InfoListVo<FavoriteVo> data) {
        if (pullView != null) {
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
    protected InfoListVo<FavoriteVo> loadData(Context context) throws Exception {
        List<FavoriteVo> list = FavoriteUtil.getLocalFavorites(context);
        InfoListVo<FavoriteVo> ret = new InfoListVo<FavoriteVo>();
        ret.hasMore = false;
        ret.voList = list;
        return ret;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FavoriteChangeEvent event) {
        pullView.setRefresh(true);
    }

    @Override
    public void onItemClick(View view, FavoriteVo data, int postion) {
        pullView.notifyDataSetChanged();
        if (data.type == Constants.FavoriteType.TEXT) {
            TextItemsVo vo = ModelUtils.json2Entry(data.extra, TextItemsVo.class);
            if (vo != null)
                TextItemsWebViewFragment.startFragment(getActivity(), vo);
        } else {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                SoundItemsFragment.startFragment(getActivity(), vo);
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
    public boolean needLoad() {
        return infoVo.hasMore;
    }

    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();
    }
}
