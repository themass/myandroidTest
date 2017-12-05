package com.timeline.sex.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.sspacee.common.ui.view.MyScrollView;
import com.sspacee.common.ui.view.MyTextView;
import com.timeline.sex.R;
import com.timeline.sex.bean.vo.TextItemsVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.BaseService;
import com.timeline.sex.data.StaticDataUtil;
import com.timeline.sex.data.UserLoginUtil;
import com.timeline.sex.ui.base.CommonFragmentActivity;
import com.timeline.sex.ui.base.features.LoadableFragment;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class TextItemsStrFragment extends LoadableFragment<String> {
    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
    @BindView(R.id.tv_view)
    MyTextView tvView;
    @BindView(R.id.sv_text)
    MyScrollView svText;
    private BaseService indexService;
    private TextItemsVo vo;

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsStrFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
        if (!UserLoginUtil.isVIP2())
            intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
        StaticDataUtil.del(Constants.TEXT_FILE);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_textitems, parent, true);
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
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }
}
