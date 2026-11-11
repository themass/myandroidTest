import java.net.HttpURLConnection;
import java.net.URL;

/** 设备端 HTTP 检查（替代 curl）：打印 "<状态码> <耗时ms>"，失败打印 "000 <原因>"。 */
public class HttpCheck {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "https://www.google.com/generate_204";
        long t0 = System.currentTimeMillis();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            c.disconnect();
            System.out.println(code + " " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Exception e) {
            System.out.println("000 " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
