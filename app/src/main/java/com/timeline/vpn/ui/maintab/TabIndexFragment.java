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
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.DataBuilder;
import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.ServerVo;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.config.ConfigActionEvent;
import com.timeline.vpn.ui.base.LoadableTabFragment;
import com.timeline.vpn.ui.view.MyPullView;

import org.strongswan.android.logic.CharonVpnService;
import org.strongswan.android.logic.VpnStateService;
import org.strongswan.android.logic.imc.ImcState;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabIndexFragment extends LoadableTabFragment<InfoListVo<RecommendVo>> implements VpnStateService.VpnStateListener, IndexRecommendAdapter.ItemClickListener, MyPullView.OnRefreshListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "index_tag";
    private static final int PREPARE_VPN_SERVICE = 0;
    @Bind(R.id.tv_vpn_state_text)
    TextView tvVpnText;
    @Bind(R.id.iv_vpn_state)
    ImageButton ibVpnStatus;
    @Bind(R.id.my_pullview)
    MyPullView pullView;
    CommonResponse.ResponseOkListener serverListener = new CommonResponse.ResponseOkListener<ServerVo>() {
        @Override
        public void onResponse(ServerVo serverVo) {
            long id = 0;
            for (HostVo vo : serverVo.hostList) {
                PingTask task = new PingTask(serverVo);
                LogUtil.i("run task " + vo.gateway);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vo);
            }
        }
    };
    private IndexRecommendAdapter adapter;
    private volatile boolean hasIp = false;
    CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            imgError();
        }
    };
    private VpnStateService mService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.i("VpnStateService->onServiceConnected");
            mService = ((VpnStateService.LocalBinder) service).getService();
            mService.registerListener(TabIndexFragment.this);
            vpnProfile = mService.getProfile();
            stateChanged();
            LogUtil.i(mService.getState()+"");
        }
    };

    private Handler handler = new Handler();
    private VpnProfile vpnProfile;
    private Animation operatingAnim = null;
    private LinearInterpolator lir = null;
    private BaseService indexService;
    private boolean isLoadingMore;
    private boolean isFirst = false;
    private InfoListVo<RecommendVo> infoVo = new InfoListVo<RecommendVo>();

    @Override
    protected int getTabHeaderViewId() {
        return R.layout.vpn_state_view_loading;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirst = true;
        infoVo.voList = new ArrayList<>();
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent) {
        inflater.inflate(R.layout.layout_recommd, parent, true);
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        if (data != null) {
            if (pullView.isLoadMore()) { //上拉加载
                infoVo.voList.addAll(data.voList);
            } else { //下拉刷新 或者首次
                infoVo.voList.clear();
                infoVo.voList.addAll(data.voList);
            }
            infoVo.copy(data);
            data.voList.clear();
            data.voList.addAll(infoVo.voList);
            setData(data);
            LogUtil.i("mData size=" + infoVo.voList.size());
        }
        pullView.notifyDataSetChanged();
    }

    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        LogUtil.i("loadData:"+mData);
        return indexService.getInfoListData(Constants.getRECOMMEND_URL(infoVo.pageNum), RecommendVo.class, INDEX_TAG);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        pullView.setLayoutManager(layoutManager);
        pullView.setItemAnimator(new DefaultItemAnimator());
        adapter = new IndexRecommendAdapter(this.getActivity(), pullView.getRecyclerView(), infoVo.voList, this, layoutManager);
        getActivity().bindService(new Intent(getActivity(), VpnStateService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        operatingAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.vpn_state_loading);
        lir = new LinearInterpolator();
        operatingAnim.setInterpolator(lir);
        indexService = new BaseService();
        indexService.setup(getActivity());
        pullView.setListener(this);
        pullView.setAdapter(adapter);
    }

    @Override
    public void onRefresh(int type) {
        LogUtil.i("onRefresh");
        if (type == MyPullView.OnRefreshListener.FRESH)
            infoVo.pageNum = 0;
        startQuery(false);
    }

    @Override
    public boolean needLoad() {
        LogUtil.i("needLoad " + infoVo);
        return infoVo.hasMore;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFirst) {
            isFirst = false;
            next();
        }
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = infoVo.voList.get(position);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(), vo.actionUrl));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        mService.disconnect();
        mService.unregisterListener(this);
        getActivity().unbindService(mServiceConnection);
        indexService.cancelRequest(INDEX_TAG);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @OnClick(R.id.iv_vpn_state)
    public void onVpnClick(View v) {
        LogUtil.i("onVpnClick " + mService.getState());
        if (mService != null && (mService.getState() == VpnStateService.State.CONNECTED || mService.getState() == VpnStateService.State.CONNECTING)) {
            mService.disconnect();
        } else {
            imgAnim();
            LocationVo vo = PreferenceUtils.getPrefObj(getActivity(), Constants.LOCATION_CHOOSE, LocationVo.class);
            indexService.getData(String.format(Constants.getUrl(Constants.API_SERVERLIST_URL), vo == null ? 0 : vo.id), serverListener, serverListenerError, INDEX_TAG, ServerVo.class);

        }
    }

    @Override
    public void stateChanged() {
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
                if (resultCode == Activity.RESULT_OK) {
                    Intent intent = new Intent(getActivity(), CharonVpnService.class);
                    Bundle bundle = new Bundle();
                    bundle.putSerializable(CharonVpnService.PROFILE, vpnProfile);
                    intent.putExtras(bundle);
                    getActivity().startService(intent);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void imgAnim() {
        stopAnim();
        hasIp = false;
        ibVpnStatus.setImageResource(R.drawable.ic_vpn_state_loading);
        ibVpnStatus.startAnimation(operatingAnim);
        tvVpnText.setText(R.string.vpn_bg_conning);
        ibVpnStatus.setEnabled(false);
    }

    private void imgError() {
        stopAnim();
        hasIp = false;
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_red);
        tvVpnText.setText(R.string.vpn_bg_error);
        ibVpnStatus.setEnabled(true);
    }

    private void imgConn() {
        stopAnim();
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_green);
        tvVpnText.setText(R.string.vpn_bg_conned);
        ibVpnStatus.setEnabled(true);
    }

    private void imgNormal() {
        stopAnim();
        hasIp = false;
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_red);
        tvVpnText.setText(R.string.vpn_bg_click);
        ibVpnStatus.setEnabled(true);
    }

    private void stopAnim() {
        ibVpnStatus.clearAnimation();
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
            synchronized (mService) {
                if (!hasIp && vo.ttlTime > 0) {
                    hasIp = true;
                    vpnProfile = DataBuilder.builderVpnProfile(server.expire, server.name, server.pwd, vo);
                    prepareVpnService();
                }
            }
            return vo;
        }

        @Override
        protected void onPostExecute(HostVo hostVo) {
            super.onPostExecute(hostVo);
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
        public boolean hasError(){
                if (errorState == VpnStateService.ErrorState.NO_ERROR)
                {
                    return false;
                }
                switch (errorState)
                {
                    case AUTH_FAILED:
                        if (imcState == ImcState.BLOCK)
                        {
                            Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_assessment_failed,Toast.LENGTH_SHORT).show();
                        }
                        else
                        {
                            Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_auth_failed,Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case PEER_AUTH_FAILED:
                        Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_peer_auth_failed,Toast.LENGTH_SHORT).show();
                        break;
                    case LOOKUP_FAILED:
                        Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_lookup_failed,Toast.LENGTH_SHORT).show();
                        break;
                    case UNREACHABLE:
                        Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_unreachable,Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Toast.makeText(TabIndexFragment.this.getActivity(),R.string.error_generic,Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
        }
        @Override
        public void run() {
            LogUtil.i("vpn stateChanged stateChanged " + state + "  errorState=" + errorState + "imcState=" + imcState);
            if(hasError()){
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
                    imgAnim();
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
