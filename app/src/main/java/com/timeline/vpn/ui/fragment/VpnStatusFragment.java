package com.timeline.vpn.ui.fragment;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.net.HttpUtils;
import com.sspacee.common.net.request.CommonResponse;
import com.sspacee.common.ui.base.BaseFragment;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.DataBuilder;
import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.bean.vo.ServerVo;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.LocationUtil;

import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.imc.ImcState;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by themass on 2015/9/1.
 */
public class VpnStatusFragment extends BaseFragment implements VpnStateService.VpnStateListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "vpn_status_tag";
    private static final int PREPARE_VPN_SERVICE = 0;
    private static boolean isAnim = false;
    @Bind(R.id.tv_vpn_state_text)
    TextView tvVpnText;
    @Bind(R.id.iv_vpn_state)
    ImageButton ibVpnStatus;
    PingTask task;
    private volatile boolean hasIp = false;
    CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            imgError();
        }
    };
    private VpnStateService mService;
    private Handler handler = new Handler();
    private VpnProfile vpnProfile;
    CommonResponse.ResponseOkListener serverListener = new CommonResponse.ResponseOkListener<ServerVo>() {
        @Override
        public void onResponse(ServerVo serverVo) {
            if (serverVo.hostList != null) {
                if (serverVo.hostList.size() == 1) {
                    LogUtil.i("one host start ready");
                    startVpn(serverVo, serverVo.hostList.get(0));
                } else {
                    LogUtil.i("many host start task ping");
                    for (HostVo vo : serverVo.hostList) {
                        task = new PingTask(serverVo);
                        LogUtil.i("run task " + vo.gateway);
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vo);
                    }
                }
            }

        }
    };
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.i("VpnStateService->onServiceConnected");
            mService = ((VpnStateService.LocalBinder) service).getService();
            mService.registerListener(VpnStatusFragment.this);
            vpnProfile = mService.getProfile();
            stateChanged();
            LogUtil.i(mService.getState() + "");
        }
    };
    private Animation operatingAnim = null;
    private LinearInterpolator lir = null;
    private BaseService indexService;

    @Override
    protected int getRootViewId() {
        return R.layout.vpn_state_view_loading;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        operatingAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.vpn_state_loading);
        lir = new LinearInterpolator();
        operatingAnim.setInterpolator(lir);
        indexService = new BaseService();
        indexService.setup(getActivity());
        getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        mService.disconnect();
        if (mService != null)
            mService.unregisterListener(this);
        getActivity().unbindService(mServiceConnection);
        indexService.cancelRequest(INDEX_TAG);
        if (task != null) {
            task.cancel(true);
        }

    }

    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        if (mService != null) {
            LogUtil.i("onVpnClick " + mService.getState());
            if (mService.getState() == VpnStateService.State.CONNECTED) {
                mService.disconnect();
            } else if (mService.getState() == VpnStateService.State.DISABLED) {
                imgAnim();
                int id = LocationUtil.getSelectId(getActivity());
                indexService.getData(String.format(Constants.getUrl(Constants.API_SERVERLIST_URL), id), serverListener, serverListenerError, INDEX_TAG, ServerVo.class);
            } else {
                Toast.makeText(getActivity(), R.string.vpn_bg_click_later, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void stateChanged() {
        if (mService != null)
            handler.post(new StatChangeJob(mService.getState(), mService.getErrorState(), mService.getImcState()));
    }

    /**
     * Prepare the VpnService. If this succeeds the current VPN profile is
     * started.
     */
    protected void prepareVpnService() {
        Intent intent;
        try {
            intent = VpnService.prepare(getActivity());
        } catch (IllegalStateException ex) {
            /* this happens if the always-on VPN feature (Android 4.2+) is activated */
            VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported_during_lockdown);
            return;
        }
        /* store profile info until the user grants us permission */
        if (intent != null) {
            try {
                startActivityForResult(intent, PREPARE_VPN_SERVICE);
            } catch (ActivityNotFoundException ex) {
                /* it seems some devices, even though they come with Android 4,
                 * don't have the VPN components built into the system image.
				 * com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog
				 * will not be found then */
                VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported);
            }
        } else {	/* user already granted permission to use VpnService */
            onActivityResult(PREPARE_VPN_SERVICE, Activity.RESULT_OK, null);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PREPARE_VPN_SERVICE:
                if (resultCode == Activity.RESULT_OK && mService != null) {
                    mService.connect(vpnProfile);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void imgAnim() {
        if (!isAnim) {
            stopAnim();
            isAnim = true;
            hasIp = false;
            ibVpnStatus.setBackgroundResource(R.drawable.vpn_ic_loading);
            ibVpnStatus.startAnimation(operatingAnim);
            tvVpnText.setText(R.string.vpn_bg_conning);
            ibVpnStatus.setEnabled(false);
        }
    }

    private void imgDisAnim() {
        if (!isAnim) {
            stopAnim();
            isAnim = true;
            hasIp = false;
            ibVpnStatus.setBackgroundResource(R.drawable.vpn_ic_dising);
            ibVpnStatus.startAnimation(operatingAnim);
            tvVpnText.setText(R.string.vpn_bg_disconning);
            ibVpnStatus.setEnabled(false);
        }
    }

    private void imgError() {
        stopAnim();
        hasIp = false;
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_ic_error);
        tvVpnText.setText(R.string.vpn_bg_error);
        ibVpnStatus.setEnabled(true);
    }

    private void imgConn() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_ic_conn);
        tvVpnText.setText(R.string.vpn_bg_conned);
        ibVpnStatus.setEnabled(true);
    }

    private void imgNormal() {
        stopAnim();
        hasIp = false;
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_state_bg);
        tvVpnText.setText(R.string.vpn_bg_click);
        ibVpnStatus.setEnabled(true);
    }

    private void stopAnim() {
        if(ibVpnStatus!=null) {
            isAnim = false;
            ibVpnStatus.clearAnimation();
        }
    }

    public void startVpn(ServerVo server, HostVo vo) {
        synchronized (mService) {
            if (!hasIp) {
                hasIp = true;
                vpnProfile = DataBuilder.builderVpnProfile(server.expire, server.name, server.pwd, vo);
                prepareVpnService();
            }
        }
    }

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

    public class PingTask extends AsyncTask<HostVo, Void, HostVo> {
        private ServerVo server;

        public PingTask(ServerVo server) {
            this.server = server;
        }

        @Override
        protected HostVo doInBackground(HostVo... params) {
            HostVo vo = params[0];
            vo.ttlTime = HttpUtils.ping(vo.gateway);
            LogUtil.i(vo.toString());
            if (vo.ttlTime > 0) {
                startVpn(server, vo);
            }
            return vo;
        }

        @Override
        protected void onPostExecute(HostVo hostVo) {
            super.onPostExecute(hostVo);
            if (ibVpnStatus != null)
                ibVpnStatus.setEnabled(true);
        }
    }

    public class StatChangeJob implements Runnable {
        private VpnStateService.State state;
        private VpnStateService.ErrorState errorState;
        private ImcState imcState;

        public StatChangeJob(VpnStateService.State state, VpnStateService.ErrorState errorState, ImcState imcState) {
            this.state = state;
            this.errorState = errorState;
            this.imcState = imcState;
        }

        public boolean hasError() {
            if (errorState == VpnStateService.ErrorState.NO_ERROR) {
                return false;
            }
            switch (errorState) {
                case AUTH_FAILED:
                    if (imcState == ImcState.BLOCK) {
                        Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_assessment_failed, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_auth_failed, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case PEER_AUTH_FAILED:
                    Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_peer_auth_failed, Toast.LENGTH_SHORT).show();
                    break;
                case LOOKUP_FAILED:
                    Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_lookup_failed, Toast.LENGTH_SHORT).show();
                    break;
                case UNREACHABLE:
                    Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_unreachable, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(VpnStatusFragment.this.getActivity(), R.string.error_generic, Toast.LENGTH_SHORT).show();
                    break;
            }
            return true;
        }

        @Override
        public void run() {
            LogUtil.i("vpn stateChanged stateChanged " + state + "  ;errorState=" + errorState + " ;imcState=" + imcState);
            if (hasError()) {
                imgError();
                hasIp = false;
                return;
            }
            switch (state) {
                case CONNECTED:
                    imgConn();
                    break;
                case CONNECTING:
                    imgAnim();
                    break;
                case DISCONNECTING:
//                    imgAnim();
                    break;
                case DISABLED:
                    imgNormal();
                    hasIp = false;
                    break;
                default:
                    imgError();
                    hasIp = false;
                    break;
            }
        }
    }
}
