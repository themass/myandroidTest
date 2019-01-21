package com.qq.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;


import com.qq.kb.R;
import com.qq.myapp.adapter.AppRecommendViewAdapter;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.AppInfo;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.ui.base.CommonFragmentActivity;
import com.qq.myapp.ui.base.features.BasePullLoadbleFragment;

/**
 * Created by themass on 2016/8/12.
 */
public class AppListFragment extends BasePullLoadbleFragment<AppInfo>{
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
        adapter = new AppRecommendViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList,this);
        return adapter;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
    }
    @Override
    protected InfoListVo<AppInfo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrl(Constants.API_APP_URL), AppInfo.class, APP_TAG);
    }

    @Override
    public void onItemClick(View view, AppInfo data, int postion) {
        super.onItemClick(view,data,postion);
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
