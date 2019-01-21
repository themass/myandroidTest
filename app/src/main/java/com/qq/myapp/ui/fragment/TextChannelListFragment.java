package com.qq.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.kyview.natives.NativeAdInfo;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.kb.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.sspacee.yewu.ads.base.NativeAdsReadyListener;
import com.qq.myapp.adapter.NativeTextChannelListItemsViewAdapter;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.FavoriteVo;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.bean.vo.TextItemsVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.FavoriteUtil;
import com.qq.myapp.data.HistoryUtil;
import com.qq.myapp.data.StaticDataUtil;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.ui.base.CommonFragmentActivity;
import com.qq.myapp.ui.base.MenuOneContext;
import com.qq.myapp.ui.base.features.BasePullLoadbleFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Created by themass on 2016/8/12.
 */
public class TextChannelListFragment extends BasePullLoadbleFragment<TextItemsVo> implements MenuOneContext.MyOnMenuItemClickListener,BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<TextItemsVo>, NativeAdsReadyListener,GdtNativeManager.OnLoadListener {
    private static final String TEXT_TAG = "text_tag";
    private NativeTextChannelListItemsViewAdapter adapter;
    private RecommendVo vo;
    private List<NativeAdInfo> nativeData = new ArrayList<>();
    GdtNativeManager gdtNativeManager =new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_SIX,Constants.AD_COUNT);;
    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextChannelListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.text);
        StaticDataUtil.add(Constants.TEXT_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        context.startActivity(intent);
    }
    public boolean onAdRecieved(List<NativeAdInfo> data){
        nativeData.clear();
        for(NativeAdInfo item:data){
            item.onDisplay(new View(
                    getActivity()));
            nativeData.add(item);
        }
        adapter.notifyDataSetChanged();
        return true;
    }
    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        adapter.notifyDataSetChanged();
    }
    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
//        adapter = new TextChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this,nativeData);
        adapter = new NativeTextChannelListItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this,gdtNativeManager);
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
//        AdsManager.getInstans().showNative(getActivity(),this, AdsContext.getIndex(0));
        gdtNativeManager.loadData(getActivity());
    }
    @Override
    protected InfoListVo<TextItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_TEXT_ITEMS_URL, infoListVo.pageNum, vo.param, keyword), TextItemsVo.class, TEXT_TAG);
    }

    @Override
    public void onItemClick(View view, TextItemsVo data, int postion) {
        UserLoginUtil.showScoreNotice(2);
        adapter.setSelected(-1);
        HistoryUtil.addHistory(getActivity(), data.fileUrl);
        if (StringUtils.hasText(data.fileUrl)) {
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
            title = R.string.menu_favorite;
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
