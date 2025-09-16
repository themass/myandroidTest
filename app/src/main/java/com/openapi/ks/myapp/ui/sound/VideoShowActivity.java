package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.ImageView;

import com.openai.commonevent.FavoriteVo;

import com.openai.commonview.view.MyFavoriteView;
import com.openai.commonutil.LogUtil;
import com.openai.commonutil.StringUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
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
public class VideoShowActivity extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public MyJzvdStd jzVideo;
    RecommendVo vo;
    @BindView(R.id.my_favoriteview)
    MyFavoriteView myFavoriteView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);

        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vo = getIntent().getSerializableExtra(Constants.CONFIG_PARAM, RecommendVo.class);
        } else {
            vo = (RecommendVo) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        }
        String url = vo.actionUrl;
        if (StringUtils.hasText(vo.urlToken)) {
            url = url + vo.urlToken;
        }

        jzVideo.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JzvdPlayerFactory.getPlayManager());
        jzVideo.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(url, StringUtils.hasText(vo.baseurl) ? vo.baseurl : vo.actionUrl);
//
//        jzVideo.headData = header;
        jzVideo.posterImageView.setScaleType(ImageView.ScaleType.CENTER);
        ImagePhotoLoad.loadCommonImg(this, vo.img, jzVideo.posterImageView);
//        Jzvd.setJzUserAction(new MyUserActionStandard());
        myFavoriteView.setListener(this);
        myFavoriteView.initFavoriteBackGroud(vo.actionUrl);
        myFavoriteView.showVideoLocal();
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
