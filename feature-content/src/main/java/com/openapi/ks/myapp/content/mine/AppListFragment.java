package com.openapi.ks.myapp.content.mine;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.openapi.feature.content.R;
import com.openapi.ks.myapp.content.mine.AppRecommendViewAdapter;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.content.mine.AppInfo;
import com.openapi.network.request.InfoListVo;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;

/**
 * Created by openapi on 2016/8/12.
 */
public class AppListFragment extends BasePullLoadbleFragment<AppInfo> {
    private static final String APP_TAG = "APP_TAG";
    private AppRecommendViewAdapter adapter;

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, AppListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_app);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
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
        return indexService.getInfoListDataAsync(Constants.getUrl(Constants.API_APP_URL), AppInfo.class, APP_TAG);
    }

    @Override
    public void onItemClick(View view, AppInfo data, int postion) {
        super.onItemClick(view, data, postion);
        final Uri uri = Uri.parse(data.url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(APP_TAG);
        super.onDestroyView();
    }

}
