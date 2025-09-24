package com.openapi.ks.chatfree.ui.maintab;


import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;

import com.openai.ads.yewu.ads.base.AdsContext;
import com.openapi.ks.myapp.ui.base.BannerHeaderFragment;
import com.openapi.ks.myapp.ui.base.features.TabBaseAdsFragment;

//import com.livekit.voiceassistant.LivekitVoiceAgentFragment;

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
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJBUElTQU5ObUVHQlF4ZlkiLCJleHAiOjE3NDEyMDkyNzAsInN1YiI6InFxcXExMTExIiwianRpIjoicXFxcTExMTEiLCJuYW1lIjoicXFxcTExMTEiLCJtZXRhZGF0YSI6Im1ldGFkYXRhIiwidmlkZW8iOnsicm9vbUpvaW4iOnRydWUsInJvb20iOiJxcXFxMTExMV9yb29tIn0sInNpcCI6e319.KlktqnktjO91l1bfFvIdWVgEyeYaIS2elxbyvFuwl6U";
//        return MainFragment.Companion.newInstance(url, token);
        return null;
    }
}
