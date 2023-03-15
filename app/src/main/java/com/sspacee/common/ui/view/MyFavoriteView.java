package com.sspacee.common.ui.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ks.myapp.bean.vo.FavoriteVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.config.ConfigActionEvent;
import com.ks.sexfree1.R;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by themass on 2016/9/6.
 */
public class MyFavoriteView extends LinearLayout {

    public static String url_pre1 = "https://jx1.pkdyplayer.com/m3u8/?url=";
    public static String url_pre2 =  "https://www.m3u8hls.com#";

    @Nullable
    @BindView(R.id.iv_favorite)
    FavoriteImageView ivFavorite;
    @BindView(R.id.iv_open_browser)
    ImageView ivBrowser;
    @BindView(R.id.iv_mp4_web)
    ImageView mp4Web;
    @BindView(R.id.iv_mp4_web2)
    ImageView mp4Web2;
    private OnFavoriteItemClick onFavoriteItemClick;

    public MyFavoriteView(Context context) {
        super(context);
        setupView();
    }

    public MyFavoriteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyFavoriteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }
    public void setListener(MyFavoriteView.OnFavoriteItemClick listener) {
        this.onFavoriteItemClick = listener;
    }

    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.layout_favorite_bar, null);
        addView(myView);
        ButterKnife.bind(this);
    }
    public void initFavoriteBackGroud(String url){
        ivFavorite.initSrc(url);
    }
    public void showVideoLocal(){
        mp4Web.setVisibility(View.VISIBLE);
        mp4Web2.setVisibility(View.VISIBLE);
    }
    @OnClick(R.id.iv_favorite)
    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(onFavoriteItemClick.getFavoriteDataUrl());
    }

    @OnClick(R.id.iv_open_browser)
    public void browserClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDatUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        startBrower();
    }
    @OnClick(R.id.iv_mp4_web)
    public void mp4OpenClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDatUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        Map<String, Object> param = new HashMap<>();
        param.put(Constants.ADS_SHOW_CONFIG, false);
        param.put(Constants.ADS_POP_SHOW_CONFIG, false);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getContext(), url_pre1+onFavoriteItemClick.getBrowserDatUrl(), "m3u8-1线", param));
    }
    @OnClick(R.id.iv_mp4_web2)
    public void mp42OpenClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDatUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        Map<String, Object> param = new HashMap<>();
        param.put(Constants.ADS_SHOW_CONFIG, false);
        param.put(Constants.ADS_POP_SHOW_CONFIG, false);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getContext(), url_pre2+onFavoriteItemClick.getBrowserDatUrl(), "m3u8-2线", param));
    }
    public void startBrower() {
        if (PackageUtils.hasBrowser(getContext())) {
            Uri uri = Uri.parse(onFavoriteItemClick.getBrowserDatUrl());
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            if (it.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(it);
            }
        } else {
            ToastUtil.showShort(R.string.no_browser);
        }
    }

    public interface OnFavoriteItemClick {

        FavoriteVo getFavoriteDataUrl();
        String getBrowserDatUrl();

    }

}
