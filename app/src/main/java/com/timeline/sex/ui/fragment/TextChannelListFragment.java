package com.timeline.sex.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.sex.R;
import com.timeline.sex.adapter.TextChannelListItemsViewAdapter;
import com.timeline.sex.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.sex.bean.vo.FavoriteVo;
import com.timeline.sex.bean.vo.InfoListVo;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.bean.vo.TextItemsVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.FavoriteUtil;
import com.timeline.sex.data.HistoryUtil;
import com.timeline.sex.data.StaticDataUtil;
import com.timeline.sex.ui.base.CommonFragmentActivity;
import com.timeline.sex.ui.base.MenuOneContext;
import com.timeline.sex.ui.base.features.BasePullLoadbleFragment;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class TextChannelListFragment extends BasePullLoadbleFragment<TextItemsVo> implements MenuOneContext.MyOnMenuItemClickListener,BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<TextItemsVo> {
    private static final String TEXT_TAG = "text_tag";
    @Nullable
    @BindView(R.id.searchView)
    SearchView mSearchView;

    private TextChannelListItemsViewAdapter adapter;
    private RecommendVo vo;
    private String keyword = "";

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.text);
        StaticDataUtil.add(Constants.TEXT_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        if(AdsContext.rateSmallShow()){
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
            intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN2);
        }
        context.startActivity(intent);
    }

    @Override
    protected  int getRootViewId() {
        return R.layout.layout_text_list_view;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent);
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        adapter = new TextChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.TEXT_CHANNEL, RecommendVo.class);
        StaticDataUtil.del(Constants.TEXT_CHANNEL);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // 当点击搜索按钮时触发该方法
            @Override
            public boolean onQueryTextSubmit(String query) {
                keyword = query;
                pullView.setRefresh(true);
                return false;
            }

            // 当搜索内容改变时触发该方法
            @Override
            public boolean onQueryTextChange(String newText) {
                //没有内容时 在查询
                if (!StringUtils.hasText(newText) && StringUtils.hasText(keyword)) {
                    keyword = null;
                    pullView.setRefresh(true);
                    mSearchView.clearFocus();
                }
                return false;
            }
        });
    }
    @Override
    protected InfoListVo<TextItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_TEXT_ITEMS_URL, infoListVo.pageNum, vo.param, keyword), TextItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, TextItemsVo data, int postion) {
        adapter.setSelected(-1);
        HistoryUtil.addHistory(getActivity(), data.fileUrl);
        if (StringUtils.hasText(data.fileUrl)) {
//            TextItemsStrFragment.startFragment(getActivity(),data);
            TextItemsWebViewFragment.startFragment(getActivity(), data);
        } else {
            TextItemsFragment.startFragment(getActivity(), data);
        }
        mSearchView.clearFocus();
        super.onItemClick(view,data,postion);
    }

    @Override
    public void onItemLongClick(View view, final TextItemsVo data, int position) {
        FavoriteVo favoriteVo = FavoriteUtil.getFavorite(getActivity(), data.fileUrl);
        int title;
        int icon = R.drawable.ic_menu_favorite_ed;
        if (favoriteVo == null) {
            title =R.string.menu_favorite;
            icon = R.drawable.ic_menu_favorite_no;
        } else {
            title =R.string.menu_favorite_cancel;
        }
        MenuOneContext.showOneMenu(getActivity(),view,title,icon,TextChannelListFragment.this,position);
    }
    @Override
    public boolean onMenuItemClick(MenuItem item, int position){
        if(item.getItemId()==R.id.menu_share){
            SystemUtils.copy(getActivity(), infoListVo.voList.get(position).fileUrl);
            ToastUtil.showShort(R.string.menu_share_copy_ok);
            LogUtil.i(infoListVo.voList.get(position).fileUrl);
        }else {
            FavoriteUtil.modLocalFavoritesAsync(getActivity(), infoListVo.voList.get(position).tofavorite(), null);
        }
        return true;
    }
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }

}
