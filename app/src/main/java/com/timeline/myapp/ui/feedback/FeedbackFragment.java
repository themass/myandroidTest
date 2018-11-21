package com.timeline.myapp.ui.feedback;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;

import com.qq.sexfree.R;
import com.sspacee.common.ui.view.HeartAnimView;
import com.sspacee.common.ui.view.MyProgressDialog;
import com.sspacee.common.ui.view.MyPullView;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.StringUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.net.request.CommonResponse;
import com.sspacee.yewu.net.request.CommonResponse.ResponseOkListener;
import com.timeline.myapp.adapter.FeedAdapter;
import com.timeline.myapp.bean.form.IwannaForm;
import com.timeline.myapp.bean.vo.IWannaVo;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.BaseService;
import com.timeline.myapp.data.UserLoginUtil;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.base.features.LoadableFragment;
import com.timeline.myapp.ui.inte.FabOpListener;
import com.timeline.myapp.ui.user.LoginActivity;


import java.util.ArrayList;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by themass on 2016/9/5.
 */
public class FeedbackFragment extends LoadableFragment<InfoListVo<IWannaVo>> implements FeedAdapter.OnFeedItemClickListener, MyPullView.OnRefreshListener, FabOpListener.SetFabListener {
    private static String TAG = "IWANNA";
    @BindView(R.id.my_pullview)
    MyPullView pullView;
    @BindView(R.id.ll_comment)
    RelativeLayout rlComment;
    @BindView(R.id.et_comment)
    EditText etComment;
    @BindView(R.id.sp_recharge)
    Spinner spRech;
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
    ResponseOkListener okListener = new ResponseOkListener<IWannaVo>() {
        @Override
        public void onResponse(IWannaVo o) {
            super.onResponse(o);
            myProgressDialog.dismiss();
            etComment.setText(null);
            pullView.setRefresh(true);
        }
    };
    private InfoListVo<IWannaVo> infoVo = new InfoListVo<>();

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, FeedbackFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, getFragmentTitle());
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
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
        feedAdapter = new FeedAdapter(getActivity(), infoVo.voList, this,Constants.API_FEEDBACK_LIKE_URL);
        pullView.setAdapter(feedAdapter);
        pullView.setListener(this);
        pullView.setItemAnimator(new FeedItemAnimator());
        myProgressDialog = new MyProgressDialog(getActivity());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.layout_feed_spinner, getResources().getStringArray(R.array.recharge_arry));
        spRech.setAdapter(adapter);
        spRech.setSelection(0,true);
    }

    @OnClick(R.id.send)
    public void send(View view) {
        if (UserLoginUtil.getUserCache() == null) {
            startActivity(LoginActivity.class);
            return;
        }
        if (StringUtils.hasText(etComment.getText().toString())) {
            myProgressDialog.show();
            indexService.postData(Constants.getUrl(Constants.API_FEEDBACK_URL), new IwannaForm(etComment.getText().toString()+"--"+spRech.getSelectedItem() ), okListener, errorListener, TAG, IWannaVo.class);
        } else {
            ToastUtil.showShort(R.string.iwanna_content_error);
        }
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_iwanna_score_fragment, parent);
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
        return indexService.getInfoListData(String.format(Constants.getUrl(Constants.API_FEEDBACK_URL), infoVo.pageNum), IWannaVo.class, TAG);
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
