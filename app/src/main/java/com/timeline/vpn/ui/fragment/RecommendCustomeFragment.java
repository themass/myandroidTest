package com.timeline.vpn.ui.fragment;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.sspacee.common.net.request.CommonResponse;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.EventBusUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.CustomeAddEvent;
import com.timeline.vpn.data.config.UserLoginEvent;
import com.timeline.vpn.ui.user.AddCustomeInfoActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Created by themass on 2015/9/1.
 */
public class RecommendCustomeFragment extends RecommendFragment{
    private static final String INDEX_TAG = "Recommend_custome_tag";
    private static final String DEL_TAG = "del_custome_tag";
    @Bind(R.id.lb_add)
    ImageButton llAdd;
    CommonResponse.ResponseOkListener listener = new CommonResponse.ResponseOkListener<NullReturnVo>() {
        @Override
        public void onResponse(NullReturnVo vo) {
            Toast.makeText(getActivity(), R.string.custome_del_ok, Toast.LENGTH_SHORT).show();
            refresh();
        }
    };
    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_recommd_custome, parent, true);
    }

    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        if(UserLoginUtil.getUserCache()!=null){
            return super.loadData(context);
        }else{
            return new InfoListVo<RecommendVo>();
        }

    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        dataForView();
    }
    private void dataForView(){
        if(!CollectionUtils.isEmpty(infoVo.voList)){
            llAdd.setVisibility(View.GONE);
        }else{
            llAdd.setVisibility(View.VISIBLE);
        }
    }
    @OnClick(R.id.lb_add)
    public void onAdd(){
        AddCustomeInfoActivity.startActivity(getActivity());
    }

    @Override
    public String getUrl(int start) {
        return Constants.getRECOMMEND_CUSTOME_URL(start);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(CustomeAddEvent event) {
        refresh();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserLoginEvent event) {
        refresh();
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBusUtil.getEventBus().unregister(this);
    }
    @Override
    public int getSpanCount() {
        return 2;
    }

    @Override
    protected boolean getNeedShimmer() {
        return false;
    }

    @Override
    public void onLongItemClick(View view, final int position){
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.del_hint);
        builder.setIcon(R.drawable.vpn_trans_default);
        builder.setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setPositiveButton(R.string.del_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Object o = mData.voList.get(position);
                indexService.postData(Constants.getUrl(Constants.API_DEL_CUSTOME),o,listener,null,DEL_TAG,NullReturnVo.class);
            }
        });
        builder.setCancelable(true);
        builder.show();
    }
}
