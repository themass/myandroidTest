package io.github.ryanhoo.music.ui.playlist;

import java.util.List;

import io.github.ryanhoo.music.data.model.PlayList;
import io.github.ryanhoo.music.ui.base.BasePresenter;
import io.github.ryanhoo.music.ui.base.BaseView;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 9/11/16
 * Time: 1:25 AM
 * Desc: PlayListContract
 */
/* package */ interface PlayListContract {

    interface View extends BaseView<Presenter> {

        void showLoading();

        void hideLoading();

        void handleError(Throwable error);

        void onPlayListsLoaded(List<PlayList> playLists);

        void onPlayListCreated(PlayList playList);

        void onPlayListEdited(PlayList playList);

        void onPlayListDeleted(PlayList playList);
    }

    interface Presenter extends BasePresenter {

        void loadPlayLists();

        void createPlayList(PlayList playList);

        void editPlayList(PlayList playList);

        void deletePlayList(PlayList playList);
    }
}
