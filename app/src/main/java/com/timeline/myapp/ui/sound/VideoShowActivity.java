package com.timeline.myapp.ui.sound;


import android.os.Bundle;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import cn.jzvd.JZUserAction;
import cn.jzvd.JZUserActionStandard;
import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerStandard;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoShowActivity extends LogActivity {
    private Unbinder unbinder;
    @BindView(R.id.jz_video)
    public JZVideoPlayerStandard jzVideo;
    RecommendVo vo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_video_show);
        unbinder = ButterKnife.bind(this);
        vo = (RecommendVo)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        HashMap<String,String> header = new HashMap<>();
        header.put("Referer",vo.actionUrl);
        jzVideo.setUp(vo.actionUrl, JZVideoPlayerStandard.SCREEN_LAYOUT_NORMAL, vo.title,header);
        jzVideo.headData = header;
        ImagePhotoLoad.loadCommonImg(this,vo.img,jzVideo.thumbImageView);
        JZVideoPlayer.setJzUserAction(new MyUserActionStandard());
    }
    @Override
    public void onPause() {
        super.onPause();
        JZVideoPlayer.releaseAllVideos();
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
        super.onDestroy();
        unbinder.unbind();
    }
    protected boolean enableSliding() {
        return true;
    }
    class MyUserActionStandard implements JZUserActionStandard {

        @Override
        public void onEvent(int type, String url, int screen, Object... objects) {
            switch (type) {
                case JZUserAction.ON_CLICK_PAUSE:
                    if(AdsContext.rateShow()){
                        AdsManager.getInstans().showInterstitialAds(VideoShowActivity.this, AdsContext.Categrey.CATEGREY_VPN3,false);
                    }                    break;
                default:break;
            }
        }
    }
}
