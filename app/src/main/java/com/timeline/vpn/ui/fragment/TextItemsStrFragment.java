package com.timeline.vpn.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sspacee.common.ui.view.MyScrollView;
import com.sspacee.common.ui.view.MyTextView;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.TextItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.BaseService;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.ui.base.CommonFragmentActivity;
import com.timeline.vpn.ui.base.LoadableFragment;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/12.
 */
public class TextItemsStrFragment extends LoadableFragment<String> {
    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
    private BaseService indexService;
    @Bind(R.id.tv_line)
    TextView tvLine;
    @Bind(R.id.tv_view)
    MyTextView tvView;
    @Bind(R.id.sv_text)
    MyScrollView svText;
    private TextItemsVo vo;
    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
        }
    };

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsStrFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.TEXT_FILE,vo);
        if(!UserLoginUtil.isVIP2())
            intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        context.startActivity(intent);
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        vo = StaticDataUtil.get(Constants.TEXT_FILE,TextItemsVo.class);
        StaticDataUtil.del(Constants.TEXT_FILE);
    }
    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState){
        inflater.inflate(R.layout.layout_textitems, parent, true);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState){
        super.setupViews(view,savedInstanceState);
    }
    @Override
    protected void onDataLoaded(String data) {
        tvView.setText(Html.fromHtml(data).toString().trim());

    }
    @Override
    protected String loadData(Context context) throws Exception {
        return indexService.getStringData(vo.fileUrl, TEXT_TAG);
    }

    @Override
    public void onDestroy() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroy();
    }
}
