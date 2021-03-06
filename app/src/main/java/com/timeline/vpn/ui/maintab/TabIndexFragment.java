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

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.DataBuilder;
import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.ServerVo;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.net.HttpUtils;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.util.EventBusUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.config.ConfigActionEvent;
import com.timeline.vpn.service.CharonVpnService;
import com.timeline.vpn.ui.base.LoadableTabFragment;
import com.timeline.vpn.ui.vpn.LocationChooseaActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2015/9/1.
 */
public class TabIndexFragment extends LoadableTabFragment<InfoListVo<RecommendVo>> implements CharonVpnService.VpnStateListener,IndexRecommendAdapter.ItemClickListener {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "index_tag";
    private static final int PREPARE_VPN_SERVICE = 0;
    @Nullable
    @Bind(R.id.footerView)
    View footerView;
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
    private BaseService indexService;
    private boolean hasMore;
    private boolean isLoadingMore;
    private int pageNum = 0;
    private List<RecommendVo> mData = new ArrayList<RecommendVo>();
    private boolean isFirst = false;

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
    protected int getTabHeaderViewId() {
        return R.layout.vpn_state_view_loading;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFirst = true;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent) {
        inflater.inflate(R.layout.tab_index_body_content, parent, true);
    }
    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        //下拉刷新
        if(refreshLayout.isRefreshing()){
            mData.clear();
            refreshLayout.setRefreshing(false);
            mData.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
            pageNum++;
        }else if(footerView.getVisibility()==View.VISIBLE){ //上拉加载
            footerView.setVisibility(View.GONE);
            mData.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
            pageNum++;
        }else if(isLoadingMore){//首次加载
            mData.addAll(data.voList);
            rvRecommend.getAdapter().notifyDataSetChanged();
        }
        hasMore = data.hasMore;
        data.voList = mData;
        setData(data);
        isLoadingMore = false;
        LogUtil.i("mData size="+mData.size());
    }

    @Override
    protected InfoListVo<RecommendVo> loadData(Context context){
        isLoadingMore= true;
        return indexService.getInfoListData(Constants.getRECOMMEND_URL(pageNum),RecommendVo.class,INDEX_TAG);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        fabUp.setImageResource(R.drawable.fab_pigu);
        adapter = new IndexRecommendAdapter(this.getActivity(), rvRecommend, mData, this);
        final StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        rvRecommend.setLayoutManager(layoutManager);
        rvRecommend.setItemAnimator(new DefaultItemAnimator());
        getActivity().bindService(new Intent(getActivity(), CharonVpnService.class),
                mServiceConnection, Service.BIND_AUTO_CREATE);
        operatingAnim = AnimationUtils.loadAnimation(getActivity(), R.anim.vpn_state_loading);
        lir = new LinearInterpolator();
        operatingAnim.setInterpolator(lir);
        indexService = new BaseService();
        indexService.setup(getActivity());
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pageNum = 0;
                startQuery(false);
            }
        });
        rvRecommend.setAdapter(adapter);
        rvRecommend.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int[] visibleItems = layoutManager.findLastVisibleItemPositions(null);
                int lastitem = Math.max(visibleItems[0], visibleItems[1]);
                if (dy > 0 && lastitem > adapter.getItemCount() - 5 && !isLoadingMore && hasMore) {
                    footerView.setVisibility(View.VISIBLE);
                    startQuery(false);
                }
            }
        });
        fabUp.setBackgroundResource(R.drawable.fab_pigu);
    }
    @Override
    public void onResume() {
        super.onResume();
        if(isFirst){
            isFirst = false;
            next();
        }
    }

    @Override
    public void onItemClick(View v, int position) {
        RecommendVo vo = mData.get(position);
        EventBusUtil.getEventBus().post(new ConfigActionEvent(getActivity(),vo.actionUrl));
    }

    /**
     * 选择地区
     * @param view
     */
    @Override
    public void onClickFab(View view) {
        startActivity(LocationChooseaActivity.class);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        LogUtil.i("onVpnClick " + mService.getCurrentVpnState().name());
        if (mService.getCurrentVpnState()== CharonVpnService.State.CONNECTED) {
            mService.stopCurrentConnection();
            imgNormal();
        } else if(mService.getCurrentVpnState()== CharonVpnService.State.DISABLED) {
            imgAnim();
            indexService.getData(Constants.SERVERLIST_URL,serverListener,serverListenerError,INDEX_TAG,ServerVo.class);
        }else if(mService.getCurrentVpnState()== CharonVpnService.State.CONNECTING){
            mService.stopCurrentConnection();
            imgNormal();
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
        ibVpnStatus.setImageResource(R.drawable.circle_vpn_blue);
        tvVpnText.setText(R.string.vpn_bg_click);
    }
    private void stopAnim(){
        ibVpnStatus.clearAnimation();
    }
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
    CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener(){
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
            int ttl = HttpUtils.ping(vo.gateway);
            vo.ttlTime = ttl;
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
