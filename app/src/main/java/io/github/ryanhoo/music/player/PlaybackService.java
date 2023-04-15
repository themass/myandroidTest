package io.github.ryanhoo.music.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.openapi.ks.moviefree1.R;
import io.github.ryanhoo.music.data.model.PlayList;
import io.github.ryanhoo.music.data.model.Song;
import io.github.ryanhoo.music.ui.main.MuiscMainActivity;
import io.github.ryanhoo.music.utils.AlbumUtils;

/**
 * Created with Android Studio.
 * User: ryan.hoo.j@gmail.com
 * Date: 9/12/16
 * Time: 4:27 PM
 * Desc: PlayService
 */
public class PlaybackService extends Service implements IPlayback, IPlayback.Callback {

    private static final String ACTION_PLAY_TOGGLE = "io.github.ryanhoo.music.ACTION.PLAY_TOGGLE";
    private static final String ACTION_PLAY_LAST = "io.github.ryanhoo.music.ACTION.PLAY_LAST";
    private static final String ACTION_PLAY_NEXT = "io.github.ryanhoo.music.ACTION.PLAY_NEXT";
    private static final String ACTION_STOP_SERVICE = "io.github.ryanhoo.music.ACTION.STOP_SERVICE";

    private static final int NOTIFICATION_ID = 1;

    private RemoteViews mContentViewBig, mContentViewSmall;

    private Player mPlayer;

    private final Binder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPlayer = Player.getInstance();
        mPlayer.registerCallback(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_TOGGLE.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (ACTION_PLAY_NEXT.equals(action)) {
                playNext();
            } else if (ACTION_PLAY_LAST.equals(action)) {
                playLast();
            } else if (ACTION_STOP_SERVICE.equals(action)) {
                if (isPlaying()) {
                    pause();
                }
                stopForeground(true);
                unregisterCallback(this);
            }
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean stopService(Intent name) {
        stopForeground(true);
        unregisterCallback(this);
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public void setPlayList(PlayList list) {
        mPlayer.setPlayList(list);
    }

    @Override
    public boolean play() {
        return mPlayer.play();
    }

    @Override
    public boolean play(PlayList list) {
        return mPlayer.play(list);
    }

    @Override
    public boolean play(PlayList list, int startIndex) {
        return mPlayer.play(list, startIndex);
    }

    @Override
    public boolean play(Song song) {
        return mPlayer.play(song);
    }

    @Override
    public boolean playLast() {
        return mPlayer.playLast();
    }

    @Override
    public boolean playNext() {
        return mPlayer.playNext();
    }

    @Override
    public boolean pause() {
        return mPlayer.pause();
    }

    @Override
    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    @Override
    public int getProgress() {
        return mPlayer.getProgress();
    }

    @Override
    public Song getPlayingSong() {
        return mPlayer.getPlayingSong();
    }

    @Override
    public boolean seekTo(int progress) {
        return mPlayer.seekTo(progress);
    }

    @Override
    public void setPlayMode(PlayMode playMode) {
        mPlayer.setPlayMode(playMode);
    }

    @Override
    public void registerCallback(Callback callback) {
        mPlayer.registerCallback(callback);
    }

    @Override
    public void unregisterCallback(Callback callback) {
        mPlayer.unregisterCallback(callback);
    }

    @Override
    public void removeCallbacks() {
        mPlayer.removeCallbacks();
    }

    @Override
    public void releasePlayer() {
        mPlayer.releasePlayer();
        super.onDestroy();
    }

    // Playback Callbacks

    @Override
    public void onSwitchLast(@Nullable Song last) {
        showNotification();
    }

    @Override
    public void onSwitchNext(@Nullable Song next) {
        showNotification();
    }

    @Override
    public void onComplete(@Nullable Song next) {
        showNotification();
    }

    @Override
    public void onPlayStatusChanged(boolean isPlaying) {
        showNotification();

    }

    // Notification

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MuiscMainActivity.class), 0);

        // Set the info for the views that show in the notification panel.

        String channelId = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel("kim.hsl", "ForegroundService");
        } else {
            channelId = "";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        Notification notification = builder.setSmallIcon(R.drawable.ic_notification_app_logo)  // the status icon
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                .setCustomContentView(getSmallContentView())
                .setCustomBigContentView(getBigContentView())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build();

        // Send the notification.
        startForeground(NOTIFICATION_ID, notification);
    }
    /**
     * 创建通知通道
     * @param channelId
     * @param channelName
     * @return
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName){
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }
    private RemoteViews getSmallContentView() {
        if (mContentViewSmall == null) {
            mContentViewSmall = new RemoteViews(getPackageName(), R.layout.remote_view_music_player_small);
            setUpRemoteView(mContentViewSmall);
        }
        updateRemoteViews(mContentViewSmall);
        return mContentViewSmall;
    }

    private RemoteViews getBigContentView() {
        if (mContentViewBig == null) {
            mContentViewBig = new RemoteViews(getPackageName(), R.layout.remote_view_music_player);
            setUpRemoteView(mContentViewBig);
        }
        updateRemoteViews(mContentViewBig);
        return mContentViewBig;
    }

    private void setUpRemoteView(RemoteViews remoteView) {
        remoteView.setImageViewResource(R.id.image_view_close, R.drawable.ic_remote_view_close);
        remoteView.setImageViewResource(R.id.image_view_play_last, R.drawable.ic_remote_view_play_last);
        remoteView.setImageViewResource(R.id.image_view_play_next, R.drawable.ic_remote_view_play_next);

        remoteView.setOnClickPendingIntent(R.id.button_close, getPendingIntent(ACTION_STOP_SERVICE));
        remoteView.setOnClickPendingIntent(R.id.button_play_last, getPendingIntent(ACTION_PLAY_LAST));
        remoteView.setOnClickPendingIntent(R.id.button_play_next, getPendingIntent(ACTION_PLAY_NEXT));
        remoteView.setOnClickPendingIntent(R.id.button_play_toggle, getPendingIntent(ACTION_PLAY_TOGGLE));
    }

    private void updateRemoteViews(RemoteViews remoteView) {
        Song currentSong = mPlayer.getPlayingSong();
        if (currentSong != null) {
            remoteView.setTextViewText(R.id.text_view_name, currentSong.getDisplayName());
            remoteView.setTextViewText(R.id.text_view_artist, currentSong.getArtist());
        }
        remoteView.setImageViewResource(R.id.image_view_play_toggle, isPlaying()
                ? R.drawable.ic_remote_view_pause : R.drawable.ic_remote_view_play);
        Bitmap album = AlbumUtils.parseAlbum(getPlayingSong());
        if (album == null) {
            remoteView.setImageViewResource(R.id.image_view_album, R.mipmap.ic_launcher);
        } else {
            remoteView.setImageViewBitmap(R.id.image_view_album, album);

        }
    }

    // PendingIntent

    private PendingIntent getPendingIntent(String action) {
        return PendingIntent.getService(this, 0, new Intent(action), 0);
    }
}
