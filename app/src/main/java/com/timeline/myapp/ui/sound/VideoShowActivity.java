package com.timeline.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.sexfree.R;
import com.sspacee.common.ui.view.FavoriteImageView;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.timeline.myapp.data.ConnLogUtil;

import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.data.VideoUtil;
import com.timeline.myapp.ui.base.WebViewActivity;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import cn.jzvd.JZDataSource;
import cn.jzvd.JZUserAction;
import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoShowActivity extends AppCompatActivity implements GdtNativeManager.OnLoadListener{
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JzvdStd jzVideo;
    RecommendVo vo;
    @BindView(R.id.iv_favorite)
    FavoriteImageView ivFavorite;
    @BindView(R.id.iv_liu)
    View ivLiu;
    @BindView(R.id.natvieView)
    ViewGroup natvieView;
    private String url;
    GdtNativeManager gdtNativeManager = new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_BANNER);
    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        if(!CollectionUtils.isEmpty(mAdViewPositionMap)){
            gdtNativeManager.showAds(Constants.FIRST_AD_POSITION,natvieView);
        }else{
            AdsManager.getInstans().showBannerAds(this, natvieView, AdsContext.Categrey.CATEGREY_VPN2);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        url = vo.actionUrl;
        if(StringUtils.hasText(vo.urlToken)){
            url = url+vo.urlToken;
        }
        HashMap<String,String> header = new HashMap<>();
        header.put(Constants.REFERER, StringUtils.hasText(vo.baseurl)?vo.baseurl : vo.actionUrl);
        JZDataSource source = new JZDataSource(url,vo.title);
        source.headerMap = header;
        jzVideo.setUp(source,JzvdStd.SCREEN_WINDOW_NORMAL);
//        Object[] source = VideoUtil.getVideoSource(url,false,StringUtils.hasText(vo.baseurl)?vo.baseurl : vo.actionUrl);
//        jzVideo.setUp(source, JzvdStd .SCREEN_WINDOW_NORMAL);
//        jzVideo.headData = header;
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.thumbImageView);
        jzVideo.setJzUserAction(new MyUserActionStandard());
        ivFavorite.initSrc(vo.actionUrl);
        if(AdsContext.rateShow()){
            gdtNativeManager.loadDataVideoDetail(this);
        }
        if(!(url.endsWith("m3u8")||url.endsWith("mp4"))){
            ivLiu.setVisibility(View.GONE);
        }
    }
    @OnClick(R.id.iv_favorite)
    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(vo.tofavorite(Constants.FavoriteType.VIDEO));
    }
    @OnClick(R.id.iv_liu)
    public void openBrowserClick(View view) {
        WebViewActivity.startWebViewActivity(this, Constants.BASE_OPENINBROWSER+url, vo.title, true, false, null);
        finish();
    }
    @Override
    public void onPause() {
        super.onPause();
        try {
            Jzvd.goOnPlayOnPause();
        }catch (Throwable e){
            LogUtil.e(e);
        }
    }
    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        super.onBackPressed();
    }
    @Override
    public void onDestroy() {
        Jzvd.releaseAllVideos();
        super.onDestroy();
        unbinder.unbind();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
    protected boolean enableSliding() {
        return true;
    }
    class MyUserActionStandard implements JZUserAction {

        @Override
        public void onEvent(int type, Object url, int screen, Object... objects) {
            switch (type) {
                case JZUserAction.ON_CLICK_PAUSE:
                    AdsContext.showRand(VideoShowActivity.this,AdsContext.Categrey.CATEGREY_VPN2);
                    break;
                case JZUserAction.ON_AUTO_COMPLETE:
                    AdsContext.showRand(VideoShowActivity.this,AdsContext.Categrey.CATEGREY_VPN2);
                    break;
                case JZUserAction.ON_CLICK_START_ERROR:
                    ConnLogUtil.addLog(VideoShowActivity.this,vo.extra+"--"+vo.baseurl,vo.actionUrl,0);
                    break;

                default:break;
            }
        }
    }
}
