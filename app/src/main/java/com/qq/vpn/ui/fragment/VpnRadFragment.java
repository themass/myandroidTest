package com.qq.vpn.ui.fragment;


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
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.king.view.radarview.RadarView;
import com.qq.BeanBuilder;
import com.qq.Constants;
import com.qq.MobAgent;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ext.network.HttpUtils;
import com.qq.ext.network.req.CommonResponse;
import com.qq.ext.util.IpUtil;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.PermissionHelper;
import com.qq.ext.util.SystemUtils;
import com.qq.ext.util.ToastUtil;
import com.qq.fq3.R;
import com.qq.vpn.domain.res.HostVo;
import com.qq.vpn.domain.res.ServerVo;
import com.qq.vpn.domain.res.VpnProfile;
import com.qq.vpn.support.LocationUtil;
import com.qq.vpn.support.NetApiUtil;
import com.qq.vpn.support.StaticDataUtil;
import com.qq.vpn.support.UserLoginUtil;
import com.qq.vpn.support.config.VpnClickEvent;
import com.qq.vpn.ui.base.fragment.BaseFragment;
import com.qq.vpn.ui.base.fragment.NativeHeaderFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.imc.ImcState;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by dengt on 2015/9/1.
 */
public class VpnRadFragment extends BaseFragment implements VpnStateService.VpnStateListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "vpn_status_tag";
    private static final int PREPARE_VPN_SERVICE = 0;
    protected NetApiUtil api;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };
    VpnCheckTask vpnCheck=    new VpnCheckTask();
    @BindView(R.id.rl_content_native)
    LinearLayout rlContentNative;
    @BindView(R.id.rl_content)
    LinearLayout rlContent;
    @BindView(R.id.vpn)
    RadarView vpn;
    @BindView(R.id.tv_vpn_state_text)
    TextView tvVpnText;
    @BindView(R.id.iv_vpn_state)
    ImageButton ibVpnStatus;
    @BindView(R.id.vpn_but)
    RelativeLayout vpnBut;
    PingTask task;
    StatChangeJob job = null;
    private LinearInterpolator lir = null;
    CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            mHandler.removeCallbacks(vpnCheck);
            imgError();
        }
    };
    private VpnStateService mService;
    private Handler handler = new Handler();
    private VpnProfile vpnProfile;
    CommonResponse.ResponseOkListener serverListener = new CommonResponse.ResponseOkListener<ServerVo>() {
        @Override
        public void onResponse(ServerVo serverVo) {
            mHandler.removeCallbacks(vpnCheck);
            LogUtil.i("removeCallbacks check");
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
            mService.registerListener(VpnRadFragment.this);
            vpnProfile = mService.getProfile();
            stateChanged();
            LogUtil.i(mService.getState() + "");
        }
    };

    @Override
    protected int getRootViewId() {
        return R.layout.vpn_rad_loading;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        api = new NetApiUtil(getActivity());
        lir = new LinearInterpolator();
        getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        showBanner();
    }
    public void showBanner(){
        AdsManager.getInstans().showBannerAds(getActivity(), rlContent, AdsContext.Categrey.CATEGREY_VPN);
//        FragmentManager fm = getChildFragmentManager();
//        Fragment fragment = fm.findFragmentById(R.id.rl_content_native);
//        if (fragment == null) {
//            Fragment header = NativeHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN3);
//            if (header == null) {
//                rlContentNative.setVisibility(View.GONE);
//            } else {
//                fm.beginTransaction().replace(R.id.rl_content_native, header).commitAllowingStateLoss();
//            }
//        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        api.cancelRequest(INDEX_TAG);
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
        }
        if (mService != null)
            mService.unregisterListener(this);
        if (job != null) {
            handler.removeCallbacks(job);
        }
        getActivity().unbindService(mServiceConnection);


    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(VpnClickEvent event) {
        onVpnClick(null);
    }
    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        if(!PermissionHelper.checkPermissions(getActivity())) {
            PermissionHelper.showPermit(getActivity());
            return ;
        }
        if(SystemUtils.isZH(getActivity())) {
            ToastUtil.showLong("中国地区无法使用，请下载灯塔VPN 或者 FreeVPN");
            return;
        }
        if (mService != null) {
            LogUtil.i("onVpnClick " + mService.getState());
            if (mService.getState() == VpnStateService.State.CONNECTED) {
                mService.disconnect();
            } else if (mService.getState() == VpnStateService.State.DISABLED) {
                MobAgent.onEventLocationChoose(getActivity(), LocationUtil.getName(getActivity()));
                if(!UserLoginUtil.isVIP3())
                    AdsContext.showRand(getActivity());
                int id = LocationUtil.getSelectLocationId(getActivity());
                api.getData(String.format(Constants.getUrl(Constants.API_SERVERLIST_URL), id), serverListener, serverListenerError, INDEX_TAG, ServerVo.class);
                mHandler.postDelayed(vpnCheck,Constants.VPN_CHECK_TIME);
                imgAnim();
            } else {
                ToastUtil.showShort( R.string.vpn_click_later);
            }
        }else{
            getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                    mServiceConnection, Service.BIND_AUTO_CREATE);
            imgNormal();
        }
    }

    @Override
    public void stateChanged() {
        if (mService != null) {
            if (job != null) {
                handler.removeCallbacks(job);
            }
            job = new StatChangeJob(mService.getState(), mService.getErrorState(), mService.getImcState());
            handler.post(job);
        }

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
        vpn.setVisibility(View.VISIBLE);
        vpn.start();
        vpnBut.setVisibility(View.GONE);
    }

    private void imgError() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_icon_err);
        tvVpnText.setText(R.string.vpn_error);
    }

    private void imgConn() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_icon_conn);
        tvVpnText.setText(R.string.vpn_conned);
    }

    private void imgNormal() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(R.drawable.vpn_state_bg);
        tvVpnText.setText(R.string.vpn_click);
    }

    private void stopAnim() {
        vpnBut.setVisibility(View.VISIBLE);
        vpn.setVisibility(View.GONE);
    }

    public void startVpn(ServerVo server, HostVo vo) {
        synchronized (mService) {
            vpnProfile = BeanBuilder.builderVpnProfile(server.expire, server.name, server.pwd, vo);
            prepareVpnService();
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

        @Override
        protected HostVo doInBackground(HostVo... params) {
            HostVo vo = params[0];
            try {
                vo.ttlTime = HttpUtils.ping(vo.gateway);
                LogUtil.i(vo.toString());
                if (vo.ttlTime > 0) {
                    if(mService!=null && mService.getState() == VpnStateService.State.DISABLED)
                         startVpn(server, vo);
                }
            }catch (Exception e){
                LogUtil.e(e);
            }
            return vo;
        }

        public PingTask(ServerVo server) {
            this.server = server;
        }

        @Override
        protected void onPostExecute(HostVo hostVo) {
            super.onPostExecute(hostVo);
            if (vpn != null)
                vpn.setClickable(true);
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
                        ToastUtil.showShort(R.string.error_assessment_failed);
                    } else {
                        ToastUtil.showShort( R.string.error_auth_failed);
                    }
                    break;
                case PEER_AUTH_FAILED:
                    ToastUtil.showShort( R.string.error_peer_auth_failed);
                    break;
                case LOOKUP_FAILED:
                    ToastUtil.showShort( R.string.error_lookup_failed);
                    break;
                case UNREACHABLE:
                    ToastUtil.showShort(R.string.error_unreachable);
                    break;
                default:
                    ToastUtil.showShort(R.string.error_generic);
                    break;
            }
            return true;
        }

        @Override
        public void run() {
            LogUtil.i("vpn stateChanged stateChanged " + state + "  ;errorState=" + errorState + " ;imcState=" + imcState);
            if (hasError()) {
                imgError();
                return;
            }
            switch (state) {
                case CONNECTED:
                    StaticDataUtil.add(Constants.VPN_STATUS,1);
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
                    StaticDataUtil.del(Constants.VPN_STATUS);
                    break;
                default:
                    imgError();
                    StaticDataUtil.del(Constants.VPN_STATUS);
                    break;
            }
        }
    }
    class VpnCheckTask implements Runnable {
        @Override
        public void run() {
            api = new NetApiUtil(getActivity());
            getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                    mServiceConnection, Service.BIND_AUTO_CREATE);
            imgError();
        }
    }
}
