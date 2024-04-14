package com.openapi.ks.myapp.ui.fragment;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupWindow;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.commons.common.util.CollectionUtils;
import com.openapi.commons.common.util.EventBusUtil;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.ks.chatfree.R;
import com.openapi.ks.myapp.bean.form.ChatSessionLog;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.UserLoginUtil;
import com.openapi.ks.myapp.data.config.ChatSessionEvent;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;

import java.util.List;

import butterknife.BindView;


/**
 * Created by openapi on 2015/9/1.
 */
public class ChatSessionFragment extends RecommendFragment  {
    private static final String INDEX_TAG = "chat_session_tag";
    @BindView(R.id.lb_add)
    ImageButton llAdd;
    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, ChatSessionFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_chat_session);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, false);
        context.startActivity(intent);
    }
    @Override
    public String getNetTag() {
        return INDEX_TAG;
    }

    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_recommd_custome, parent, true);
    }
    @Override
    protected void startQuery(boolean showLoading) {
        if(UserLoginUtil.getUserCache()!=null) {
            super.startQuery(showLoading);
        }else{
            hideLoading();
        }
    }
    @Override
    public void onRefresh(int type) {
        pullView.setRefresh(false);
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        Long id =  PreferenceUtils.getPrefLong(getActivity(), Constants.CHAT_SESSION, 0);
        List<ChatSessionLog> chatSessionLogList = DBManager.getInstance().getDaoSession().getChatSessionLogDao().loadAll();
        InfoListVo<RecommendVo> volist = new InfoListVo<RecommendVo>();
        for(ChatSessionLog log:chatSessionLogList){
            RecommendVo vo = new RecommendVo();
            vo.title = log.name;
            vo.id = log.id;
            vo.rate=0.2F;
            vo.showType = 2;
            if(id == vo.id){
                vo.color = "#698b87";
            }else{
                vo.color = "#666666";
            }
            volist.voList.add(vo);

        }
        volist.hasMore = false;
        return volist;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view,savedInstanceState);

    }

    @Override
    public int getSpanCount() {
        return 1;
    }

    @Override
    protected boolean getNeedShimmer() {
        return false;
    }

    @Override
    public String getUrl(int start) {
        return null;
    }

    @Override
    public void onLongItemClick(View view, final int position) {
        showPopupWindow(view, position);
    }
    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        if (!CollectionUtils.isEmpty(infoListVo.voList)) {
            if (llAdd != null)
                llAdd.setVisibility(View.GONE);
        } else {
            if (llAdd != null)
                llAdd.setVisibility(View.VISIBLE);
        }
    }
    public void onCustomerItemClick(View v, int position){
        RecommendVo data = infoListVo.voList.get(position);
        LogUtil.i("onItemClick");
        Long id =  PreferenceUtils.getPrefLong(getActivity(), Constants.CHAT_SESSION, 0);
        if(id != data.id){
            EventBusUtil.getEventBus().post(new ChatSessionEvent(data.id));
        }
        getActivity().finish();
    }

    @Override
    public void onEditClick(View view, int postion) {
        super.onEditClick(view, postion);
        showPopupWindow(view, postion);
    }

    private void showPopupWindow(View view, final int postion) {
        // 一个自定义的布局，作为显示的内容
        View contentView = LayoutInflater.from(mContext).inflate(
                R.layout.layout_customer_pop, null);
        final PopupWindow popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setTouchable(true);
        popupWindow.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
        int w = view.getWidth() / 2;
        int h = 0 - view.getHeight() / 2;
        popupWindow.showAsDropDown(view, w, h);
        // 设置按钮的点击事件
        Button btnDel = (Button) contentView.findViewById(R.id.btn_del);
        btnDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.del_hint);
                builder.setIcon(R.drawable.ic_launcher);
                builder.setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        popupWindow.dismiss();
                    }
                });
                builder.setPositiveButton(R.string.del_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(infoListVo.voList.size()<2){
                            ToastUtil.showLong(R.string.custome_del_fail);
                            return;
                        }else{
                            RecommendVo o = infoListVo.voList.get(postion);
                            DBManager.getInstance().getDaoSession().getChatSessionLogDao().deleteByKey(o.id);
                            refresh();
                        }

                        popupWindow.dismiss();
                    }
                });
                builder.setCancelable(true);
                builder.show();
            }
        });
        contentView.findViewById(R.id.btn_edit).setVisibility(View.GONE);

    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        super.onStartDrag(viewHolder);
        mItemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public boolean getCanMove() {
        return false;
    }

//    @Override
    public boolean getShowEdit() {
        return false;
    }

}
