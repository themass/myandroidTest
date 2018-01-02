package com.timeline.sex.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.etiennelawlor.imagegallery.library.adapters.ImageGalleryAdapter;
import com.etiennelawlor.imagegallery.library.utilities.DisplayUtility;
import com.sspacee.common.ui.view.FavoriteImageView;
import com.sspacee.common.ui.view.GridSpacingItemDecoration;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.StringUtils;
import com.timeline.sex.R;
import com.timeline.sex.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.sex.base.MyApplication;
import com.timeline.sex.bean.vo.ImgItemVo;
import com.timeline.sex.bean.vo.ImgItemsVo;
import com.timeline.sex.bean.vo.InfoListVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.StaticDataUtil;
import com.timeline.sex.ui.base.CommonFragmentActivity;
import com.timeline.sex.ui.base.features.BasePullLoadbleFragment;
import com.timeline.sex.ui.sound.MyFullScreenImageGalleryActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgGalleryFragment extends BasePullLoadbleFragment<ImgItemVo> implements ImageGalleryAdapter.OnImageClickListener{
    private static final String TEXT_TAG = "Img_ITEM_TAG";
    @BindView(R.id.iv_favorite)
    FavoriteImageView ivFavorite;
    private ImgItemsVo vo;
    // region Member Variables
    private ArrayList<String> images = new ArrayList<>();
    private ArrayList<String> origeImages = new ArrayList<>();
    private ArrayList<String> remteImages = new ArrayList<>();
    // endregion
    private int gridItemWidth;
    private int gridItemHeight;
    public static void startFragment(Context context, ImgItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgGalleryFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.IMG_ITEMS, vo);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, false);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, false);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_ITEMS, ImgItemsVo.class);
        StaticDataUtil.del(Constants.IMG_ITEMS);
        ivFavorite.initSrc(vo.url);
    }
    protected BaseRecyclerViewAdapter getAdapter(){return null;}
    @OnClick(R.id.iv_favorite)
    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(vo.tofavorite());
    }
    @Override
    public void initPullView() {
        int numOfColumns;
        if (DisplayUtility.isInLandscapeMode(getActivity())) {
            numOfColumns = 4;
        } else {
            numOfColumns = 3;
        }
        int lineW = DisplayUtility.dp2px(getActivity(),1);
        gridItemWidth = (DisplayUtility.getScreenWidth(getActivity())-(numOfColumns-1)*lineW)/numOfColumns;
        gridItemHeight =(int)(gridItemWidth*1.3) ;
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), numOfColumns);
        pullView.setLayoutManager(layoutManager);
        GridSpacingItemDecoration itemDecoration = new GridSpacingItemDecoration(numOfColumns, lineW, false);
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        MyImageGalleryAdapter imageGalleryAdapter = new MyImageGalleryAdapter(getContext(), images);
        imageGalleryAdapter.setOnImageClickListener(this);
        imageGalleryAdapter.setImageThumbnailLoader(MyApplication.getInstance().getPhotoLoad());
        pullView.setAdapter(imageGalleryAdapter);
        pullView.setListener(this);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_img_gallery_list, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<ImgItemVo> data) {
        setData(data);
        if (!CollectionUtils.isEmpty(data.voList)) {
            images.clear();
            origeImages.clear();
            remteImages.clear();
            for (ImgItemVo item : data.voList) {
                images.add(item.picUrl);
                if(StringUtils.hasText(item.origUrl)){
                    origeImages.add(item.origUrl);
                }else{
                    origeImages.add(item.picUrl);
                }
                if(StringUtils.hasText(item.remoteUrl)){
                    remteImages.add(item.remoteUrl);
                }else{
                    remteImages.add(item.picUrl);
                }
            }
        }
        pullView.notifyDataSetChanged();
    }

    @Override
    protected InfoListVo<ImgItemVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_IMG_ITEM_URL, String.valueOf(vo.url)), ImgItemVo.class, TEXT_TAG);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initPullView();
    }

    @Override
    public boolean needLoad() {
        return false;
    }

    @Override
    public void onRefresh(int type) {
        startQuery(false);
    }

    // region ImageGalleryAdapter.OnImageClickListener Methods
    @Override
    public void onImageClick(int position) {
//        FullScreenImageGalleryActivity.setFullScreenImageLoader(MyApplication.getInstance().getPhotoLoad());
        Intent intent = new Intent(getContext(), MyFullScreenImageGalleryActivity.class);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(MyFullScreenImageGalleryActivity.KEY_IMAGES, origeImages);
        bundle.putStringArrayList(MyFullScreenImageGalleryActivity.KEY_IMAGES_REMOTE, remteImages);
        bundle.putInt(MyFullScreenImageGalleryActivity.KEY_POSITION, position);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    // endregion
    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }
    public class MyImageGalleryAdapter extends ImageGalleryAdapter{
        public MyImageGalleryAdapter(Context context, List<String> images) {
            super(context,images);
        }
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.image_gallery_view, viewGroup, false);
            ImageView view = (ImageView)v.findViewById(R.id.iv);
//            view.setScaleType(ImageView.ScaleType.FIT_XY);
            view.setLayoutParams(this.getGridItemLayoutParams(view));
            return new ImageGalleryAdapter.ImageViewHolder(v);
        }
        private ViewGroup.LayoutParams getGridItemLayoutParams(View view) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.width = gridItemWidth;
            layoutParams.height = gridItemHeight;
            return layoutParams;
        }
    }
}
