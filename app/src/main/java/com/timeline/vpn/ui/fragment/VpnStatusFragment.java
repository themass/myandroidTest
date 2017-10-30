package com.timeline.vpn.ui.fragment;


import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.ui.base.BaseFragment;
import com.timeline.vpn.R;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2015/9/1.
 */
public class VpnStatusFragment extends BaseFragment {
    @BindView(R.id.tv_vpn_state_text)
    TextView tvVpnText;
    @BindView(R.id.iv_vpn_state)
    ImageButton ibVpnStatus;

    @Override
    protected int getRootViewId() {
        return R.layout.vpn_state_view_loading;
    }

    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        Toast.makeText(getActivity(),"请在“设置”里下载最新版本!",Toast.LENGTH_SHORT).show();
    }
}
