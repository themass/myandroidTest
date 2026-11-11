package com.openapi.ks.myapp.content.text;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.content.text.TextChannelListItemsViewAdapter;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.core.db.FavoriteVo;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.ks.myapp.content.text.TextItemsVo;
import com.openapi.core.config.Constants;
import com.openapi.core.util.FavoriteUtil;
import com.openapi.ks.myapp.content.common.HistoryUtil;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.content.common.MenuOneContext;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;
import com.openapi.feature.content.R;

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
    protected boolean showSearchView() {
        return true;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.TEXT_CHANNEL, RecommendVo.class);
        StaticDataUtil.del(Constants.TEXT_CHANNEL);
    }

    @Override
    protected CompletableFuture<InfoListVo<TextItemsVo>> loadData(Context context) {
        return indexService.getInfoListDataAsync(Constants.getUrlWithParam(Constants.API_TEXT_ITEMS_URL, infoListVo.pageNum, vo.param, keyword), TextItemsVo.class, TEXT_TAG);
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
        super.onItemClick(view, data, postion);
    }

    @Override
    public void onItemLongClick(View view, final TextItemsVo data, int position) {
        FavoriteVo favoriteVo = FavoriteUtil.getFavorite(getActivity(), data.fileUrl);
        int title;
        int icon = com.openapi.core.R.drawable.ic_menu_favorite_ed;
        if (favoriteVo == null) {
            title = R.string.menu_favorite;
            icon = com.openapi.core.R.drawable.ic_menu_favorite_no;
        } else {
            title = R.string.menu_favorite_cancel;
        }
        MenuOneContext.showOneMenu(getActivity(), view, title, icon, TextChannelListFragment.this, position);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item, int position) {
        if (item.getItemId() == R.id.menu_share) {
            SystemUtils.copy(getActivity(), infoListVo.voList.get(position).fileUrl);
            ToastUtil.showShort(R.string.menu_share_copy_ok);
            LogUtil.i(infoListVo.voList.get(position).fileUrl);
        } else {
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
