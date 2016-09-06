package com.timeline.vpn.ui.feedback;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.FeedAdapter;
import com.timeline.vpn.bean.vo.IWannaVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.ui.base.LoadableFragment;
import com.timeline.vpn.ui.view.HeartAnimView;

import java.util.ArrayList;

import butterknife.Bind;

/**
 * Created by gqli on 2016/9/5.
 */
public class IWannaFragment extends LoadableFragment<InfoListVo<IWannaVo>> implements FeedAdapter.OnFeedItemClickListener{
    private static String TAG = "IWANNA";
    private BaseService indexService;
    @Nullable
    @Bind(R.id.rv_navi)
    RecyclerView rvRecommend;
    @Bind(R.id.srl_layout)
    SwipeRefreshLayout refreshLayout;
    @Nullable
    @Bind(R.id.footer_view)
    View footerView;
    @Bind(R.id.tv_tips)
    ShimmerTextView tvTips;
    FeedAdapter  feedAdapter;
    private boolean isLoadingMore;
    Shimmer shimmer;
    private InfoListVo<IWannaVo> infoVo = new InfoListVo<IWannaVo>();
    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity()) {
            @Override
            protected int getExtraLayoutSpace(RecyclerView.State state) {
                return 300;
            }
        };
        infoVo.voList = new ArrayList<>();
        rvRecommend.setLayoutManager(linearLayoutManager);
        feedAdapter = new FeedAdapter(getActivity(),infoVo.voList,this);
        rvRecommend.setAdapter(feedAdapter);
        setUpShimmer();

    }
    public void setUpShimmer() {
        shimmer = new Shimmer();
        shimmer.setDuration(Constants.VIP_SHIMMER_DURATION);
        shimmer.start(tvTips);
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_iwanna, parent);
    }

    @Override
    protected void onDataLoaded(InfoListVo<IWannaVo> data) {
        //下拉刷新
        if (refreshLayout.isRefreshing()) {
            infoVo.voList.clear();
            refreshLayout.setRefreshing(false);
            infoVo.voList.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
        } else if (footerView.getVisibility() == View.VISIBLE) { //上拉加载
            footerView.setVisibility(View.GONE);
            infoVo.voList.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
        } else if (isLoadingMore) {//首次加载
            infoVo.voList.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
        }
        isLoadingMore = false;
        data = infoVo;
        setData(data);
        LogUtil.i("mData size=" + data.voList.size());
    }

    @Override
    protected InfoListVo<IWannaVo> loadData(Context context) throws Exception {
        isLoadingMore = true;
        return indexService.getInfoListData(String.format(Constants.API_IWANNA_URL,infoVo.pageNum),IWannaVo.class,TAG);
    }

    @Override
    public void onCommentsClick(View v, int position) {
        HeartAnimView.show(getActivity(),null);
    }
    public static IWannaFragment getInstanse(){
        return new IWannaFragment();
    }
}
