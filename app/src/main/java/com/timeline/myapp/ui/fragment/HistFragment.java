package com.timeline.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.qq.sexfree.R;
import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.ModelUtils;
import com.sspacee.common.util.PathUtil;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.common.util.ToastUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.net.NetUtils;
import com.timeline.myapp.adapter.FavoriteViewAdapter;
import com.timeline.myapp.adapter.HistViewAdapter;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.HistVo;
import com.timeline.myapp.bean.vo.ImgItemsVo;
import com.timeline.myapp.bean.vo.InfoListVo;
import com.timeline.myapp.bean.vo.RecommendVo;
import com.timeline.myapp.bean.vo.TextItemsVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.FavoriteUtil;
import com.timeline.myapp.data.HistUtil;
import com.timeline.myapp.data.config.FavoriteChangeEvent;
import com.timeline.myapp.ui.base.CommonFragmentActivity;
import com.timeline.myapp.ui.base.MenuOneContext;
import com.timeline.myapp.ui.base.features.BasePullLoadbleFragment;
import com.timeline.myapp.ui.sound.VideoShowActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.List;


/**
 * Created by themass on 2016/8/12.
 */
public class HistFragment extends BasePullLoadbleFragment<HistVo> implements MenuOneContext.MyOnMenuItemClickListener,BaseRecyclerViewAdapter.OnRecyclerViewItemLongClickListener<HistVo> {
    private HistViewAdapter adapter;

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, HistFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.menu_btn_favorite);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN3);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, false);
        context.startActivity(intent);
    }

    @Override
    protected InfoListVo<HistVo> loadData(Context context) throws Exception {
        List<HistVo> list = HistUtil.getLocalFavorites(context);
        InfoListVo<HistVo> ret = new InfoListVo<HistVo>();
        ret.hasMore = false;
        ret.voList = list;
        return ret;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
    }
    @Override
    protected  BaseRecyclerViewAdapter getAdapter(){
        adapter = new HistViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        adapter.setLongClickListener(this);
        return adapter;
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FavoriteChangeEvent event) {
        pullView.setRefresh(true);
    }

    @Override
    public void onItemClick(View view, HistVo data, int postion) {
        super.onItemClick(view,data,postion);
        if (data.type == Constants.FavoriteType.TEXT) {
            TextItemsVo vo = ModelUtils.json2Entry(data.extra, TextItemsVo.class);
            if (vo != null) {
                if(!NetUtils.checkNetwork(getActivity())){
                    String fileName = PathUtil.getFileExtensionFromUrl(vo.fileUrl);
                    String path = FileUtils.getWriteFilePath(getContext());
                    File file = new File(path,fileName);
                    if(file.exists())
                        vo.fileUrl = "file:///"+path+File.separator+fileName;
                }
                TextItemsWebViewFragment.startFragment(getContext(),vo);
            }
        } else if (data.type == Constants.FavoriteType.SOUND) {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                SoundItemsMusicFragment.startFragment(getActivity(), vo);
        }else if (data.type == Constants.FavoriteType.IMG) {
            ImgItemsVo vo = ModelUtils.json2Entry(data.extra, ImgItemsVo.class);
            if (vo != null)
                ImgItemFragment.startFragment(getActivity(), vo);
        }else if (data.type == Constants.FavoriteType.VIDEO) {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                startActivity(VideoShowActivity.class, vo);
        }
    }
    @Override
    public void onItemLongClick(View view, HistVo data, int position){
        MenuOneContext.showOneMenu(getActivity(), view, R.string.menu_del_all, R.drawable.ic_menu_favorite_ed, HistFragment.this, position);
    }
    @Override
    public boolean onMenuItemClick(MenuItem item, int position){
        if(item.getItemId()==R.id.menu_share){
            if (infoListVo.voList.get(position).type == Constants.FavoriteType.TEXT) {
                SystemUtils.copy(getActivity(), infoListVo.voList.get(position).itemUrl);
                ToastUtil.showShort(R.string.menu_share_copy_ok);
            }else{
                ToastUtil.showShort(R.string.menu_share_text_only);
            }
            LogUtil.i(infoListVo.voList.get(position).itemUrl);
        }else {
            HistUtil.delAllLocalFavorite(getActivity());
            pullView.setRefresh(true);
        }
        return true;
    }
    public void modFavorite(boolean ret){
        pullView.setRefresh(true);
    }
    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();
    }
}
