//package com.timeline.vpn.ui.fragment;
//
//
//import android.content.Intent;
//import android.view.View;
//
//import com.timeline.vpn.bean.vo.RecommendVo;
//import com.timeline.vpn.constant.Constants;
//import com.timeline.vpn.ui.sound.VideoPlayQiniuActivity;
//
///**
// * Created by openapi on 2015/9/1.
// */
//public class VideoBodyFragment extends RecommendFragment {
//    private static final String INDEX_TAG = "VideoChannle_tag";
//
//    @Override
//    public String getUrl(int start) {
//        return Constants.getPage_URL(Constants.API_VIDEO_ITEMS_URL, start);
//    }
//    @Override
//    public String getNetTag() {
//        return INDEX_TAG;
//    }
//
//    @Override
//    public void onItemClick(View v, int position) {
//        RecommendVo vo = infoListVo.voList.get(position);
////        Intent intent = new Intent(Intent.ACTION_VIEW);
////        String type = "video/* ";
////        Uri uri = Uri.parse(vo.actionUrl);
////        intent.setDataAndType(uri, type);
////        startActivity(intent);
//        Intent intent = new Intent(getActivity(), VideoPlayQiniuActivity.class);
//        intent.putExtra(Constants.CONFIG_PARAM,vo);
//        startActivity(intent);
//    }
//
//    @Override
//    public int getSpanCount() {
//        return 2;
//    }
//
//    @Override
//    public boolean getShowEdit() {
//        return false;
//    }
//
//}
