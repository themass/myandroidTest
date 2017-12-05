package com.timeline.sex.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import com.sspacee.common.util.LogUtil;
import com.timeline.sex.bean.vo.SoundItemsVo;

import java.util.List;

/***
 * 2013/5/25
 * @author wwj
 * 音乐播放服务
 */
public class PlayService extends Service {
    //服务要发送的一些Action
    public static final String UPDATE_ACTION = "com.sspacee.vpn.action.UPDATE_ACTION";    //更新动作
    public static final String CTL_ACTION = "com.sspacee.vpn.action.CTL_ACTION";        //控制动作
    public static final String MUSIC_CURRENT = "com.sspacee.vpn.action.MUSIC_CURRENT";    //当前音乐播放时间更新动作
    public static final String MUSIC_DURATION = "com.sspacee.vpn.action.MUSIC_DURATION";//新音乐长度更新动作
    public static final String MUSIC_PREPAREING = "com.sspacee.vpn.action.MUSIC_PREPAREING";//正在准备
    public static final String MUSIC_PREPARED = "com.sspacee.vpn.action.MUSIC_PREPARED";//加载完毕
    public static final String CURRENTTIME = "currentTime";
    public static final String DURATION = "duration";
    public static final String CURRENT = "current";
    private final IBinder mBinder = new LocalBinder();
    private MediaPlayer mediaPlayer; // 媒体播放器对象
    private String path;            // 音乐文件路径
    private boolean isPause;        // 暂停状态
    private int current = 0;        // 记录当前正在播放的音乐
    private List<SoundItemsVo> mp3Infos;    //存放Mp3Info对象的集合
    private int status = 3;            //播放状态，默认为顺序播放
    private MyReceiver myReceiver;    //自定义广播接收器
    private int currentTime;        //当前播放进度
    private int duration;            //播放长度
    private boolean prepared = false;
    /**
     * handler用来接收消息，来发送广播更新播放时间
     */
    private Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == 1) {
                if (mediaPlayer != null) {
                    if (prepared) {
                        currentTime = mediaPlayer.getCurrentPosition(); // 获取当前音乐播放的位置
                        Intent intent = new Intent();
                        intent.setAction(MUSIC_CURRENT);
                        intent.putExtra(CURRENTTIME, currentTime);
                        sendBroadcast(intent); // 给PlayerActivity发送广播
                    }
                    handler.sendEmptyMessageDelayed(1, 1000);
                }
            } else if (msg.what == 2) { //播放音乐
                playAsync();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.i("service created");
        mediaPlayer = new MediaPlayer();
        /**
         * 设置音乐播放完成时的监听器
         */
        mediaPlayer.setOnCompletionListener(new OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                if (status == 1) { // 单曲循环
                    mediaPlayer.start();
                } else if (status == 2) { // 全部循环
                    current++;
                    if (current > mp3Infos.size() - 1) {    //变为第一首的位置继续播放
                        current = 0;
                    }
                    Intent sendIntent = new Intent(UPDATE_ACTION);
                    sendIntent.putExtra(CURRENT, current);
                    // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                    sendBroadcast(sendIntent);
                    path = mp3Infos.get(current).file;
                    play(0);
                } else if (status == 3) { // 顺序播放
                    current++;    //下一首位置
                    if (current <= mp3Infos.size() - 1) {
                        Intent sendIntent = new Intent(UPDATE_ACTION);
                        sendIntent.putExtra(CURRENT, current);
                        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                        sendBroadcast(sendIntent);
                        path = mp3Infos.get(current).file;
                        play(0);
                    } else {
                        mediaPlayer.seekTo(0);
                        current = 0;
                        Intent sendIntent = new Intent(UPDATE_ACTION);
                        sendIntent.putExtra(CURRENT, current);
                        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                        sendBroadcast(sendIntent);
                    }
                } else if (status == 4) {    //随机播放
                    current = getRandomIndex(mp3Infos.size() - 1);
                    System.out.println("currentIndex ->" + current);
                    Intent sendIntent = new Intent(UPDATE_ACTION);
                    sendIntent.putExtra(CURRENT, current);
                    // 发送广播，将被Activity组件中的BroadcastReceiver接收到
                    sendBroadcast(sendIntent);
                    path = mp3Infos.get(current).file;
                    play(0);
                }
            }
        });

        myReceiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CTL_ACTION);
        registerReceiver(myReceiver, filter);
    }

    /**
     * 获取随机位置
     *
     * @param end
     * @return
     */
    protected int getRandomIndex(int end) {
        int index = (int) (Math.random() * end);
        return index;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    public void playAsync() {
        try {
            LogUtil.i("正在准备准备：" + path);
            prepared = false;
            mediaPlayer.reset();// 把各项参数恢复到初始状态
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnPreparedListener(new PreparedListener(currentTime));// 注册一个监听器
            mediaPlayer.prepareAsync(); // 进行缓冲
            handler.sendEmptyMessage(1);
            Intent sendIntent = new Intent(MUSIC_PREPAREING);
            // 发送广播，将被Activity组件中的BroadcastReceiver接收到
            sendBroadcast(sendIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//	@Override
//	public void onStart(Intent intent, int startId) {
//		path = intent.getStringExtra("url");		//歌曲路径
//		current = intent.getIntExtra("listPosition", -1);	//当前播放歌曲的在mp3Infos的位置
//		msg = intent.getIntExtra("MSG", 0);			//播放信息
//		if (msg == AppConstant.PlayerMsg.PLAY_MSG) {	//直接播放音乐
//			play(0);
//		} else if (msg == AppConstant.PlayerMsg.PAUSE_MSG) {	//暂停
//			pause();
//		} else if (msg == AppConstant.PlayerMsg.STOP_MSG) {		//停止
//			stop();
//		} else if (msg == AppConstant.PlayerMsg.CONTINUE_MSG) {	//继续播放
//			resume();
//		} else if (msg == AppConstant.PlayerMsg.PRIVIOUS_MSG) {	//上一首
//			previous();
//		} else if (msg == AppConstant.PlayerMsg.NEXT_MSG) {		//下一首
//			next();
//		} else if (msg == AppConstant.PlayerMsg.PROGRESS_CHANGE) {	//进度更新
//			currentTime = intent.getIntExtra("progress", -1);
//			play(currentTime);
//		} else if (msg == AppConstant.PlayerMsg.PLAYING_MSG) {
//			handler.sendEmptyMessage(1);
//		}
//		super.onStart(intent, startId);
//	}

    /**
     * 播放音乐
     *
     * @param currentTime
     */
    public void play(int currentTime) {
        this.currentTime = currentTime;
        handler.sendEmptyMessage(2);
    }

    public void setData(List<SoundItemsVo> mp3Infos) {
        this.mp3Infos = mp3Infos;
    }

    /**
     * 暂停音乐
     */

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPause = true;
        }
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            return mediaPlayer.isPlaying();
        }
        return false;
    }

    public void resume() {
        if (isPause) {
            mediaPlayer.start();
            isPause = false;
        }
    }

    public int getPosition() {
        return currentTime;
    }

    /**
     * 上一首
     */
    public void previous() {
        Intent sendIntent = new Intent(UPDATE_ACTION);
        sendIntent.putExtra(CURRENT, current);
        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
        sendBroadcast(sendIntent);
        play(0);
    }

    /**
     * 下一首
     */
    public void next() {
        Intent sendIntent = new Intent(UPDATE_ACTION);
        sendIntent.putExtra(CURRENT, current);
        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
        sendBroadcast(sendIntent);
        play(0);
    }

    public void playPosi(int position) {
        LogUtil.i("service play ：" + position);
        current = position;
        path = mp3Infos.get(position).file;
        Intent sendIntent = new Intent(UPDATE_ACTION);
        sendIntent.putExtra(CURRENT, current);
        // 发送广播，将被Activity组件中的BroadcastReceiver接收到
        sendBroadcast(sendIntent);
        play(0);
    }

    /**
     * 停止音乐
     */
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            try {
                mediaPlayer.prepare(); // 在调用stop后如果需要再次通过start进行播放,需要之前调用prepare函数
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (myReceiver != null) {
            unregisterReceiver(myReceiver);
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

    }

    public class LocalBinder extends Binder {

        public PlayService getService() {
            // Return this instance of PlayerService so clients can call public methods
            return PlayService.this;
        }
    }

    /**
     * 实现一个OnPrepareLister接口,当音乐准备好的时候开始播放
     */
    private final class PreparedListener implements OnPreparedListener {
        private int currentTime;

        public PreparedListener(int currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            LogUtil.i("歌曲准备完毕：" + path);
            prepared = true;
            mediaPlayer.start(); // 开始播放
            if (currentTime > 0) { // 如果音乐不是从头播放
                mediaPlayer.seekTo(currentTime);
            }
            Intent intent1 = new Intent();
            intent1.setAction(MUSIC_PREPARED);
            sendBroadcast(intent1);
            Intent intent = new Intent();
            intent.setAction(MUSIC_DURATION);
            duration = mediaPlayer.getDuration();
            intent.putExtra(DURATION, duration);    //通过Intent来传递歌曲的总长度
            sendBroadcast(intent);
        }
    }

    public class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int control = intent.getIntExtra("control", -1);
            switch (control) {
                case 1:
                    status = 1; // 将播放状态置为1表示：单曲循环
                    break;
                case 2:
                    status = 2;    //将播放状态置为2表示：全部循环
                    break;
                case 3:
                    status = 3;    //将播放状态置为3表示：顺序播放
                    break;
                case 4:
                    status = 4;    //将播放状态置为4表示：随机播放
                    break;
            }
        }
    }

}
