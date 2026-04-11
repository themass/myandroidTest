package com.openapi.ks.myapp.ui.fragment.body;


import android.os.Bundle;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.openai.net.request.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.fragment.ImgChannelImgListFragment;
import com.openapi.ks.myapp.ui.fragment.RecommendFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 */
public class ImgChannleBodyFragment extends RecommendFragment {
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
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!checkUserLevel(vo.type)) {
            return;
        }
        ImgChannelImgListFragment.startFragment(getActivity(), vo);
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    protected RecyclerView.LayoutManager createRecommendLayoutManager() {
        return createIrregularWidthImageGridLayout();
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }


    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
    }
}
