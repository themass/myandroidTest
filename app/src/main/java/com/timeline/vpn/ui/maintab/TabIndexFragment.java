package com.timeline.vpn.ui.maintab;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.volley.Response;
import com.timeline.vpn.R;
import com.timeline.vpn.api.bean.DataBuilder;
import com.timeline.vpn.api.bean.HostVo;
import com.timeline.vpn.api.bean.ServerVo;
import com.timeline.vpn.api.bean.VpnProfile;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.VolleyUtils;
import com.timeline.vpn.common.net.request.GsonRequest;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.IndexService;
import com.timeline.vpn.service.CharonVpnService;
import com.timeline.vpn.ui.adapter.IndexRecommendAdapter;
import com.timeline.vpn.ui.base.LoadableTabFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabIndexFragment extends LoadableTabFragment<List<String>> implements CharonVpnService.VpnStateListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final int PREPARE_VPN_SERVICE = 0;
    @Nullable
    @Bind(R.id.rvNavi)
    RecyclerView rvRecommend;
    @Bind(R.id.tv_vpn_state_text)
    TextView tvVpnText;
    @Bind(R.id.iv_vpn_state)
    ImageButton ibVpnStatus;
    @Bind(R.id.swipeLayout)
    SwipeRefreshLayout refreshLayout;
    private IndexRecommendAdapter adapter;
    private volatile boolean hasIp = false;
    private CharonVpnService mService;
    private Handler handler = new Handler();
    private VpnProfile vpnProfile;
    private Animation operatingAnim = null ;
    private LinearInterpolator lir = null;
    private IndexService indexService;
    private List<String> mData = new ArrayList<String>();
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((CharonVpnService.LocalBinder) service).getService();
            mService.registerListener(TabIndexFragment.this);
        }
    };

    @Override
    protected int getRootViewId() {
        return R.layout.tab_index;
    }

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.vpn_state_view_loading;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent) {
        inflater.inflate(R.layout.tab_index_body_content, parent, true);
    }

    @Override
    protected void onDataLoaded(List<String> data) {
        mData.addAll(data);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected List<String> loadData(Context context) throws Exception {
        return Arrays.asList(context.getResources().getStringArray(R.array.user_photos));
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        adapter = new IndexRecommendAdapter(this.getActivity(), rvRecommend,mData);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        rvRecommend.setLayoutManager(layoutManager);
        rvRecommend.setAdapter(adapter);
        rvRecommend.setItemAnimator(new DefaultItemAnimator());
        getActivity().bindService(new Intent(getActivity(), CharonVpnService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        operatingAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.vpn_state_loading);
        lir = new LinearInterpolator();
        operatingAnim.setInterpolator(lir);
        indexService = new IndexService();
        indexService.setup(getActivity());
        // 模拟下拉刷新
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);
                        adapter.notifyDataSetChanged();
                    }
                }, 2000);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mService.unregisterListener(this);
        getActivity().unbindService(mServiceConnection);

    }
    private void initRecommendList(){
        GsonRequest request = new GsonRequest(getActivity(), Constants.SERVERLIST_URL, ServerVo.class, HttpUtils.getHeader(),serverListener,serverListenerError);
        VolleyUtils.addRequest(request);
    }
    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        LogUtil.i("onVpnClick " + mService.getCurrentVpnState().name());
        if (mService.getCurrentVpnState()== CharonVpnService.State.CONNECTED) {
            mService.stopCurrentConnection();
            imgNormal();
        } else if(mService.getCurrentVpnState()== CharonVpnService.State.DISABLED) {
            imgAnim();
            indexService.getHost(serverListener,serverListenerError);
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
                if (resultCode == Activity.RESULT_OK) {
                    mService.startConnection(vpnProfile);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void imgAnim() {
        stopAnim();
        ibVpnStatus.setImageResource(R.drawable.icon_ivpn_state_loading);
        ibVpnStatus.startAnimation(operatingAnim);
        tvVpnText.setText(R.string.vpn_bg_conning);
    }
    private void imgError() {
        stopAnim();
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_red);
        tvVpnText.setText(R.string.vpn_bg_error);
    }
    private void imgConn() {
        stopAnim();
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_green);
        tvVpnText.setText(R.string.vpn_bg_conned);
    }
    private void imgNormal() {
        stopAnim();
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_blue);
        tvVpnText.setText(R.string.vpn_bg_click);
    }
    private void stopAnim(){
        ibVpnStatus.clearAnimation();
    }
    Response.Listener serverListener = new Response.Listener<ServerVo>() {
        @Override
        public void onResponse(ServerVo serverVo) {
            long id = 0;
            for (HostVo vo : serverVo.getHostList()) {
                PingTask task = new PingTask(serverVo);
                LogUtil.i("run task " + vo.getGateway());
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vo);
            }
        }
    };
    BaseService.ResponseErrorListener serverListenerError = new BaseService.ResponseErrorListener(){
        @Override
        protected void onError() {
            super.onError();
            imgError();
        }
    };
    public class PingTask extends AsyncTask<HostVo, Void, HostVo> {
        private ServerVo server;
        public PingTask(ServerVo server) {
            this.server = server;
        }
        @Override
        protected HostVo doInBackground(HostVo... params) {
            HostVo vo = params[0];
            int ttl = HttpUtils.ping(vo.getGateway());
            vo.setTtlTime(ttl);
            LogUtil.i(vo.toString());
            synchronized (mService) {
                if (!hasIp && vo.getTtlTime() > 0) {
                    hasIp = true;
                    vpnProfile = DataBuilder.builderVpnProfile(server.getExpire(),server.getName(),server.getPwd(),vo);
                    prepareVpnService();
                }
            }
            return vo;
        }
    }

    public class StatChangeJob implements Runnable {
        private CharonVpnService.State state;
        private CharonVpnService.ErrorState errorState;
        public StatChangeJob(CharonVpnService.State state, CharonVpnService.ErrorState errorState) {
            this.state = state;
            this.errorState = errorState;
        }
        @Override
        public void run() {
            LogUtil.i("vpn stateChanged stateChanged " + state.name() + "  errorState=" + errorState.name());
            switch (state) {
                case CONNECTED:
                    imgConn();
                    break;
                case CONNECTING:
                    imgAnim();
                    break;
                case DISCONNECTING:
                    imgNormal();
                    break;
                case DISABLED:
                    imgError();
                    hasIp = false;
                default:
                    imgError();
                    hasIp = false;
                    break;
            }
        }
    }

    @Override
    public void stateChanged(CharonVpnService.State state, CharonVpnService.ErrorState errorState) {
        handler.post(new StatChangeJob(state, errorState));
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
}
