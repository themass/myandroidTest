package com.openapi.ks.myapp.content.common;


import java.util.concurrent.CompletableFuture;
import android.content.Context;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.view.View;

import com.openapi.core.CommonConstants;
import com.openapi.ks.myapp.content.common.touch.OnStartDragListener;
import com.openapi.ks.myapp.content.common.touch.SimpleItemTouchHelperCallback;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.ads.yewu.um.MobAgent;
import com.openapi.feature.content.R;
import com.openapi.ks.myapp.content.common.IndexRecommendAdapter;
import com.openapi.ks.myapp.content.common.BasePhotoFlowRecycleViewAdapter;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.core.event.ConfigActionEvent;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;
import com.openapi.core.config.Routes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by openapi on 2015/9/1.
 */
public abstract class RecommendFragment extends BasePullLoadbleFragment<RecommendVo> implements BasePhotoFlowRecycleViewAdapter.OnRecyclerViewItemClickListener, OnStartDragListener, IndexRecommendAdapter.OnEditClickListener {
    protected ItemTouchHelper mItemTouchHelper;
    protected IndexRecommendAdapter adapter;

    public void addData(List<RecommendVo> data) {
        infoListVo.voList.addAll(data);
        initSort();
        sortData();
        mData.voList.addAll(data);
    }

    @Override
    protected CompletableFuture<InfoListVo<RecommendVo>> loadData(Context context) {
        return indexService.getInfoListDataAsync(getUrl(infoListVo.pageNum), RecommendVo.class, getNetTag());
    }

    protected void switchFlag(boolean flag) {
        adapter.switchFlag(flag);
    }

    protected boolean getSwitchFlag() {
        return adapter.getSwitchFlag();
    }

    protected boolean getShowParam() {
        return true;
    }

    /**
     * 默认：纵向瀑布流（列宽一致、行高随比例变化）。子类可改为不规则宫格等。
     */
    protected RecyclerView.LayoutManager createRecommendLayoutManager() {
        StaggeredGridLayoutManager lm = new StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL);
        lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        return lm;
    }

    /**
     * 图片频道：6 等分，按 2+2+2 / 3+3 交替占格，实现同一行内宽度不一致。
     */
    protected GridLayoutManager createIrregularWidthImageGridLayout() {
        GridLayoutManager glm = new GridLayoutManager(getActivity(), 6);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int mod = position % 5;
                return mod < 3 ? 2 : 3;
            }
        });
        return glm;
    }

    @Override
    protected void initPullView() {
        RecyclerView.LayoutManager layoutManager = createRecommendLayoutManager();
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoListVo.voList, layoutManager, this, this, this, getShowEdit());
        adapter.setShowParam(getShowParam());
        adapter.setNeedShimmer(getNeedShimmer());
        pullView.setListener(this);
        pullView.setAdapter(adapter);

        switchFlag(false);
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapter, getCanMove(), false);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(pullView.getRecyclerView());
    }

    //initPullView 方法被复写，这个函数无用了
    protected BaseRecyclerViewAdapter getAdapter() {
        return null;
    }

    protected boolean checkUserLevel(int type) {
        if (type > 0) {
            if (UserLoginUtil.getUserCache() == null) {
                startActivity(Routes.login(getActivity()));
                return false;
            }
            if (UserLoginUtil.getUserCache().level < type) {
                ToastUtil.showShort(R.string.vip_tips);
                return false;
            }
        }
        return true;
    }

    public boolean adClick(View v, RecommendVo vo) {
        return false;
    }

    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!checkUserLevel(vo.type)) {
            return;
        }
        Map<String, Object> param = new HashMap<>();
        param.put(CommonConstants.ADS_SHOW_CONFIG, vo.adsShow);
        param.put(CommonConstants.ADS_POP_SHOW_CONFIG, vo.adsPopShow);
        com.openapi.core.event.CoreEvents.INSTANCE.postConfigAction(new ConfigActionEvent(getActivity(), vo.actionUrl, vo.title, param));
        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!adClick(v, vo)) {
            onCustomerItemClick(v, position);
        }
    }

    @Override
    public void onLongItemClick(View view, int position) {

    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(getNetTag());
        super.onDestroyView();
    }

    protected boolean getNeedShimmer() {
        return true;
    }

    public abstract String getUrl(int start);

    public abstract String getNetTag();

    public abstract int getSpanCount();

    public abstract boolean getShowEdit();

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
    }

    @Override
    public void onItemMove(Object o1, Object o2) {
    }

    @Override
    public void onItemRemove(Object o) {
    }

    public boolean getCanMove() {
        return false;
    }

    @Override
    public void onEditClick(View view, int postion) {

    }
}
