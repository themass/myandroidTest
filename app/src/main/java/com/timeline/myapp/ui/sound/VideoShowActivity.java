package com.timeline.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.sspacee.common.ui.view.FavoriteImageView;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ConnLogUtil;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.data.VideoUtil;
import com.timeline.vpn.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import cn.jzvd.JZUserAction;
import cn.jzvd.JZUserActionStandard;
import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerStandard;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoShowActivity extends AppCompatActivity {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JZVideoPlayerStandard jzVideo;
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
        Object[] source = VideoUtil.getVideoSource(url,false,com.sspacee.common.util.StringUtils.hasText(vo.baseurl)?vo.baseurl : vo.actionUrl);
        jzVideo.setUp(source,0, JZVideoPlayerStandard.SCREEN_WINDOW_NORMAL, vo.title);
//        jzVideo.headData = header;
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.thumbImageView);
        JZVideoPlayer.setJzUserAction(new MyUserActionStandard());
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
            JZVideoPlayer.goOnPlayOnPause();
        }catch (Throwable e){
            LogUtil.e(e);
        }
    }
    @Override
    public void onBackPressed() {
        if (JZVideoPlayer.backPress()) {
            return;
        }
        super.onBackPressed();
    }
    @Override
    public void onDestroy() {
        JZVideoPlayer.releaseAllVideos();
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
    class MyUserActionStandard implements JZUserActionStandard {

        @Override
        public void onEvent(int type, Object url, int screen, Object... objects) {
            switch (type) {
                case JZUserAction.ON_CLICK_PAUSE:
                    AdsContext.showRand(VideoShowActivity.this);
                    break;
                case JZUserAction.ON_AUTO_COMPLETE:
                    AdsContext.showRand(VideoShowActivity.this);
                    break;
                case JZUserAction.ON_CLICK_START_ERROR:
                    ConnLogUtil.addLog(VideoShowActivity.this,vo.extra+"--"+vo.baseurl,vo.actionUrl,0);
                    break;

                default:break;
            }
        }
    }
}
