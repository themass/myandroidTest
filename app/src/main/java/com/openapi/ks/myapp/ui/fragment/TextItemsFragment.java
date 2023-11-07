package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.openapi.commons.common.ui.view.MyFavoriteView;
import com.openapi.commons.common.ui.view.MyScrollView;
import com.openapi.commons.common.ui.view.MyTextView;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.TextItemVo;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.data.StaticDataUtil;
import com.openapi.ks.myapp.task.SaveTextTask;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.features.LoadableFragment;
import com.openapi.ks.moviefree1.R;

import butterknife.BindView;

/**
 * Created by openapi on 2016/8/12.
 */
public class TextItemsFragment extends LoadableFragment<TextItemVo> implements  MyFavoriteView.OnFavoriteItemClick {
    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
    @BindView(R.id.tv_view)
    MyTextView tvView;
    @BindView(R.id.sv_text)
    MyScrollView svText;
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;
    private BaseService indexService;
    private TextItemsVo vo;
    private String infoVo;
    private String url;

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
        url = vo.fileUrl;
        StaticDataUtil.del(Constants.TEXT_FILE);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(url);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_textitems, parent, true);
    }

    @Override
    protected void onDataLoaded(TextItemVo data) {
        infoVo = data.file;
        tvView.setText(Html.fromHtml(infoVo).toString().trim());
    }

    @Override
    protected TextItemVo loadData(Context context) throws Exception {
        return indexService.getData(Constants.getUrlWithParam(Constants.API_TEXT_ITEM_URL, String.valueOf(vo.id)), TextItemVo.class, TEXT_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }
    @Override
    public FavoriteVo getFavoriteDataUrl() {
        SaveTextTask.startSave(getActivity(), url);
        return vo.tofavorite();
    }

    @Override
    public String getBrowserDatUrl() {
        return url;
    }
}