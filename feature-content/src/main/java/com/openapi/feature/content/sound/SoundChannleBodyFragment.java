package com.openapi.feature.content.sound;


import android.os.Bundle;
import android.os.Build;
import android.view.View;

import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.feature.content.common.RecommendFragment;
import com.openapi.feature.content.sound.SoundItemsMusicFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 */
public class SoundChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "SoundChannle_tag";
    private String channel = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getArguments();
        HashMap<String, String> configParam = null;
        if (b != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                configParam = b.getSerializable(Constants.CONFIG_PARAM, HashMap.class);
            } else {
                configParam = (HashMap<String, String>) b.getSerializable(Constants.CONFIG_PARAM);
            }
        }
        if (configParam != null) {
            String c = configParam.get(Constants.CHANNEL);
            channel = c != null ? c : "";
        }
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_SOUND_CHANNLE_URL, start, channel);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!checkUserLevel(vo.type)) {
            return;
        }
        SoundItemsMusicFragment.startFragment(getActivity(), vo);
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

}
