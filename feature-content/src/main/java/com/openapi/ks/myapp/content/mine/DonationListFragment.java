package com.openapi.ks.myapp.content.mine;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.network.request.InfoListVo;
import com.openapi.feature.content.R;
import com.openapi.ks.myapp.content.mine.AppRecommendViewAdapter;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.content.mine.AppInfo;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.content.image.MyFullScreenImageGalleryActivity;

import java.util.ArrayList;

/**
 * Created by openapi on 2016/8/12.
 */
public class DonationListFragment extends BasePullLoadbleFragment<AppInfo> {
    private static final String APP_TAG = "APP_TAG";
    private AppRecommendViewAdapter adapter;
    ArrayList<String> origeImages = new ArrayList<>();

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, DonationListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_donation);
        context.startActivity(intent);
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        adapter = new AppRecommendViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
    }

    @Override
    protected CompletableFuture<InfoListVo<AppInfo>> loadData(Context context) {
        return indexService.getInfoListDataAsync(Constants.getUrl(Constants.API_DONATION_URL), AppInfo.class, APP_TAG);
    }

    @Override
    protected void onDataLoaded(InfoListVo<AppInfo> data) {
        super.onDataLoaded(data);
        if (infoListVo.voList != null) {
            origeImages.clear();
            for (AppInfo info : infoListVo.voList) {
                origeImages.add(info.img);
            }
        }
    }

    @Override
    public void onItemClick(View view, AppInfo data, int postion) {
        Intent intent = new Intent(getContext(), MyFullScreenImageGalleryActivity.class);
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(MyFullScreenImageGalleryActivity.KEY_IMAGES, origeImages);
        bundle.putInt(MyFullScreenImageGalleryActivity.KEY_POSITION, postion);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(APP_TAG);
        super.onDestroyView();
    }

}
