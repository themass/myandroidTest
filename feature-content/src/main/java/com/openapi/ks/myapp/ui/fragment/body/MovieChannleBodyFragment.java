package com.openapi.ks.myapp.ui.fragment.body;


import android.os.Bundle;
import android.os.Build;

import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.channel.ChannelBodyFragment;
import com.openapi.ks.myapp.ui.fragment.VideoChannelListFragment;
import com.openapi.ks.myapp.ui.fragment.VideoChannelUserListFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 */
public class MovieChannleBodyFragment extends ChannelBodyFragment {
    private static final String INDEX_TAG = "movie_tag";
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
        return Constants.getUrlWithParam(Constants.API_VIDEO_CHANNLE_URL, start, channel);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onChannelItemClick(RecommendVo vo) {
        // 影视频道用 param 字段判断用户列表（与视频频道的 actionUrl 不同，保持原行为）
        if (Constants.VIDEO_USER_CHANNEL.equals(vo.param)) {
            VideoChannelUserListFragment.startFragment(getActivity(), vo);
        } else {
            VideoChannelListFragment.startFragment(getActivity(), vo);
        }
    }

    @Override
    public int getSpanCount() {
        return 3;
    }
}
