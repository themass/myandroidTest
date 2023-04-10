package io.github.ryanhoo.music.ui.local.all;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.ks.sexfree1.R;
import io.github.ryanhoo.music.data.model.Song;
import io.github.ryanhoo.music.ui.common.AbstractSummaryAdapter;
import io.github.ryanhoo.music.ui.details.SongItemView;
import io.github.ryanhoo.music.ui.widget.RecyclerViewFastScroller;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 9/2/16
 * Time: 8:21 PM
 * Desc: LocalMusicAdapter
 */
public class LocalMusicAdapter extends AbstractSummaryAdapter<Song, LocalMusicItemView>
        implements RecyclerViewFastScroller.BubbleTextGetter {

    Context mContext;

    public LocalMusicAdapter(Context context, List<Song> data) {
        super(context, data);
        mContext = context;
    }

    @Override
    protected String getEndSummaryText(int dataCount) {
        return mContext.getString(R.string.mp_local_files_music_list_end_summary_formatter, dataCount);
    }

    @Override
    protected LocalMusicItemView createView(Context context) {
        return new LocalMusicItemView(context);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Log.i("tag","position="+position+"; getLastItemClickPosition="+getLastItemClickPosition());

        if(holder.itemView  instanceof LocalMusicItemView) {
            LocalMusicItemView itemView = (LocalMusicItemView) holder.itemView;
            if (position == getLastItemClickPosition()) {
                itemView.visualizer.setColor(mContext.getResources().getColor(R.color.song_red));
                itemView.visualizer.setVisibility(View.VISIBLE);
                itemView.visualizer.startAnima();
//                setAnimation(holder.itemView, position);
            } else {
                itemView.visualizer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public String getTextToShowInBubble(int position) {
        Song item = getItem(position);
        if (position > 0 && item == null) {
            item = getItem(position - 1);
        }
        return item.getDisplayName().substring(0, 1);
    }
}
