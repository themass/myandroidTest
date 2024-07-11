package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.ModelUtils;
import com.openapi.commons.common.util.PathUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.net.NetUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.FavoriteViewAdapter;
import com.openapi.ks.myapp.adapter.SettingCharacterAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.CharacterVo;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.IWannaVo;
import com.openapi.ks.myapp.bean.vo.ImgItemsVo;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.FavoriteUtil;
import com.openapi.ks.myapp.data.config.FavoriteChangeEvent;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.MenuOneContext;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.sound.VideoShowActivityLazyUrl;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.List;


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
    protected InfoListVo<CharacterVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrl(Constants.API_CHARACTER_URL), CharacterVo.class, TAG);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        adapter = new SettingCharacterAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FavoriteChangeEvent event) {
        pullView.setRefresh(true);
    }

    @Override
    public void onItemClick(View view, CharacterVo data, int postion) {
        super.onItemClick(view, data, postion);
        SystemUtils.copy(getActivity(), data.getContent());
        PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.MY_SETTING, data.getContent());
        ToastUtil.showShort(R.string.save);
    }

    @Override
    public void onItemLongClick(View view, CharacterVo data, int position) {
        SystemUtils.copy(getActivity(), data.getContent());
        PreferenceUtils.setPrefString(MyApplication.getInstance(), Constants.MY_SETTING, data.getContent());
        ToastUtil.showShort(R.string.save);
    }

    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();
    }
}
