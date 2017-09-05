package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.etiennelawlor.imagegallery.library.adapters.ImageGalleryAdapter;
import com.etiennelawlor.imagegallery.library.utilities.DisplayUtility;
import com.sspacee.common.ui.view.GridSpacingItemDecoration;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.CollectionUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;
import com.timeline.vpn.bean.vo.ImgItemVo;
import com.timeline.vpn.bean.vo.ImgItemsVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;
import com.timeline.vpn.ui.sound.MyFullScreenImageGalleryActivity;

import java.util.ArrayList;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgGalleryFragment extends LoadableFragment<InfoListVo<ImgItemVo>> implements MyPullView.OnRefreshListener, ImageGalleryAdapter.OnImageClickListener {
    private static final String TEXT_TAG = "Img_ITEM_TAG";
    @Nullable
    @BindView(R.id.my_pullview)
    MyPullView pullView;
    private BaseService indexService;
    private ImgItemsVo vo;
    // region Member Variables
    private ArrayList<String> images = new ArrayList<>();
    // endregion

    public static void startFragment(Context context, ImgItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgGalleryFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.IMG_ITEMS, vo);
        intent.putExtra(CommonFragmentActivity.ADS, false);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, true);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, false);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        vo = StaticDataUtil.get(Constants.IMG_ITEMS, ImgItemsVo.class);
        StaticDataUtil.del(Constants.IMG_ITEMS);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        int numOfColumns;
        if (DisplayUtility.isInLandscapeMode(getActivity())) {
            numOfColumns = 4;
        } else {
            numOfColumns = 3;
        }
        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), numOfColumns);
        pullView.setLayoutManager(layoutManager);
        GridSpacingItemDecoration itemDecoration = new GridSpacingItemDecoration(numOfColumns, 1, false);
        pullView.getRecyclerView().addItemDecoration(itemDecoration);
        ImageGalleryAdapter imageGalleryAdapter = new ImageGalleryAdapter(getContext(), images);
        imageGalleryAdapter.setOnImageClickListener(this);
        imageGalleryAdapter.setImageThumbnailLoader(MyApplication.getInstance().getPhotoLoad());
        pullView.setAdapter(imageGalleryAdapter);
        pullView.setListener(this);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.common_mypage_view, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<ImgItemVo> data) {
        setData(data);
        if (!CollectionUtils.isEmpty(data.voList)) {
            images.clear();
            for (ImgItemVo item : data.voList) {
                images.add(item.picUrl);
            }
        }
        pullView.notifyDataSetChanged();
    }

    @Override
    protected InfoListVo<ImgItemVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getWithParam_URL(Constants.API_IMG_ITEM_URL, String.valueOf(vo.url)), ImgItemVo.class, TEXT_TAG);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setUpRecyclerView();
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
        bundle.putStringArrayList(MyFullScreenImageGalleryActivity.KEY_IMAGES, images);
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
}
