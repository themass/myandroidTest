package com.openapi.ks.myapp.content.mine;

import com.openapi.ks.myapp.content.image.ImgItemFragment;
import com.openapi.ks.myapp.content.sound.SoundItemsMusicFragment;
import com.openapi.ks.myapp.content.text.TextItemsWebViewFragment;

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

import com.openapi.core.event.FavoriteVo;
import com.openapi.core.util.FavoriteUtil;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.json.GsonUtils;
import com.openapi.common.util.android.PathUtil;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.feature.content.R;
import com.openapi.ks.myapp.content.image.ImgItemsVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.ks.myapp.content.text.TextItemsVo;
import com.openapi.core.config.Constants;
import com.openapi.core.config.Routes;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 收藏夹（A18）：页面改为 Compose 列表（{@link FavoriteScreen}），打开各类收藏的逻辑保持不变。
 */
public class FavoriteFragment extends Fragment {

    private final FavoriteListState state = new FavoriteListState();

    public static void startFragment(Context context) {
        Intent intent = new Intent(context, com.openapi.core.legacyui.base.CommonFragmentActivity.class);
        intent.putExtra(com.openapi.core.legacyui.base.CommonFragmentActivity.FRAGMENT, FavoriteFragment.class);
        intent.putExtra(com.openapi.core.legacyui.base.CommonFragmentActivity.TITLE, R.string.menu_btn_favorite);
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
        // 收藏变化时刷新；随视图的生命周期自动停止收集
        com.openapi.common.event.EventFlowsKt.observeJava(
                androidx.lifecycle.LifecycleOwnerKt.getLifecycleScope(getViewLifecycleOwner()),
                com.openapi.core.event.CoreEvents.INSTANCE.getFavoriteChanged(),
                event -> reload());
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

    private void onOpen(FavoriteVo data) {
        if (data.type == Constants.FavoriteType.TEXT) {
            TextItemsVo vo = GsonUtils.json2Entry(data.extra, TextItemsVo.class);
            if (vo != null) {
                String fileName = PathUtil.getFileExtensionFromUrl(vo.fileUrl);
                String path = FileUtils.getWriteFilePath(getContext());
                File file = new File(path, fileName);
                if (file.exists())
                    vo.fileUrl = "file:///" + path + File.separator + fileName;
                TextItemsWebViewFragment.startFragment(getContext(), vo);
            }
        } else if (data.type == Constants.FavoriteType.SOUND) {
            RecommendVo vo = GsonUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null)
                SoundItemsMusicFragment.startFragment(getActivity(), vo);
        } else if (data.type == Constants.FavoriteType.IMG) {
            ImgItemsVo vo = GsonUtils.json2Entry(data.extra, ImgItemsVo.class);
            if (vo != null)
                ImgItemFragment.startFragment(getActivity(), vo);
        } else if (data.type == Constants.FavoriteType.VIDEO) {
            RecommendVo vo = GsonUtils.json2Entry(data.extra, RecommendVo.class);
            if (vo != null) {
                startActivity(Routes.videoShowLazyUrl(getActivity(), vo));
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
        super.onDestroyView();
    }
}
