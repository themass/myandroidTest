package com.timeline.sexfree1.ui.maintab.body;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.qq.sexfree.R;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.PackageUtils;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.base.MyApplication;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.config.ConfigActionEvent;
import com.timeline.myapp.ui.fragment.AppListFragment;
import com.timeline.myapp.ui.fragment.RecommendFragment;
import com.timeline.myapp.ui.fragment.TeleplayChannelFragment;
import com.timeline.myapp.ui.fragment.VideoChannelListFragment;
import com.timeline.myapp.ui.fragment.VideoChannelUserListFragment;


import java.util.HashMap;
import java.util.Map;

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
            if(Constants.VIDEO_USER_CHANNEL.equals(vo.actionUrl)){
                VideoChannelUserListFragment.startFragment(getActivity(),vo);
            }else if(Constants.VIDEO_LIST_CHANNEL.equals(vo.actionUrl)){
                Map<String, Object> param = new HashMap<>();
                param.put(Constants.ADS_SHOW_CONFIG, vo.adsShow);
                param.put(Constants.ADS_POP_SHOW_CONFIG, vo.adsPopShow);
                String actionUrl = "videoList://config?channel="+vo.param;
                EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), actionUrl, vo.title, param));
            }else{
                VideoChannelListFragment.startFragment(getActivity(), vo);
            }
        }

        MobAgent.onEventRecommondChannel(getActivity(), vo.title);
    }
}
