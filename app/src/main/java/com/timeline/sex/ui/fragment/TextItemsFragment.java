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
import com.timeline.sex.bean.vo.TextItemVo;
import com.timeline.sex.bean.vo.TextItemsVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.data.BaseService;
import com.timeline.sex.data.StaticDataUtil;
import com.timeline.sex.ui.base.CommonFragmentActivity;
import com.timeline.sex.ui.base.features.LoadableFragment;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class TextItemsFragment extends LoadableFragment<TextItemVo> {
    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
    @BindView(R.id.tv_view)
    MyTextView tvView;
    @BindView(R.id.sv_text)
    MyScrollView svText;
    private BaseService indexService;
    private TextItemsVo vo;
    private String infoVo;

    public static void startFragment(Context context, TextItemsVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
        StaticDataUtil.add(Constants.TEXT_FILE, vo);
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
    protected void onDataLoaded(TextItemVo data) {
        infoVo = data.file;
        tvView.setText(Html.fromHtml(infoVo).toString().trim());
    }

    @Override
    protected TextItemVo loadData(Context context) throws Exception {
        return indexService.getData(Constants.getUrlWithParam(Constants.API_TEXT_ITEM_URL, String.valueOf(vo.id)), TextItemVo.class, TEXT_TAG);
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(TEXT_TAG);
        super.onDestroyView();
    }
}