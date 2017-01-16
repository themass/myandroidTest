package com.timeline.vpn.common.net;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.timeline.vpn.common.util.CollectionUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.PackageUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
            StringBuilder stringBuffer = new StringBuilder();
            String content;
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

    public static void upload(Context context, String actionUrl, File file, String str) {
        String end = "/r/n";
        String Hyphens = "--";
        String boundary = "*****";
        try {
            URL url = new URL(actionUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
      /* 允许Input、Output，不使用Cache */
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);
      /* 设定传送的method=POST */
            con.setRequestMethod("POST");
      /* setRequestProperty */
            con.setRequestProperty("Connection", "Keep-Alive");
            con.setRequestProperty("Charset", "UTF-8");
      /* 设定DataOutputStream */
            DataOutputStream ds = new DataOutputStream(con.getOutputStream());
            ds.writeBytes(Hyphens + boundary + end);
            ds.writeBytes(end);
      /* 取得文件的FileInputStream */
            FileInputStream fStream = new FileInputStream(file);
      /* 设定每次写入1024bytes */
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int length;
      /* 从文件读取数据到缓冲区 */
            while ((length = fStream.read(buffer)) != -1) {
        /* 将数据写入DataOutputStream中 */
                ds.write(buffer, 0, length);
            }
            ds.writeBytes(end);
            ds.writeBytes(Hyphens + boundary + Hyphens + end);
            fStream.close();
            ds.flush();
      /* 取得Response内容 */
            InputStream is = con.getInputStream();
            int ch;
            StringBuilder sb = new StringBuilder();
            while ((ch = is.read()) != -1) {
                sb.append((char) ch);
            }
            LogUtil.i("上传成功");
            Toast.makeText(context, "上传成功", Toast.LENGTH_LONG).show();
            ds.close();
        } catch (Exception e) {
            LogUtil.e(e);
            Toast.makeText(context, "上传失败" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    public static void download(Context context, String url, File file, DownloadListener listener) throws IOException {
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
                byte[] buf = new byte[1024 * 4];
                conn.connect();
                if (conn.getResponseCode() >= 400) {
                    Toast.makeText(context, "连接超时", Toast.LENGTH_SHORT)
                            .show();
                } else {
                    int total = conn.getContentLength();
                    int currentLength = 0;
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        currentLength += len;
                        if (listener != null) {
                            listener.onDownloading(currentLength, total);
                        }
                    }
                }
                conn.disconnect();
                fos.close();
                if (is != null)
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
