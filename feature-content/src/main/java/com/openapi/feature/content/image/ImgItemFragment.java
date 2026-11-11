package com.openapi.feature.content.image;

import com.openapi.feature.content.common.RecommendFragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.feature.content.image.FullScreenImagePagerAdapter;

import com.openapi.core.db.FavoriteVo;
import com.openapi.core.favorite.MyFavoriteView;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.core.app.AppContext;
import com.openapi.feature.content.image.ImgItemsVo;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.feature.content.image.SaveImageCallBack;
import com.openapi.feature.content.image.SaveImageTask;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.core.legacyui.inte.OnBackKeyDownListener;
import com.openapi.feature.content.R;


/**
 * Created by openapi on 2016/8/12.
 */
public class ImgItemFragment extends RecommendFragment implements SaveImageCallBack, OnBackKeyDownListener, MyFavoriteView.OnFavoriteItemClick {
    private static final String IMG_ITEM_TAG = "Img_ITEM_TAG";
    MyFavoriteView myFavoriteView;
    RelativeLayout fullView;
    RelativeLayout listView;
    ViewPager2 viewPager;
    TextView tvPage;
    private ImgItemsVo vo;
    // region Member Variables
    FullScreenImagePagerAdapter fullScreenImageGalleryAdapter;
    AlphaAnimation appearAnimation;
    AlphaAnimation disappearAnimation;

    // endregion
    public static void startFragment(Context context, ImgItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgItemFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.IMG_ITEMS, vo);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.FABUP_SHOW, false);
        context.startActivity(intent);
    }

    public void onSave(View view) {
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(getActivity(), infoListVo.voList.get(viewPager.getCurrentItem()).actionUrl, this);
    }

    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        myFavoriteView = view.findViewById(R.id.my_favoriteview);
        fullView = view.findViewById(R.id.full_view);
        listView = view.findViewById(R.id.rl_list);
        viewPager = view.findViewById(R.id.vp_big_container);
        tvPage = view.findViewById(R.id.tv_page);
        view.findViewById(R.id.iv_save).setOnClickListener(this::onSave);
    }

    @Override
    protected void unbindViews() {
        myFavoriteView = null;
        fullView = null;
        listView = null;
        viewPager = null;
        tvPage = null;
        super.unbindViews();
    }


    public void onSuccess() {
        ToastUtil.showShort(R.string.save_ok);
    }

    public void onFailed() {
        ToastUtil.showShort(R.string.save_fail);
    }

    private void setPosition(int position) {
        tvPage.setText(String.format("%s/%s", String.valueOf(position + 1), infoListVo.total));
    }

    private final ViewPager2.OnPageChangeCallback viewPagerOnPageChangeListener = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            if (ImgItemFragment.this.viewPager != null) {
                ImgItemFragment.this.setPosition(position);
                if ((position == infoListVo.voList.size() - 1) && infoListVo.hasMore) {
                    ToastUtil.showShort(R.string.down_to_loadmore);
                }
            }
        }
    };

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_ITEMS, ImgItemsVo.class);
        StaticDataUtil.del(Constants.IMG_ITEMS);
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.url);
        fullScreenImageGalleryAdapter = new FullScreenImagePagerAdapter(new FullScreenImagePagerAdapter.UrlSource() {
            @Override
            public int count() {
                return infoListVo.voList.size();
            }

            @Override
            public String urlAt(int position) {
                return infoListVo.voList.get(position).actionUrl;
            }
        });
        this.viewPager.setAdapter(fullScreenImageGalleryAdapter);
        this.viewPager.registerOnPageChangeCallback(this.viewPagerOnPageChangeListener);
        appearAnimation = new AlphaAnimation(0, 1);
        appearAnimation.setDuration(800);

        disappearAnimation = new AlphaAnimation(1, 0);
        disappearAnimation.setDuration(800);
    }

    protected BaseRecyclerViewAdapter getAdapter() {
        return null;
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_img_gallery_page_list, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        fullScreenImageGalleryAdapter.notifyDataSetChanged();
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_IMG_ITEM_PAGE_URL, start, String.valueOf(vo.url));
    }

    @Override
    public String getNetTag() {
        return IMG_ITEM_TAG;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        this.viewPager.setCurrentItem(position);
        this.setPosition(position);
        switchView(true);
    }

    private void switchView(boolean onFull) {
        if (onFull) {

            listView.startAnimation(disappearAnimation);
            fullView.startAnimation(appearAnimation);
            listView.setVisibility(View.GONE);
            fullView.setVisibility(View.VISIBLE);
        } else {
            listView.startAnimation(appearAnimation);
            fullView.startAnimation(disappearAnimation);
            listView.setVisibility(View.VISIBLE);
            fullView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onkeyBackDown() {
        if (fullView.getVisibility() == View.VISIBLE) {
            switchView(false);
            return true;
        }
        return false;
    }

    // endregion
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(IMG_ITEM_TAG);
        super.onDestroyView();
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite();
    }

    @Override
    public String getBrowserDataUrl() {
        return infoListVo.voList.get(0).actionUrl;
    }

}
