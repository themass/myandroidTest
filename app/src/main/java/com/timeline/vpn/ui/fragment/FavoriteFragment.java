package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.ModelUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.BaseRecyclerViewAdapter;
import com.timeline.vpn.adapter.FavoriteViewAdapter;
import com.timeline.vpn.bean.vo.FavoriteVo;
import com.timeline.vpn.bean.vo.ImgItemsVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.TextItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.FavoriteUtil;
import com.timeline.vpn.data.config.FavoriteChangeEvent;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.MenuOneContext;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;


/**
 * Created by themass on 2016/8/12.
 */
public class FavoriteFragment extends BasePullLoadbleFragment<FavoriteVo> implements FavoriteUtil.ModFavoriteListener,MenuOneContext.MyOnMenuItemClickListener,BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<FavoriteVo> {
    private FavoriteViewAdapter adapter;

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
    protected InfoListVo<FavoriteVo> loadData(Context context) throws Exception {
        List<FavoriteVo> list = FavoriteUtil.getLocalFavorites(context);
        InfoListVo<FavoriteVo> ret = new InfoListVo<FavoriteVo>();
        ret.hasMore = false;
        ret.voList = list;
        return ret;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }
    protected  BaseRecyclerViewAdapter getAdapter(){
        adapter = new FavoriteViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FavoriteChangeEvent event) {
        pullView.setRefresh(true);
    }

    @Override
    public void onItemClick(View view, FavoriteVo data, int postion) {
        super.onItemClick(view,data,postion);
        if (data.type == Constants.FavoriteType.TEXT) {
            TextItemsVo vo = ModelUtils.json2Entry(data.extra, TextItemsVo.class);
            if (vo != null)
                TextItemsWebViewFragment.startFragment(getActivity(), vo);
        } else if (data.type == Constants.FavoriteType.SOUND) {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                SoundItemsFragment.startFragment(getActivity(), vo);
        }else if (data.type == Constants.FavoriteType.IMG) {
            ImgItemsVo vo = ModelUtils.json2Entry(data.extra, ImgItemsVo.class);
            if (vo != null)
                ImgGalleryFragment.startFragment(getActivity(), vo);
        }
    }
    @Override
    public void onItemLongClick(View view, FavoriteVo data, int position){
        MenuOneContext.showOneMenu(getActivity(),view,R.string.menu_favorite_cancel,FavoriteFragment.this,position);
    }
    @Override
    public boolean onMenuItemClick(MenuItem item, int position){
        FavoriteUtil.modLocalFavoritesAsync(getActivity(),infoListVo.voList.get(position),this);
        return true;
    }
    public void modFavorite(boolean ret){
        pullView.setRefresh(true);
    }
    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();
    }
}
