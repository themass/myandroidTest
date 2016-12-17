package com.timeline.vpn.common.net;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.timeline.vpn.common.util.CollectionUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PackageUtils;

import org.apache.http.HttpEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Map;

public class HttpUtils {
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static String USER_AGENT_SUFFIX = "VPN/%s";

    public static boolean isGzip(String name, String value) {
        return "Content-Encoding".equals(name) && "gzip".equals(value);
    }

    public static boolean isGzip(String value) {
        return "gzip".equals(value);
    }

    public static String getUserAgentSuffix(Context context) {
        return String.format(USER_AGENT_SUFFIX, PackageUtils.getAppVersion(context));
    }

    public static okhttp3.Headers getOkHeader() {
        return new okhttp3.Headers.Builder().build();
    }

    public static int ping(String ip) {
        String result = null;
        try {
            Process p = Runtime.getRuntime().exec("ping -c 2 -w 100 " + ip);
            InputStream input = p.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            StringBuffer stringBuffer = new StringBuffer();
            String content = "";
            while ((content = in.readLine()) != null) {
                stringBuffer.append(content);
            }
            Log.i("TTT", "result content : " + stringBuffer.toString());
            int status = p.waitFor();
            if (status == 0) {
                result = "successful~";
                return 1;
            } else {
                result = "failed~ cannot reach the IP address";
            }
        } catch (IOException e) {
            result = "failed~ IOException";
        } catch (InterruptedException e) {
            result = "failed~ InterruptedException";
        } finally {
            Log.i("TTT", "result = " + result);
        }
        return -1;
    }
    public static String generateGetUrl(String url, Map<String, String> params) {
        if (!CollectionUtils.isEmpty(params)) {
            StringBuilder sb = new StringBuilder(url).append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                sb.append(entry.getKey()).append("=").append(encoderParam(entry.getValue()))
                        .append("&");
            }
            return sb.toString();
        }
        return url;
    }

    private static String encoderParam(String param) {
        try {
            return URLEncoder.encode(param, DEFAULT_CHARSET);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void download(Context context, String url, File file, DownloadListener listener) throws IOException {
        FileOutputStream out = null;
        HttpEntity entity = null;
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        File fileTemp = new File(file.getAbsolutePath() + "." + Calendar.getInstance().getTimeInMillis());
        try {
            URL dUrl = new URL(url);
            try {
                HttpURLConnection conn = (HttpURLConnection) dUrl
                        .openConnection();
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buf = new byte[256];
                conn.connect();
                double count = 0;
                if (conn.getResponseCode() >= 400) {
                    Toast.makeText(context, "连接超时", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    while (count <= 100) {
                        if (is != null) {
                            int numRead = is.read(buf);
                            if (numRead <= 0) {
                                break;
                            } else {
                                fos.write(buf, 0, numRead);
                            }

                        } else {
                            break;
                        }
                    }
                }
                conn.disconnect();
                fos.close();
                is.close();
            } catch (IOException e) {
                LogUtil.e(e);
            }
        } finally {
            fileTemp.delete();
        }
    }

    public static interface DownloadListener {
        public void onDownloading(long current, long total);
    }

}
