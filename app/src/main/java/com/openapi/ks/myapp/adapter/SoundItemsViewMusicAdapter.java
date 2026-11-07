package com.openapi.ks.myapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.core.view.view.widgets.MusicVisualizer;
import com.openapi.ks.chat.R;
import com.openapi.ks.chat.databinding.ItemSongBinding;
import com.openapi.ks.chat.databinding.ItemSongAuxioBinding;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.SoundItemsVo;
import com.openapi.ks.myapp.ui.inte.MusicStateListener;

import java.util.List;

/**
 * Created by openapi on 2016/8/12.
 */
public class SoundItemsViewMusicAdapter extends BaseRecyclerViewAdapter<SoundItemsViewMusicAdapter.SoundItemView, SoundItemsVo> {
    public int lastPosition = -1;
    MusicStateListener mService = null;
    private final boolean useAuxioStyle;

    public SoundItemsViewMusicAdapter(Context context, RecyclerView recyclerView, List<SoundItemsVo> data,
                                      OnRecyclerViewItemClickListener<SoundItemsVo> listener, boolean useAuxioStyle) {
        super(context, recyclerView, data, listener);
        this.useAuxioStyle = useAuxioStyle;
    }

    public void setPlayServise(MusicStateListener service) {
        mService = service;
    }

    @Override
    public SoundItemsViewMusicAdapter.SoundItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        int layout = useAuxioStyle ? R.layout.item_song_auxio : R.layout.item_song;
        View view = LayoutInflater.from(context).inflate(layout, parent, false);
        return new SoundItemView(view, useAuxioStyle, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        SoundItemView holder = (SoundItemView) h;
        SoundItemsVo vo = data.get(position);
        holder.title.setText(vo.name);
        holder.artist.setText(vo.fileDate);

        if (useAuxioStyle) {
            bindAuxioRow(holder, position);
        } else {
            bindClassicCardRow(holder, position);
        }
    }

    private void bindAuxioRow(SoundItemView holder, int position) {
        if (holder.albumFrame != null) {
            holder.albumFrame.setBackgroundResource(
                    position == getSelected()
                            ? R.drawable.bg_auxio_list_thumb_playing
                            : R.drawable.bg_auxio_list_thumb_idle);
        }
        if (position == getSelected()) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.song_select_title));
            setAnimation(holder.itemView, position);
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.music_auxio_list_title));
        }
        holder.artist.setTextColor(ContextCompat.getColor(context, R.color.music_auxio_list_subtitle));
        boolean spectrumActive = mService != null && mService.showMusicListSpectrum();
        boolean isCurrent = position == getSelected();
        if (holder.visualizer != null) {
            holder.visualizer.setAuxioStyle(true);
            holder.visualizer.setColor(ContextCompat.getColor(context, R.color.music_auxio_list_eq_bar));
        }
        if (isCurrent && spectrumActive && holder.visualizer != null && holder.albumArt != null) {
            holder.albumArt.setVisibility(View.GONE);
            holder.visualizer.setVisibility(View.VISIBLE);
            holder.visualizer.setElevation(2f * context.getResources().getDisplayMetrics().density);
            holder.visualizer.bringToFront();
            holder.visualizer.invalidate();
            holder.visualizer.startAnima();
        } else {
            if (holder.albumArt != null) {
                holder.albumArt.setVisibility(View.VISIBLE);
            }
            if (holder.visualizer != null) {
                holder.visualizer.setVisibility(View.GONE);
                holder.visualizer.stopAnima();
            }
        }
        if (holder.playingBar != null) {
            holder.playingBar.setVisibility(View.GONE);
        }
        if (holder.ivRowOverflow != null) {
            holder.ivRowOverflow.setVisibility(View.VISIBLE);
        }
    }

    private void bindClassicCardRow(SoundItemView holder, int position) {
        float density = context.getResources().getDisplayMetrics().density;
        if (holder.itemView instanceof CardView) {
            ((CardView) holder.itemView).setCardElevation(
                    (position == getSelected() ? 8f : 3f) * density);
        }
        if (holder.playingBar != null) {
            holder.playingBar.setVisibility(position == getSelected() ? View.VISIBLE : View.GONE);
        }
        if (position == getSelected()) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.song_select_title));
            if (holder.visualizer != null) {
                holder.visualizer.setAuxioStyle(false);
                holder.visualizer.setColor(ContextCompat.getColor(context, R.color.song_red));
                holder.visualizer.setVisibility(View.VISIBLE);
                holder.visualizer.startAnima();
            }
            setAnimation(holder.itemView, position);
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.main_dark));
            if (holder.visualizer != null) {
                holder.visualizer.setVisibility(View.GONE);
                holder.visualizer.stopAnima();
            }
        }
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.slide_out_to_left);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    static class SoundItemView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<SoundItemsVo> {
        @Nullable
        protected TextView title;
        @Nullable
        protected TextView artist;
        @Nullable
        protected ImageView albumArt;
        @Nullable
        protected FrameLayout albumFrame;
        @Nullable
        protected MusicVisualizer visualizer;
        @Nullable
        protected View playingBar;
        @Nullable
        protected ImageView ivRowOverflow;

        public SoundItemView(View itemView, boolean auxio, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            // 两个布局的 id 相同，但 Binding 类不同，按布局选择对应的一套
            if (auxio) {
                ItemSongAuxioBinding b = ItemSongAuxioBinding.bind(itemView);
                title = b.songTitle;
                artist = b.songArtist;
                albumArt = b.albumArt;
                albumFrame = b.albumFrame;
                visualizer = b.visualizer;
                playingBar = b.viewNowPlayingBar;
                ivRowOverflow = b.ivRowOverflow;
            } else {
                ItemSongBinding b = ItemSongBinding.bind(itemView);
                title = b.songTitle;
                artist = b.songArtist;
                albumArt = b.albumArt;
                albumFrame = b.albumFrame;
                visualizer = b.visualizer;
                playingBar = b.viewNowPlayingBar;
                ivRowOverflow = b.ivRowOverflow;
            }
        }
    }
}
