package com.openapi.feature.content.image;


import android.os.Bundle;
import android.os.Build;

import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.feature.content.common.ChannelBodyFragment;
import com.openapi.feature.content.image.ImgChannelImgListFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 */
public class ImgChannleBodyFragment extends ChannelBodyFragment {
    private static final String INDEX_TAG = "img_tag";
    private String channel = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getArguments();
        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        HashMap<String, String> configParam;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            configParam = b.getSerializable(Constants.CONFIG_PARAM, HashMap.class);
        } else {
            configParam = (HashMap<String, String>) b.getSerializable(Constants.CONFIG_PARAM);
        }
        channel = configParam.get(Constants.CHANNEL);
    }

    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_IMG_CHANNLE_URL, start, channel);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onChannelItemClick(RecommendVo vo) {
        ImgChannelImgListFragment.startFragment(getActivity(), vo);
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean isIrregularGrid() {
        return true;
    }
}
