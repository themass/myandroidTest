package com.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

import com.ks.myapp.ui.base.app.BaseToolBarActivity;
import com.ks.myapp.ui.sound.media.JZMediaExo;
import com.ks.myapp.ui.sound.media.JZMediaIjk;
import com.ks.myapp.ui.user.SettingActivity;
import com.sspacee.common.ui.view.FavoriteImageView;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.StringUtils;
import com.ks.sexfree1.R;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.data.ImagePhotoLoad;
import com.ks.myapp.data.VideoUtil;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoShowActivity extends AppCompatActivity {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JzvdStd jzVideo;
    RecommendVo vo;
    @BindView(R.id.iv_favorite)
    FavoriteImageView ivFavorite;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        String url = vo.actionUrl;
        if(StringUtils.hasText(vo.urlToken)){
            url = url+vo.urlToken;
        }

        boolean playCore = PreferenceUtils.getPrefBoolean(this, Constants.PLAYCORE_SWITCH, true);
        if(!playCore) {
            jzVideo.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaExo.class);
        } else {
            jzVideo.setUp(vo.actionUrl, vo.title, JzvdStd.SCREEN_NORMAL, JZMediaIjk.class);
        }
        jzVideo.jzDataSource.headerMap = VideoUtil.getVideoSourceHeader(url, com.sspacee.common.util.StringUtils.hasText(vo.baseurl) ? vo.baseurl : vo.actionUrl);
//
//        jzVideo.headData = header;
        jzVideo.posterImageView.setScaleType(ImageView.ScaleType.CENTER);
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.posterImageView);
//        Jzvd.setJzUserAction(new MyUserActionStandard());
        ivFavorite.initSrc(vo.actionUrl);

    }
    @OnClick(R.id.iv_favorite)
    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(vo.tofavorite(Constants.FavoriteType.VIDEO));
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
