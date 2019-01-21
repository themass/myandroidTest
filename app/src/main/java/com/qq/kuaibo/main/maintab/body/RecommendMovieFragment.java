package com.qq.kuaibo.main.maintab.body;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.qq.kb.R;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.yewu.um.MobAgent;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.ui.fragment.AppListFragment;
import com.qq.myapp.ui.fragment.RecommendFragment;
import com.qq.myapp.ui.fragment.TeleplayChannelFragment;
import com.qq.myapp.ui.fragment.VideoChannelListFragment;


import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendMovieFragment extends RecommendFragment {
    private static final String INDEX_TAG = "MovieRecommend_tag";
    @BindView(R.id.ll_vpn_status)
    RelativeLayout layout;
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_index_fragment, parent);
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        boolean install = PackageUtils.isPackageInstalled(getActivity(),Constants.VPN_PACKAGE);
        if(install || MyApplication.isTemp){
            layout.setVisibility(View.GONE);
        }
    }
    @OnClick(R.id.ll_vpn_status)
    public void downLoad(View view){
        AppListFragment.startFragment(getActivity());
    }
    @Override
    public String getUrl(int start) {
        return Constants.getUrlWithParam(Constants.API_RECOMMEND_MOVIE_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public int getSpanCount() {
        return 2;
    }

    @Override
    public boolean getShowEdit() {
        return false;
    }
    public void onCustomerItemClick(View v, int position) {
        RecommendVo vo = infoListVo.voList.get(position);
        if(!checkUserLevel(vo.type)){
            return;
        }
        if(vo.param.startsWith(Constants.VIDEO_TV_CHANNEL)){
            TeleplayChannelFragment.startFragment(getActivity(),vo);
        }else{
            VideoChannelListFragment.startFragment(getActivity(), vo);
        }

        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
}
