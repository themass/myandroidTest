package com.openapi.feature.user.setting;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.common.ads.base.AdsContext;
import com.openapi.feature.user.R;
import com.openapi.feature.user.setting.SettingCharacterAdapter;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.core.app.AppContext;
import com.openapi.core.model.CharacterVo;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.base.CommonFragmentActivity;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;



/**
 * Created by openapi on 2016/8/12.
 */
public class SettingCharacterFragment extends BasePullLoadbleFragment<CharacterVo> implements BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<CharacterVo> {
    private SettingCharacterAdapter adapter;
    private static String TAG = "Character";

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, SettingCharacterFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.my_charater);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, false);
        intent.putExtra(CommonFragmentActivity.FABUP_SHOW, false);
        context.startActivity(intent);
    }

    @Override
    protected CompletableFuture<InfoListVo<CharacterVo>> loadData(Context context) {
        return indexService.getInfoListDataAsync(Constants.getUrl(Constants.API_CHARACTER_URL), CharacterVo.class, TAG);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        // 收藏变化时刷新列表；随视图的生命周期自动停止收集
        com.openapi.common.event.EventFlowsKt.observeJava(
                androidx.lifecycle.LifecycleOwnerKt.getLifecycleScope(getViewLifecycleOwner()),
                com.openapi.core.event.CoreEvents.INSTANCE.getFavoriteChanged(),
                event -> pullView.setRefresh(true));
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        adapter = new SettingCharacterAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }

    @Override
    public void onItemClick(View view, CharacterVo data, int postion) {
        super.onItemClick(view, data, postion);
        SystemUtils.copy(getActivity(), data.getContent());
        PreferenceUtils.setPrefString(AppContext.get(), Constants.MY_SETTING, data.getContent());
        ToastUtil.showShort(R.string.save);
    }

    @Override
    public void onItemLongClick(View view, CharacterVo data, int position) {
        SystemUtils.copy(getActivity(), data.getContent());
        PreferenceUtils.setPrefString(AppContext.get(), Constants.MY_SETTING, data.getContent());
        ToastUtil.showShort(R.string.save);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
