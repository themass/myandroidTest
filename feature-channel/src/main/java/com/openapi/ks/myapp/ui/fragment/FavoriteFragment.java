package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.openapi.core.event.FavoriteChangeEvent;
import com.openapi.core.event.FavoriteVo;
import com.openapi.core.util.EventBusUtil;
import com.openapi.core.util.FavoriteUtil;
import com.openapi.core.util.FileUtils;
import com.openapi.core.util.ModelUtils;
import com.openapi.core.util.PathUtil;
import com.openapi.core.util.SystemUtils;
import com.openapi.core.util.ToastUtil;
import com.openapi.feature.channel.R;
import com.openapi.ks.myapp.bean.vo.ImgItemsVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.sound.VideoShowActivityLazyUrl;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹（A18）：页面改为 Compose 列表（{@link FavoriteScreen}），打开各类收藏的逻辑保持不变。
 */
public class FavoriteFragment extends Fragment {

    private final FavoriteListState state = new FavoriteListState();

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, com.openapi.ks.myapp.ui.base.CommonFragmentActivity.class);
        intent.putExtra(com.openapi.ks.myapp.ui.base.CommonFragmentActivity.FRAGMENT, FavoriteFragment.class);
        intent.putExtra(com.openapi.ks.myapp.ui.base.CommonFragmentActivity.TITLE, R.string.menu_btn_favorite);
        context.startActivity(intent);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ComposeView view = new ComposeView(requireContext());
        FavoriteScreenKt.setFavoriteContent(view, state,
                vo -> { onOpen(vo); return kotlin.Unit.INSTANCE; },
                vo -> { onShare(vo); return kotlin.Unit.INSTANCE; },
                vo -> { onCancelFavorite(vo); return kotlin.Unit.INSTANCE; });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EventBusUtil.getEventBus().register(this);
        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        if (getContext() == null) return;
        List<FavoriteVo> list = FavoriteUtil.getLocalFavorites(getContext());
        state.replace(list == null ? new ArrayList<>() : list);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FavoriteChangeEvent event) {
        reload();
    }

    private void onOpen(FavoriteVo data) {
        if (data.type == Constants.FavoriteType.TEXT) {
            TextItemsVo vo = ModelUtils.json2Entry(data.extra, TextItemsVo.class);
            if (vo != null) {
                String fileName = PathUtil.getFileExtensionFromUrl(vo.fileUrl);
                String path = FileUtils.getWriteFilePath(getContext());
                File file = new File(path, fileName);
                if (file.exists())
                    vo.fileUrl = "file:///" + path + File.separator + fileName;
                TextItemsWebViewFragment.startFragment(getContext(), vo);
            }
        } else if (data.type == Constants.FavoriteType.SOUND) {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                SoundItemsMusicFragment.startFragment(getActivity(), vo);
        } else if (data.type == Constants.FavoriteType.IMG) {
            ImgItemsVo vo = ModelUtils.json2Entry(data.extra, ImgItemsVo.class);
            if (vo != null)
                ImgItemFragment.startFragment(getActivity(), vo);
        } else if (data.type == Constants.FavoriteType.VIDEO) {
            RecommendVo vo = ModelUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null) {
                Intent intent = new Intent(getActivity(), VideoShowActivityLazyUrl.class);
                intent.putExtra(Constants.CONFIG_PARAM, vo);
                startActivity(intent);
            }
        }
    }

    private void onShare(FavoriteVo data) {
        if (data.type == Constants.FavoriteType.TEXT) {
            SystemUtils.copy(getActivity(), data.itemUrl);
            ToastUtil.showShort(R.string.menu_share_copy_ok);
        } else {
            ToastUtil.showShort(R.string.menu_share_text_only);
        }
    }

    private void onCancelFavorite(FavoriteVo data) {
        FavoriteUtil.modLocalFavoritesAsync(getActivity(), data, vo -> reload());
    }

    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();
    }
}
