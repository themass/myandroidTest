package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.etiennelawlor.imagegallery.library.adapters.FullScreenImageGalleryAdapter;
import com.etiennelawlor.imagegallery.library.utilities.DisplayUtility;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.commons.common.ui.view.MyFavoriteView;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.ImgItemsVo;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.StaticDataUtil;
import com.openapi.ks.myapp.task.SaveImageCallBack;
import com.openapi.ks.myapp.task.SaveImageTask;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.inte.OnBackKeyDownListener;
import com.openapi.ks.moviefree1.R;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by openapi on 2016/8/12.
 */
public class ImgItemFragment extends RecommendFragment implements SaveImageCallBack,OnBackKeyDownListener, MyFavoriteView.OnFavoriteItemClick{
    private static final String IMG_ITEM_TAG = "Img_ITEM_TAG";
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;
    @BindView(R.id.full_view)
    RelativeLayout fullView;
    @BindView(R.id.rl_list)
    RelativeLayout listView;
    @BindView(R.id.vp_big_container)
    ViewPager viewPager;
    @BindView(R.id.tv_page)
    TextView tvPage;
    private ImgItemsVo vo;
    // region Member Variables
    MyFullScreenImageGalleryAdapter fullScreenImageGalleryAdapter;
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
    @OnClick(R.id.iv_save)
    public void onSave(View view){
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(getActivity(),infoListVo.voList.get(viewPager.getCurrentItem()).actionUrl,this);
    }
    public void onSuccess(){
        ToastUtil.showShort(R.string.save_ok);
    }

    public void onFailed(){
        ToastUtil.showShort(R.string.save_fail);
    }
    private void setPosition(int position) {
        tvPage.setText(String.format("%s/%s", String.valueOf(position + 1), infoListVo.total));
    }
    private final ViewPager.OnPageChangeListener viewPagerOnPageChangeListener = new ViewPager.OnPageChangeListener() {
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            if (ImgItemFragment.this.viewPager != null) {
                ImgItemFragment.this.viewPager.setCurrentItem(position);
                ImgItemFragment.this.setPosition(position);
                if((position==infoListVo.voList.size()-1)&&infoListVo.hasMore){
                    ToastUtil.showShort(R.string.down_to_loadmore);
                }
            }
        }

        public void onPageScrollStateChanged(int state) {
        }
    };
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_ITEMS, ImgItemsVo.class);
        StaticDataUtil.del(Constants.IMG_ITEMS);
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.url);
        fullScreenImageGalleryAdapter = new MyFullScreenImageGalleryAdapter();
        fullScreenImageGalleryAdapter.setFullScreenImageLoader(MyApplication.getInstance().getPhotoLoad());
        this.viewPager.setAdapter(fullScreenImageGalleryAdapter);
        this.viewPager.addOnPageChangeListener(this.viewPagerOnPageChangeListener);
        appearAnimation = new AlphaAnimation(0, 1);
        appearAnimation.setDuration(800);

        disappearAnimation = new AlphaAnimation(1, 0);
        disappearAnimation.setDuration(800);
    }
    protected BaseRecyclerViewAdapter getAdapter(){return null;}

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
        return Constants.getUrlWithParam(Constants.API_IMG_ITEM_PAGE_URL, start,String.valueOf(vo.url));
    }
    @Override
    public  String getNetTag(){
        return IMG_ITEM_TAG;
    }
    @Override
    public  boolean getShowEdit(){
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
    private void switchView(boolean onFull){
        if(onFull){

            listView.startAnimation(disappearAnimation);
            fullView.startAnimation(appearAnimation);
            listView.setVisibility(View.GONE);
            fullView.setVisibility(View.VISIBLE);
        }else{
            listView.startAnimation(appearAnimation);
            fullView.startAnimation(disappearAnimation);
            listView.setVisibility(View.VISIBLE);
            fullView.setVisibility(View.GONE);
        }
    }
    @Override
    public boolean onkeyBackDown() {
        if(fullView.getVisibility()==View.VISIBLE) {
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
    public String getBrowserDatUrl() {
        return infoListVo.voList.get(0).actionUrl;
    }

    public class MyFullScreenImageGalleryAdapter extends PagerAdapter {
        private FullScreenImageGalleryAdapter.FullScreenImageLoader fullScreenImageLoader;

        public Object instantiateItem(ViewGroup container, int position) {
            View view = LayoutInflater.from(getActivity()).inflate(R.layout.layout_img_item_view, null, false);
            ImageView imageView = (ImageView) view.findViewById(R.id.photoView);
            String image = infoListVo.voList.get(viewPager.getCurrentItem()).actionUrl;
            Context context = imageView.getContext();
            int width = DisplayUtility.getScreenWidth(context);
            this.fullScreenImageLoader.loadFullScreenImage(imageView, image, width, null);
            container.addView(view, 0);
            return view;
        }

        public int getCount() {
            return infoListVo.voList.size();
        }

        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public void setFullScreenImageLoader(FullScreenImageGalleryAdapter.FullScreenImageLoader loader) {
            this.fullScreenImageLoader = loader;
        }
    }
}
