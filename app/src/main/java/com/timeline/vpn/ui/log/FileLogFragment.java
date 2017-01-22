/*
 * Copyright (C) 2012 Tobias Brunner
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package com.timeline.vpn.ui.log;

import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.FileUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.log.BaseFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;

import butterknife.Bind;

public class FileLogFragment extends BaseFragment implements Runnable {
    @Bind(R.id.log_view)
    TextView mLogView;
    @Bind(R.id.scroll_view)
    LogScrollView mScrollView;
    private String mLogFilePath;
    private Handler mLogHandler;
    private BufferedReader mReader;
    private Thread mThread;
    private volatile boolean mRunning;
    private FileObserver mDirectoryObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogFilePath = FileUtils.getCharonFilePath();
        /* use a handler to update the log view */
        mLogHandler = new Handler();
        mDirectoryObserver = new LogDirectoryObserver(getActivity().getFilesDir().getAbsolutePath());
    }

    @Override
    protected int getRootViewId() {
        return R.layout.log_fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        startLogReader();
        mDirectoryObserver.startWatching();
    }

    @Override
    public void onStop() {
        super.onStop();
        mDirectoryObserver.stopWatching();
        stopLogReader();
    }

    /**
     * Start reading from the log file
     */
    private void startLogReader() {
        try {
            mReader = new BufferedReader(new FileReader(mLogFilePath));
        } catch (FileNotFoundException e) {
            mReader = new BufferedReader(new StringReader(""));
        }

        mLogView.setText("");
        mRunning = true;
        mThread = new Thread(this);
        mThread.start();
    }

    /**
     * Stop reading from the log file
     */
    private void stopLogReader() {
        try {
            mRunning = false;
            mThread.interrupt();
            mThread.join();
        } catch (InterruptedException e) {
            LogUtil.e(e);
        }
    }

    /**
     * Write the given log line to the TextView. We strip the prefix off to save
     * some space (it is not that helpful for regular users anyway).
     *
     * @param line log line to log
     */
    public void logLine(final String line) {
        mLogHandler.post(new Runnable() {
            @Override
            public void run() {
                /* strip off prefix (month=3, day=2, time=8, thread=2, spaces=3) */
                mLogView.append((line.length() > 18 ? line.substring(18) : line) + '\n');
				/* calling autoScroll() directly does not work, probably because content
				 * is not yet updated, so we post this to be done later */
                mScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        mScrollView.autoScroll();
                    }
                });
            }
        });
    }

    @Override
    public void run() {
        while (mRunning) {
            try {	/* this works as long as the file is not truncated */
                String line = mReader.readLine();
                if (line == null) {	/* wait until there is more to log */
                    Thread.sleep(1000);
                } else {
                    logLine(line);
                }
            } catch (Exception e) {
                break;
            }
        }
    }

    /**
     * FileObserver that checks for changes regarding the log file. Since charon
     * truncates it (for which there is no explicit event) we check for any modification
     * to the file, keep track of the file size and reopen it if it got smaller.
     */
    private class LogDirectoryObserver extends FileObserver {
        private final File mFile;
        private long mSize;

        public LogDirectoryObserver(String path) {
            super(path, FileObserver.CREATE | FileObserver.MODIFY | FileObserver.DELETE);
            mFile = new File(mLogFilePath);
            mSize = mFile.length();
        }

        @Override
        public void onEvent(int event, String path) {
            if (path == null || !path.equals(Constants.LOG_FILE)) {
                return;
            }
            switch (event) {	/* even though we only subscribed for these we check them,
				 * as strange events are sometimes received */
                case FileObserver.CREATE:
                case FileObserver.DELETE:
                    restartLogReader();
                    break;
                case FileObserver.MODIFY:
					/* if the size got smaller reopen the log file, as it was probably truncated */
                    long size = mFile.length();
                    if (size < mSize) {
                        restartLogReader();
                    }
                    mSize = size;
                    break;
            }
        }

        private void restartLogReader() {
			/* we are called from a separate thread, so we use the handler */
            mLogHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopLogReader();
                    startLogReader();
                }
            });
        }
    }
}
