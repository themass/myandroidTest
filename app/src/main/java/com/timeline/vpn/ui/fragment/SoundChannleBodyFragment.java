package com.timeline.vpn.ui.fragment;


import android.view.View;
import android.widget.Toast;

import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import org.strongswan.android.logic.VpnStateService;

/**
 * Created by themass on 2015/9/1.
 */
public class SoundChannleBodyFragment extends RecommendFragment {
    private static final String INDEX_TAG = "SoundChannle_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getPage_URL(Constants.API_SOUND_CHANNLE_URL,start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        if (mService != null) {
            if (!VpnStateService.State.CONNECTED.equals(mService.getState())) {
                Toast.makeText(getActivity(), R.string.vpn_need, Toast.LENGTH_SHORT).show();
            }
        }
        RecommendVo vo = infoVo.voList.get(position);
        SoundItemsFragment.startFragment(getActivity(),vo);
        MobAgent.onEventRecommond(getActivity(), vo.title);
//        StaticDataUtil.add(Constants.SOUND_CHANNEL,infoVo.voList.get(position));
//        startActivity(SoundListActivity.class);
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
