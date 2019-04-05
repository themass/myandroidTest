package com.qq.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.qq.Constants;
import com.qq.network.R;
import com.qq.vpn.adapter.AppRecommendViewAdapter;
import com.qq.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.qq.vpn.domain.res.AppInfo;
import com.qq.vpn.domain.res.InfoListVo;
import com.qq.vpn.main.ui.MyFullScreenImageGalleryActivity;
import com.qq.vpn.ui.base.actvity.CommonFragmentActivity;
import com.qq.vpn.ui.base.fragment.BasePullLoadbleFragment;

import java.util.ArrayList;

/**
 * Created by themass on 2016/8/12.
 */
public class DonationListFragment extends BasePullLoadbleFragment<AppInfo> {
    private static final String APP_TAG = "APP_TAG";
    private AppRecommendViewAdapter adapter;
    ArrayList<String> origeImages = new ArrayList<>();
    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, DonationListFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_donation);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, false);
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
        return api.getInfoListData(Constants.getUrl(Constants.API_DONATION_URL), AppInfo.class, APP_TAG);
    }

    @Override
    protected void onDataLoaded(InfoListVo<AppInfo> data) {
        super.onDataLoaded(data);
        if(infoListVo.voList!=null) {
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
        api.cancelRequest(APP_TAG);
        super.onDestroyView();
    }

}