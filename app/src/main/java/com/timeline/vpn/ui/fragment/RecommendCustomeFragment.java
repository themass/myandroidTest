package com.timeline.vpn.ui.fragment;


import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.google.gson.reflect.TypeToken;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.GsonUtils;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.net.request.CommonResponse;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.form.CustomeAddForm;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.NullReturnVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.CustomeAddEvent;
import com.timeline.vpn.data.config.UserLoginEvent;
import com.timeline.vpn.ui.inte.OnBackKeyDownListener;
import com.timeline.vpn.ui.main.MainFragmentViewPage;
import com.timeline.vpn.ui.user.AddCustomeInfoActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import butterknife.BindView;
import butterknife.OnClick;


/**
 * Created by themass on 2015/9/1.
 */
public class RecommendCustomeFragment extends RecommendFragment implements OnBackKeyDownListener {
    private static final String INDEX_TAG = "Recommend_custome_tag";
    @BindView(R.id.lb_add)
    ImageButton llAdd;
    LinkedList<Integer> sortList = null;

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
       if(UserLoginUtil.getUserCache()!=null){
           super.onRefresh(type);
       }else {
           pullView.setRefresh(false);
       }
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        if (UserLoginUtil.getUserCache() != null) {
            return super.loadData(context);
        } else {
            return new InfoListVo<RecommendVo>();
        }
    }

    @Override
    protected void onDataLoaded(InfoListVo<RecommendVo> data) {
        super.onDataLoaded(data);
        dataForView();
    }

    @Override
    protected void initSort() {
        super.initSort();
        if (CollectionUtils.isEmpty(sortList) && !CollectionUtils.isEmpty(infoListVo.voList)) {
            for (RecommendVo vo : infoListVo.voList) {
                sortList.add(vo.id);
            }
        }
    }

    @Override
    protected void sortData() {
        Collections.sort(infoListVo.voList, new MyComparator());
    }

    private void dataForView() {
        if (!CollectionUtils.isEmpty(infoListVo.voList)) {
            if (llAdd != null)
                llAdd.setVisibility(View.GONE);
        } else {
            if (llAdd != null)
                llAdd.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.lb_add)
    public void onAdd() {
        AddCustomeInfoActivity.startActivity(getActivity(), null);
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
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view,savedInstanceState);
        EventBusUtil.getEventBus().register(this);
        ((MainFragmentViewPage) getActivity()).addListener(this);
        sortList = PreferenceUtils.getPrefObj(getActivity(), Constants.CUSTOME_SORT, new TypeToken<LinkedList<Integer>>() {
        }.getType());
        if (sortList == null) {
            sortList = new LinkedList<>();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBusUtil.getEventBus().unregister(this);
        ((MainFragmentViewPage) getActivity()).removeListener(this);
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
    public void onLongItemClick(View view, final int position) {
        switchFlag(true);
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
                builder.setIcon(R.drawable.vpn_trans_default);
                builder.setNegativeButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        popupWindow.dismiss();
                    }
                });
                builder.setPositiveButton(R.string.del_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Object o = infoListVo.voList.get(postion);
                        indexService.postData(Constants.getUrl(Constants.API_DEL_CUSTOME), o, new CommonResponse.ResponseOkListener<NullReturnVo>(o) {
                            @Override
                            public void onResponse(NullReturnVo vo) {
                                Toast.makeText(getActivity(), R.string.custome_del_ok, Toast.LENGTH_SHORT).show();
                                sortList.remove(sortList.indexOf(((RecommendVo) getParam()).id));
                                refresh();
                                saveSortMap();

                            }
                        }, null, INDEX_TAG, NullReturnVo.class);
                        popupWindow.dismiss();
                    }
                });
                builder.setCancelable(true);
                builder.show();
            }
        });
        Button btnEdit = (Button) contentView.findViewById(R.id.btn_edit);
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                RecommendVo o = infoListVo.voList.get(postion);
                CustomeAddForm form = new CustomeAddForm(o.id, o.title, o.actionUrl);
                AddCustomeInfoActivity.startActivity(getActivity(), form);
            }
        });
    }

    private void saveSortMap() {
        PreferenceUtils.setPrefString(getActivity(), Constants.CUSTOME_SORT, GsonUtils.getInstance().toJson(sortList));
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        super.onStartDrag(viewHolder);
        mItemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onItemMove(Object o1, Object o2) {
        super.onItemMove(o1, o2);
//        sortMap.put(String.valueOf(((RecommendVo)o1).id),((RecommendVo)o2).id);
        int move1 = sortList.indexOf(((RecommendVo) o1).id);
        int move2 = sortList.indexOf(((RecommendVo) o2).id);
        sortList.remove(move2);
        sortList.add(move1, ((RecommendVo) o2).id);
        saveSortMap();
    }

    @Override
    public boolean getCanMove() {
        return true;
    }

    @Override
    public boolean getShowEdit() {
        return true;
    }

    @Override
    public boolean onkeyBackDown() {
        if (getSwitchFlag()) {
            switchFlag(false);
            return true;
        }
        return false;
    }

    public class MyComparator implements Comparator<RecommendVo> {
        @Override
        public int compare(RecommendVo lhs, RecommendVo rhs) {
            Integer start = sortList.indexOf(lhs.id);
            Integer end = sortList.indexOf(rhs.id);
            return start - end;
        }
    }
}
