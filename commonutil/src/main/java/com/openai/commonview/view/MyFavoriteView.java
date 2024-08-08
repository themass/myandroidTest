package com.openai.commonview.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.openai.CommonConstants;
import com.openai.commonevent.ConfigActionEvent;
import com.openai.commonevent.FavoriteVo;
import com.openai.commonutil.EventBusUtil;
import com.openai.commonutil.PackageUtils;
import com.openai.commonutil.R;
import com.openai.commonutil.SystemUtils;
import com.openai.commonutil.ToastUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by openapi on 2016/9/6.
 */
public class MyFavoriteView extends LinearLayout {

//    public static String url_pre1 = "https://jx1.pkdyplayer.com/m3u8/?url=";
    public static String url_pre1 = "http://file.ok123find.top/index2.html?play=";
    public static String url_pre2 = "https://m3u8play.com?play=";
    int currentSpeedIndex = 0;
    float[] speedX = {1.0f, 1.25f, 1.5f, 1.75f, 2f};
    FavoriteImageView ivFavorite;
    ImageView ivBrowser;
    ImageView mp4Web;
    ImageView mp4Web2;
    TextView speed;
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
        ivFavorite = findViewById(R.id.iv_favorite);
        ivBrowser = findViewById(R.id.iv_open_browser);
        mp4Web = findViewById(R.id.iv_mp4_web);
        mp4Web2 = findViewById(R.id.iv_mp4_web2);
        speed = findViewById(R.id.speed);
        ivFavorite.setOnClickListener(v -> favoriteClick(v));
        ivBrowser.setOnClickListener(v -> browserClick(v));
        mp4Web.setOnClickListener(v -> mp4OpenClick(v));
        mp4Web2.setOnClickListener(v -> mp42OpenClick(v));
        speed.setOnClickListener(v -> speedClick(v));

    }

    public void initFavoriteBackGroud(String url) {
        ivFavorite.initSrc(url);
    }

    public void showVideoLocal() {
        mp4Web.setVisibility(View.VISIBLE);
//        mp4Web2.setVisibility(View.VISIBLE);
        speed.setVisibility(View.VISIBLE);
        speed.setText(speedX[currentSpeedIndex] + "X");
    }

    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(onFavoriteItemClick.getFavoriteDataUrl());
    }

    public void speedClick(View view) {
        int index = currentSpeedIndex;
        currentSpeedIndex = (currentSpeedIndex + 1) % speedX.length;
        boolean ret = onFavoriteItemClick.setSpeed(speedX[currentSpeedIndex]);
        if (!ret) {
            currentSpeedIndex = index;
            return;
        }
        speed.setText(speedX[currentSpeedIndex] + "X");
        ToastUtil.showShort(speedX[currentSpeedIndex] + "X");
    }

    public void browserClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDataUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        startBrower();
    }

    public void mp4OpenClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDataUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        String url = url_pre1 + onFavoriteItemClick.getBrowserDataUrl();
//        Map<String, Object> param = new HashMap<>();
//        param.put(CommonConstants.ADS_SHOW_CONFIG, false);
//        param.put(CommonConstants.ADS_POP_SHOW_CONFIG, false);
//        EventBusUtil.getEventBus().post(new ConfigActionEvent(getContext(), url_pre1 + onFavoriteItemClick.getBrowserDataUrl(), "m3u8-1线", param));
        if (PackageUtils.hasBrowser(getContext())) {
            Uri uri = Uri.parse(url);
            Intent it = new Intent(Intent.ACTION_VIEW, uri);
            getContext().startActivity(it);
        }
    }

    public void mp42OpenClick(View view) {
        SystemUtils.copy(getContext(), onFavoriteItemClick.getBrowserDataUrl());
        ToastUtil.showShort(R.string.menu_share_copy_ok);
        Map<String, Object> param = new HashMap<>();
        param.put(CommonConstants.ADS_SHOW_CONFIG, false);
        param.put(CommonConstants.ADS_POP_SHOW_CONFIG, false);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getContext(), url_pre2 + onFavoriteItemClick.getBrowserDataUrl(), "m3u8-2线", param));
    }

    public void startBrower() {
        if (PackageUtils.hasBrowser(getContext())) {
            Uri uri = Uri.parse(onFavoriteItemClick.getBrowserDataUrl());
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

        String getBrowserDataUrl();

        default boolean setSpeed(float speed) {
            return false;
        }

    }

}
