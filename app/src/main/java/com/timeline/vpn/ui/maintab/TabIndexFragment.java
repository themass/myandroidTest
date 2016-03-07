package com.timeline.vpn.ui.maintab;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.widget.ImageView;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.service.VpnStateService;
import com.timeline.vpn.ui.adapter.IndexNaviAdapter;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabIndexFragment extends TabBaseFragment implements VpnStateService.VpnStateListener{
    private static final String DIALOG_TAG = "Dialog";
    private static final int PREPARE_VPN_SERVICE = 0;
    @Nullable
    @Bind(R.id.rvNavi)
    RecyclerView rvNavi;
    IndexNaviAdapter adapter;
    @Bind(R.id.iv_vpn_state)
    ImageView ivVpnStatus;
    private AnimationDrawable anim = null;
    private Bundle mProfileInfo;

    @Override
    protected int getRootViewId() {
        return R.layout.tab_index;
    }

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.vpn_status_view;
    }

    @Override
    protected int getTabBodyViewId() {
        return R.layout.tab_index_body_content;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        adapter = new IndexNaviAdapter(this.getActivity());
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);
        rvNavi.setLayoutManager(layoutManager);
        rvNavi.setAdapter(adapter);
        rvNavi.setItemAnimator(new DefaultItemAnimator());
    }

    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        if (anim == null) {
            ivVpnStatus.setImageResource(R.drawable.cirle_animate);
            anim = (AnimationDrawable) ivVpnStatus.getDrawable();
        }
        if (anim.isRunning()) {
            anim.stop();
        } else {
            anim.start();

        }
    }

    protected void prepareVpnService(Bundle profileInfo) {
        Intent intent;
        try {
            intent = VpnService.prepare(getActivity());
        } catch (IllegalStateException ex) {
            /* this happens if the always-on VPN feature (Android 4.2+) is activated */
            VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported_during_lockdown);
            return;
        }
		/* store profile info until the user grants us permission */
        mProfileInfo = profileInfo;
        if (intent != null) {
            try {
                startActivityForResult(intent, PREPARE_VPN_SERVICE);
            } catch (ActivityNotFoundException ex) {
                VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported);
            }
        } else {	/* user already granted permission to use VpnService */
            onActivityResult(PREPARE_VPN_SERVICE, Activity.RESULT_OK, null);
        }
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Bundle profileInfo = new Bundle();
//        profileInfo.putLong(VpnProfileDataSource.KEY_ID, profile.getId());
//        profileInfo.putString(VpnProfileDataSource.KEY_USERNAME, profile.getUsername());
//        profileInfo.putString(VpnProfileDataSource.KEY_PASSWORD, profile.getPassword());
//        profileInfo.putBoolean(PROFILE_REQUIRES_PASSWORD, profile.getVpnType().has(VpnType.VpnTypeFeature.USER_PASS));
//        profileInfo.putString(PROFILE_NAME, profile.getName());
//        switch (requestCode) {
//            case PREPARE_VPN_SERVICE:
//                if (resultCode == Activity.RESULT_OK && profileInfo != null) {
//                    Intent intent = new Intent(getActivity(), CharonVpnService.class);
//                    intent.putExtras(mProfileInfo);
//                    getActivity().startService(intent);
//                }
//                break;
//            default:
//                super.onActivityResult(requestCode, resultCode, data);
//        }
//    }

    public static class VpnNotSupportedError extends DialogFragment {
        static final String ERROR_MESSAGE_ID = "org.strongswan.android.VpnNotSupportedError.MessageId";

        public static void showWithMessage(Activity activity, int messageId) {
            Bundle bundle = new Bundle();
            bundle.putInt(ERROR_MESSAGE_ID, messageId);
            VpnNotSupportedError dialog = new VpnNotSupportedError();
            dialog.setArguments(bundle);
            dialog.show(activity.getFragmentManager(), DIALOG_TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle arguments = getArguments();
            final int messageId = arguments.getInt(ERROR_MESSAGE_ID);
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.vpn_not_supported_title)
                    .setMessage(messageId)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    }).create();
        }
    }

    @Override
    public void stateChanged() {
        LogUtil.i("vpnstatelist  stateChanged");
    }
}
