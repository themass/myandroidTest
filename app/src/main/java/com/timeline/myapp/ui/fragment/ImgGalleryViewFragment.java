package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.ui.view.gallery.BigImagePreview;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.DensityUtil;
import com.timeline.vpn.R;
import com.timeline.myapp.bean.vo.ImgItemVo;
import com.timeline.myapp.bean.vo.ImgItemsVo;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.StaticDataUtil;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.base.features.LoadableFragment;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgGalleryViewFragment extends LoadableFragment<InfoListVo<ImgItemVo>> {
    private static final String TEXT_TAG = "Img_ITEM_TAG";
    @Nullable
    @BindView(R.id.bigImagePreview)
    BigImagePreview mPreview;
    private ImgItemsVo vo;
    private List<Integer> mImgWidthList;
    private List<Integer> mImgHeightList;
    private int times;
    // region Member Variables
    private ArrayList<String> images = new ArrayList<>();
    // endregion

    public static void startFragment(Context context, ImgItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ImgGalleryViewFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.IMG_ITEMS, vo);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, false);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }
    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_img_gallery, parent, true);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.IMG_ITEMS, ImgItemsVo.class);
        StaticDataUtil.del(Constants.IMG_ITEMS);
        mImgWidthList = new ArrayList<>();
        mImgHeightList = new ArrayList<>();
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
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        opts.inSampleSize = 1;
        //opts.inJustDecodeBounds = false;
        for (int i = 0; i < images.size(); i++) {
            BitmapFactory.decodeResource(getResources(), Constants.img[i % Constants.img.length], opts);
            int width = opts.outWidth;
            int height = opts.outHeight;
            mImgWidthList.add(width / 3);
            mImgHeightList.add(height / 3);
        }
        mPreview.init(images, mImgWidthList, mImgHeightList,
                DensityUtil.dip2px(getActivity(), 4), DensityUtil.dip2px(getActivity(), 4), DensityUtil.dip2px(getActivity(), 60));

        mPreview.setGalleryLoadMore(new BigImagePreview.GalleryLoadMore() {
            @Override
            public void loadMore() {
                if (times > 1) {
                    images.clear();
                    mPreview.setGalleryLoadMoreData(images, mImgWidthList, mImgHeightList);
                } else {
                    mPreview.setGalleryLoadMoreData(images, mImgWidthList, mImgHeightList);
                }
                times++;
            }
        });
    }

    @Override
    protected InfoListVo<ImgItemVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_IMG_ITEM_URL, String.valueOf(vo.url)), ImgItemVo.class, TEXT_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }
}
