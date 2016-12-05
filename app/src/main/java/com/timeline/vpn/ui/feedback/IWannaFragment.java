package com.timeline.vpn.ui.feedback;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.timeline.vpn.R;
import com.timeline.vpn.adapter.FeedAdapter;
import com.timeline.vpn.bean.form.IwannaForm;
import com.timeline.vpn.bean.vo.IWannaVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.common.net.request.CommonResponse;
import com.timeline.vpn.common.net.request.CommonResponse.ResponseOkListener;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.StringUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;
import com.timeline.vpn.ui.inte.FabOpListener;
import com.timeline.vpn.ui.user.LoginActivity;
import com.timeline.vpn.ui.view.HeartAnimView;
import com.timeline.vpn.ui.view.MyProgressDialog;
import com.timeline.vpn.ui.view.MyPullView;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by gqli on 2016/9/5.
 */
public class IWannaFragment extends LoadableFragment<InfoListVo<IWannaVo>> implements FeedAdapter.OnFeedItemClickListener, MyPullView.OnRefreshListener, FabOpListener.SetFabListener {
    private static String TAG = "IWANNA";
    @Bind(R.id.my_pullview)
    MyPullView pullView;
    @Bind(R.id.ll_comment)
    RelativeLayout rlComment;
    @Bind(R.id.et_comment)
    EditText etComment;
    FeedAdapter feedAdapter;
    private BaseService indexService;
    private FabOpListener.OnFabListener listener;
    private MyProgressDialog myProgressDialog;
    CommonResponse.ResponseErrorListener errorListener = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            myProgressDialog.dismiss();
        }
    };
    private InfoListVo<IWannaVo> infoVo = new InfoListVo<IWannaVo>();
    ResponseOkListener okListener = new ResponseOkListener<IWannaVo>() {
        @Override
        public void onResponse(IWannaVo o) {
            super.onResponse(o);
            myProgressDialog.dismiss();
            etComment.setText(null);
            if (!infoVo.hasMore) {
                infoVo.voList.add(o);
                pullView.notifyDataSetChanged();
            }
            Toast.makeText(getActivity(), R.string.iwanna_content_success, Toast.LENGTH_SHORT).show();
        }
    };

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, IWannaFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        context.startActivity(intent);
    }

    public static int getFragmentTitle() {
        return R.string.iwanna_title;
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity()) {
            @Override
            protected int getExtraLayoutSpace(RecyclerView.State state) {
                return 300;
            }
        };
        infoVo.voList = new ArrayList<>();
        pullView.setLayoutManager(linearLayoutManager);
        feedAdapter = new FeedAdapter(getActivity(), infoVo.voList, this);
        pullView.setAdapter(feedAdapter);
        pullView.setListener(this);
        pullView.setItemAnimator(new FeedItemAnimator());
        myProgressDialog = new MyProgressDialog(getActivity());
//        if(listener!=null){
//            listener.setFabUpVisibility(View.VISIBLE);
//            listener.setFabUpClickListener(new View.OnClickListener(){
//                @Override
//                public void onClick(View v) {
//                    rlComment.setVisibility(rlComment.getVisibility()==View.GONE?View.VISIBLE:View.GONE);
//                }
//            });
//        }
    }

    @OnClick(R.id.send)
    public void send(View view) {
        if (UserLoginUtil.getUserCache() == null) {
            startActivity(LoginActivity.class);
            return;
        }
        if (StringUtils.hasText(etComment.getText().toString())) {
            myProgressDialog.show();
            indexService.postData(Constants.getUrl(Constants.API_IWANNA_URL), new IwannaForm(etComment.getText().toString()), okListener, errorListener, TAG, IWannaVo.class);
        } else {
            Toast.makeText(getActivity(), R.string.iwanna_content_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_iwanna_fragment, parent);
    }

    @Override
    protected void onDataLoaded(InfoListVo<IWannaVo> data) {
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
    protected InfoListVo<IWannaVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(String.format(Constants.getUrl(Constants.API_IWANNA_URL), infoVo.pageNum), IWannaVo.class, TAG);
    }

    @Override
    public void onCommentsClick(View v, int position) {
        HeartAnimView.show(getActivity(), null);
    }

    @Override
    public void setFabUpListener(FabOpListener.OnFabListener l) {
        this.listener = l;
    }
}
