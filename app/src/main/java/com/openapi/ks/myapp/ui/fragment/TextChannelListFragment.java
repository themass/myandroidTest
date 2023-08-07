package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.adapter.TextChannelListItemsViewAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.FavoriteUtil;
import com.openapi.ks.myapp.data.HistoryUtil;
import com.openapi.ks.myapp.data.StaticDataUtil;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.MenuOneContext;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.moviefree1.R;

/**
 * Created by openapi on 2016/8/12.
 */
public class TextChannelListFragment extends BasePullLoadbleFragment<TextItemsVo> implements MenuOneContext.MyOnMenuItemClickListener, BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<TextItemsVo> {
    private static final String TEXT_TAG = "text_tag";
    private TextChannelListItemsViewAdapter adapter;
    private RecommendVo vo;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.text);
        StaticDataUtil.add(Constants.TEXT_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        context.startActivity(intent);
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        adapter = new TextChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }
    @Override
    protected boolean showSearchView(){
        return true;
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.TEXT_CHANNEL, RecommendVo.class);
        StaticDataUtil.del(Constants.TEXT_CHANNEL);
    }
    @Override
    protected InfoListVo<TextItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_TEXT_ITEMS_URL, infoListVo.pageNum, vo.param, keyword), TextItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, TextItemsVo data, int postion) {
        adapter.setSelected(-1);
        HistoryUtil.addHistory(getActivity(), data.fileUrl);
        if (StringUtils.hasText(data.fileUrl) && data.fileUrl.endsWith("html")) {
//            TextItemsStrFragment.startFragment(getActivity(),data);
            TextItemsWebViewFragment.startFragment(getActivity(), data);
        } else {
            TextItemsFragment.startFragment(getActivity(), data);
        }
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
