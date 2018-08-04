package com.timeline.myapp.ui.fragment;//package com.timeline.myapp.ui.fragment;
//
//import android.content.Context;
//import android.content.Intent;
//import android.os.Bundle;
//import android.text.Html;
//import android.view.LayoutInflater;
//import android.view.ViewGroup;
//
//import com.sspacee.common.ui.view.MyScrollView;
//import com.sspacee.common.ui.view.MyTextView;
//import com.timeline.sexfree1.R;
//import com.timeline.myapp.bean.vo.TextItemsVo;
//import com.timeline.myapp.constant.Constants;
//import com.timeline.myapp.data.BaseService;
//import com.timeline.myapp.data.StaticDataUtil;
//import com.timeline.myapp.data.UserLoginUtil;
//import com.timeline.myapp.ui.base.CommonFragmentActivity;
//import com.timeline.myapp.ui.base.features.LoadableFragment;
//
//import butterknife.BindView;
//
///**
// * Created by themass on 2016/8/12.
// */
//public class TextItemsStrFragment extends LoadableFragment<String> {
//    private static final String TEXT_TAG = "TEXT_ITEM_TAG";
//    @BindView(R.id.tv_view)
//    MyTextView tvView;
//    @BindView(R.id.sv_text)
//    MyScrollView svText;
//    private BaseService indexService;
//    private TextItemsVo vo;
//
//    public static void startFragment(Context context, TextItemsVo vo) {
//        Intent intent = new Intent(context, CommonFragmentActivity.class);
//        intent.putExtra(CommonFragmentActivity.FRAGMENT, TextItemsStrFragment.class);
//        intent.putExtra(CommonFragmentActivity.TITLE, vo.name);
//        StaticDataUtil.add(Constants.TEXT_FILE, vo);
//        if (!UserLoginUtil.isVIP3())
//            intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
//        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
//        context.startActivity(intent);
//    }
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        indexService = new BaseService();
//        indexService.setup(getActivity());
//        vo = StaticDataUtil.get(Constants.TEXT_FILE, TextItemsVo.class);
//        StaticDataUtil.del(Constants.TEXT_FILE);
//    }
//
//    @Override
//    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
//        inflater.inflate(R.layout.layout_textitems, parent, true);
//    }
//
//    @Override
//    protected void onDataLoaded(String data) {
//        tvView.setText(Html.fromHtml(data).toString().trim());
//
//    }
//
//    @Override
//    protected String loadData(Context context) throws Exception {
//        return indexService.getStringData(vo.fileUrl, TEXT_TAG);
//    }
//
//    @Override
//    public void onDestroyView() {
//        indexService.cancelRequest(TEXT_TAG);
//        super.onDestroyView();
//    }
//}
