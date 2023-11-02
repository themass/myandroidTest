package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.VolleyError;
import com.openapi.commons.common.ui.view.MyFavoriteView;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.StringUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.net.request.CommonResponse;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.bean.vo.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.BaseService;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.VideoUtil;
import com.openapi.ks.myapp.ui.sound.media.JZMediaExo;
import com.openapi.ks.myapp.ui.sound.media.JZMediaIjk;
import com.openapi.ks.myapp.ui.sound.media.MyJzvdStd;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

/**
 * Created by openapi on 2015/9/1.
 */
public class VideoShowActivityLazyUrl extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {

    private static final String VIDEO_TAG = "video_url_tag";
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public MyJzvdStd jzVideo;
    RecommendVo vo;
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;
    protected BaseService indexService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        indexService = new BaseService();
        indexService.setup(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        //需要单独发起解析url 的请求
        if(vo.needLazyUrl){
            try {
                ToastUtil.showShort(R.string.load);
                jzVideo.loadingInit();
                indexService.getData(Constants.getUrlWithParam(Constants.API_VIDEO_ITEM_URL, vo.id), new CommonResponse.ResponseOkListener<RecommendVo>() {
                    @Override
                    public void onResponse(RecommendVo o) {
                        super.onResponse(o);
                        openVideoShow(o);
                        jzVideo.closeLoadingInit();
                    }
                }, new CommonResponse.ResponseErrorListener(){
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        super.onErrorResponse(volleyError);
                        LogUtil.e(volleyError.getMessage(), volleyError);
                        jzVideo.closeLoadingInit();
                    }
                }, VIDEO_TAG, RecommendVo.class);

            }catch (Exception e){
                jzVideo.closeLoadingInit();
                openVideoShow(vo);
            }
        }else{//否则直接打开播放页面
            openVideoShow(vo);
        }
//        jzVideo.headData = header;
        jzVideo.posterImageView.setScaleType(ImageView.ScaleType.CENTER);
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.posterImageView);
//        Jzvd.setJzUserAction(new MyUserActionStandard());
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.actionUrl);
        myFavoriteView.showVideoLocal();
    }

    private void openVideoShow(RecommendVo urlVo){
        String url = urlVo.actionUrl;
        if(StringUtils.hasText(urlVo.urlToken)){
            url = url+urlVo.urlToken;
        }
        boolean playCore = PreferenceUtils.getPrefBoolean(this, Constants.PLAYCORE_SWITCH, true);
        if(!playCore) {
            jzVideo.setUp(urlVo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaExo.class);
        } else {
            jzVideo.setUp(urlVo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaIjk.class);
        }
        jzVideo.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(url, StringUtils.hasText(urlVo.baseurl) ? urlVo.baseurl : urlVo.actionUrl);
//
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

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite(Constants.FavoriteType.VIDEO);
    }

   //倍速功能
    @Override
    public boolean setSpeed(float speed) {
        return jzVideo.setSpeed(speed);
    }

    @Override
    public String getBrowserDatUrl() {
        return vo.actionUrl;
    }
//    class MyUserActionStandard implements JZUserActionStandard {
//
//        @Override
//        public void onEvent(int type, Object url, int screen, Object... objects) {
//            switch (type) {
//                case JZUserAction.ON_CLICK_PAUSE:
//                    AdsContext.showRand(VideoShowActivity.this);
//                    break;
//                case JZUserAction.ON_AUTO_COMPLETE:
//                    AdsContext.showRand(VideoShowActivity.this);
//                    break;
//                case JZUserAction.ON_CLICK_START_ERROR:
//                    ConnLogUtil.addLog(VideoShowActivity.this,vo.extra+"--"+vo.baseurl,vo.actionUrl,0);
//                    break;
//
//                default:break;
//            }
//        }
//    }
}
