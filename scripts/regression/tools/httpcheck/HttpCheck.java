import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 设备端 HTTP 检查（替代 curl）：打印 "&lt;状态码&gt; &lt;耗时ms&gt;"，失败打印 "000 &lt;原因&gt;"。
 * <p>
 * 两个刻意的设计：
 * <ul>
 *   <li>强制走 IPv4：隧道目前只承载 IPv4，不少网络会把 google.com 解析成 IPv6，
 *       直连 v6 会绕过隧道，检查结果就失真了。</li>
 *   <li>保留原域名的 SNI：先解析出 IPv4 再连 IP，但 TLS 握手仍用域名，
 *       否则证书校验会失败（服务器返回的是 IP 的证书）。</li>
 * </ul>
 */
public class HttpCheck {

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "https://www.google.com/generate_204";
        long t0 = System.currentTimeMillis();
        Socket raw = null;
        SSLSocket ssl = null;
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : ("https".equals(u.getScheme()) ? 443 : 80);
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();

            InetAddress target = null;
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a instanceof Inet4Address) {
                    target = a;
                    break;
                }
            }
            if (target == null) {
                target = InetAddress.getByName(host);
            }

            raw = new Socket();
            raw.connect(new InetSocketAddress(target, port), 15000);
            raw.setSoTimeout(15000);

            Socket conn = raw;
            if ("https".equals(u.getScheme())) {
                ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(raw, host, port, true);
                ssl.startHandshake();
                conn = ssl;
            }

            OutputStream out = conn.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + host
                    + "\r\nConnection: close\r\nAccept-Encoding: identity\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = conn.getInputStream();
            StringBuilder head = new StringBuilder();
            int b;
            while ((b = in.read()) >= 0) {
                head.append((char) b);
                if (head.length() > 12 && head.toString().endsWith("\r\n")) {
                    break;
                }
            }
            String statusLine = head.toString().split("\r\n")[0].trim();
            String code = statusLine.split(" ").length > 1 ? statusLine.split(" ")[1] : "000";
            System.out.println(code + " " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Exception e) {
            System.out.println("000 " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            try {
                if (ssl != null) {
                    ssl.close();
                } else if (raw != null) {
                    raw.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
