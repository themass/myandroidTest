package com.timeline.vpn.ui.fragment;


import android.view.View;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;

import org.strongswan.android.logic.VpnStateService;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendListFragment extends RecommendFragment {
    private static final String INDEX_TAG = "Recommend_tag";

    @Override
    public String getUrl(int start) {
        return Constants.getRECOMMEND_URL(start);
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
        super.onItemClick(v, position);
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
