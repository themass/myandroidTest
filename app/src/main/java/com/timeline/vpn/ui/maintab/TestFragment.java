package com.timeline.vpn.ui.maintab;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.ui.base.LoadableTabFragment;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;

/**
 * Created by gqli on 2016/3/31.
 */
public class TestFragment extends LoadableTabFragment<InfoListVo<RecommendVo>> implements IndexRecommendAdapter.ItemClickListener {
    private static final String TAG = "testF";
    @Nullable
    @Bind(R.id.footer_view)
    View footerView;
    @Nullable
    @Bind(R.id.rv_navi)
    RecyclerView rvRecommend;
    @Bind(R.id.srl_layout)
    SwipeRefreshLayout refreshLayout;
    BaseService indexService;
    List<RecommendVo> mData = new ArrayList<>();

    @Override
    protected int getTabHeaderViewId() {
        return -1;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        IndexRecommendAdapter adapter = new IndexRecommendAdapter(this.getActivity(), rvRecommend, mData, null,layoutManager);
        rvRecommend.setLayoutManager(layoutManager);
        rvRecommend.setItemAnimator(new DefaultItemAnimator());
        rvRecommend.setAdapter(adapter);
    }

    @Override
    public void onItemClick(View v, int position) {
        LogUtil.i("onItemClick");
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent) {
        inflater.inflate(R.layout.tab_index_body_content, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        if (footerView != null) {
            footerView.setVisibility(View.GONE);
            if (refreshLayout.isRefreshing() && mData.size() > 0) {
                mData.clear();
                refreshLayout.setRefreshing(false);
            }
            mData.addAll(data.voList);
            data.voList = mData;
            setData(data);
            rvRecommend.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getRECOMMEND_URL(0), RecommendVo.class, TAG);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        indexService.cancelRequest(TAG);
    }
}
