package com.timeline.vpn.ui.fragment;


import android.view.View;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

import org.strongswan.android.logic.VpnStateService;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendVipFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Vip_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getVIP_URL(start);
    }

    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    public void onItemClick(View v, int position) {
        //关于
        if (position == 0) {
            super.onItemClick(v, position);
            return;
        }
        //非VIP用户可以使用，但是有广告控制，VIP无广告
//        if(!UserLoginUtil.isVIP()){
//            Toast.makeText(getActivity(), R.string.vip_need,Toast.LENGTH_LONG).show();
//            return;
//        }
        if (mService != null) {
            if (!VpnStateService.State.CONNECTED.equals(mService.getState())) {
                Toast.makeText(getActivity(), R.string.vpn_need, Toast.LENGTH_SHORT).show();
            }
        }
        super.onItemClick(v, position);
    }
}
