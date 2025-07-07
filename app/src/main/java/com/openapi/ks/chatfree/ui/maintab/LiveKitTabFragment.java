package com.openapi.ks.chatfree.ui.maintab;


import androidx.fragment.app.Fragment;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.chatfree.ui.maintab.body.RecommendMovieFragment;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

/**
 * Created by openapi on 2015/9/1.
 */
public class LiveKitTabFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @Override
    protected Fragment getTabBodyView() {
//        String url = "wss://testvoice-b8y35yvd.livekit.cloud";
//        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Mzc3MTc5NTEsImlzcyI6IkFQSVNBTk5tRUdCUXhmWSIsIm5iZiI6MTczNzcxNDM1MSwic3ViIjoiZ3FsaSIsInZpZGVvIjp7ImNhblB1Ymxpc2giOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlLCJjYW5TdWJzY3JpYmUiOnRydWUsInJvb20iOiJ0ZXN0cm9vbSIsInJvb21Kb2luIjp0cnVlfX0.6AGUrSsmUreLkRp8jQjYsH_w3QnImKxOlfxc0bVC5tk";
//        return LivekitVoiceAgentFragment.Companion.newInstance(url, token);
        return new RecommendMovieFragment();
    }
}
