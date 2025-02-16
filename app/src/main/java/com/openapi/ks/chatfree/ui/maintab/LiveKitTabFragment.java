package com.openapi.ks.chatfree.ui.maintab;


import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

import com.livekit.voiceassistant.LivekitVoiceAgentFragment;

import io.livekit.android.annotations.Beta;

/**
 * Created by openapi on 2015/9/1.
 */
public class LiveKitTabFragment extends TabBaseAdsFragment {

    @Override
    protected Fragment getTabHeaderView() {
        return BannerHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN1);
    }

    @OptIn(markerClass = Beta.class)
    @Override
    protected Fragment getTabBodyView() {
        String url = "wss://testvoice-b8y35yvd.livekit.cloud";
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Mzk3NDc5ODEsImlzcyI6IkFQSVNBTk5tRUdCUXhmWSIsIm5iZiI6MTczOTcxMTk4MSwic3ViIjoiZ3FsaSIsInZpZGVvIjp7ImNhblB1Ymxpc2giOnRydWUsImNhblB1Ymxpc2hEYXRhIjp0cnVlLCJjYW5TdWJzY3JpYmUiOnRydWUsInJvb20iOiJ0ZXN0cm9vbSIsInJvb21Kb2luIjp0cnVlfX0.yhbyzeUrCLhvwbY9cmF-XMS6SqR5fLM8LjKryMLT7-0";
        return LivekitVoiceAgentFragment.Companion.newInstance(url, token);
    }
}
