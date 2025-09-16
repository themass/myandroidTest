package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.VolleyError;
import com.openai.commonview.view.MyFavoriteView;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.StringUtils;
import com.openai.commonutil.ToastUtil;
import com.openai.net.request.CommonResponse;
import com.openapi.ks.chat.R;
import com.openai.commonevent.FavoriteVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openai.net.BaseService;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.VideoUtil;
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
    private String url;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        indexService = new BaseService();
        indexService.setup(this);
        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vo = getIntent().getSerializableExtra(Constants.CONFIG_PARAM, RecommendVo.class);
        } else {
            vo = (RecommendVo) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        }
        url = vo.actionUrl;
        LogUtil.i("data="+vo);
        //需要单独发起解析url 的请求
        if (vo.needLazyUrl) {
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
                }, new CommonResponse.ResponseErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        super.onErrorResponse(volleyError);
                        LogUtil.e(volleyError.getMessage(), volleyError);
                        jzVideo.closeLoadingInit();
                        openVideoShow(vo);
                    }
                }, VIDEO_TAG, RecommendVo.class);

            } catch (Exception e) {
                jzVideo.closeLoadingInit();
                openVideoShow(vo);
            }
        } else {//否则直接打开播放页面
            openVideoShow(vo);
        }
//        jzVideo.headData = header;
        jzVideo.posterImageView.setScaleType(ImageView.ScaleType.CENTER);
        ImagePhotoLoad.loadCommonImg(this, vo.img, jzVideo.posterImageView);
//        Jzvd.setJzUserAction(new MyUserActionStandard());
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.actionUrl);
        myFavoriteView.showVideoLocal();
    }

    private void openVideoShow(RecommendVo urlVo) {
        url = urlVo.actionUrl;
        if (StringUtils.hasText(urlVo.urlToken)) {
            url = url + urlVo.urlToken;
        }
        jzVideo.setUp(urlVo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JzvdPlayerFactory.getPlayManager());
        jzVideo.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(url, StringUtils.hasText(urlVo.baseurl) ? urlVo.baseurl : urlVo.actionUrl);
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            Jzvd.goOnPlayOnPause();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        // 使用现代API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        indexService.cancelRequest(VIDEO_TAG);
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
    public String getBrowserDataUrl() {
        return url;
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
