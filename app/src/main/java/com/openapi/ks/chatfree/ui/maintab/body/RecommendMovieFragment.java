package com.openapi.ks.chatfree.ui.maintab.body;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.openapi.core.util.PackageUtils;
import com.openapi.core.ads.yewu.um.MobAgent;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.fragment.AppListFragment;
import com.openapi.ks.myapp.ui.fragment.RecommendFragment;
import com.openapi.ks.myapp.ui.fragment.TeleplayChannelFragment;
import com.openapi.ks.myapp.ui.fragment.VideoChannelListFragment;
import com.openapi.ks.chat.databinding.LayoutIndexFragmentBinding;


/**
 * Created by openapi on 2015/9/1.
 */
public class RecommendMovieFragment extends RecommendFragment {
    private static final String INDEX_TAG = "MovieRecommend_tag";
    RelativeLayout layout;

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_index_fragment, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        boolean install = PackageUtils.isPackageInstalled(getActivity(), Constants.VPN_PACKAGE);
        if (install || MyApplication.isTemp) {
            layout.setVisibility(View.GONE);
        }
    }

    public void downLoad(View view) {
        AppListFragment.startFragment(getActivity());
    }

    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        LayoutIndexFragmentBinding b = LayoutIndexFragmentBinding.bind(view);
        layout = (RelativeLayout) b.llVpnStatus.getRoot();
        b.llVpnStatus.getRoot().setOnClickListener(this::downLoad);
    }

    @Override
    protected void unbindViews() {
        layout = null;
        super.unbindViews();
    }


    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_MOVIE_URL, start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public int getSpanCount() {
        return 3;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }

    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if (!checkUserLevel(vo.type)) {
            return;
        }
        if (vo.param.startsWith(Constants.VIDEO_TV_CHANNEL)) {
            TeleplayChannelFragment.startFragment(getActivity(), vo);
        } else {
            VideoChannelListFragment.startFragment(getActivity(), vo);
        }

        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
}
