#!/usr/bin/env python3
"""
VIP2 全节点回归（只用 adb，不需要人工点击）。

每个节点的判定：
  1. 在节点列表里选中该节点，首页卡片显示的名字与其一致；
  2. 连接，探针通过，VPN 网络存在；
  3. 打开节点列表页，VIP2 的节点名能展示出来；
  4. 设备端 curl 访问 https://www.google.com/generate_204 返回 204，且 ip route 显示走 tun 接口；
  5. 读取隧道接口的 MTU（tun* / wg*）；
  6. 断开，VPN 网络消失，App 进程 pid 不变。
全部通过才记 PASS，任一步失败记 FAIL 并写明原因，然后继续下一个节点。

输出：runs/vip2_<时间>/summary.csv 和 每个节点的 logcat 原文。
"""
import csv, os, re, subprocess, sys, time, datetime

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "com.openapi.ks.free1"
HOME_ACT = "com.openapi.ks.ui.main.MainFragmentViewPage"
LIST_ACT = "com.openapi.myapp.ui.base.LocationFragmentActivity"

# 首页按钮坐标按屏幕尺寸取（首页有地球动画，uiautomator 拿不到 idle，只能用坐标）
PROFILES = {
    "1080x2460": {"connect": (840, 2074), "server": (320, 2060)},   # 小米
    "1256x2760": {"connect": (960, 2204), "server": (366, 2204)},   # 华为 ADY-AL00
}
VIP2_TAB = (674, 324)         # 只在找不到 "VIP2" 文字时兜底


def _screen():
    out = subprocess.run([ADB, "shell", "wm", "size"], capture_output=True, text=True).stdout
    m = re.search(r"(\d+x\d+)", out)
    return m.group(1) if m else ""


SIZE = _screen()
if SIZE not in PROFILES:
    print("未知屏幕尺寸，需要先在 PROFILES 里加坐标:", SIZE); sys.exit(2)
CONNECT_BTN = PROFILES[SIZE]["connect"]
SERVER_BOX = PROFILES[SIZE]["server"]
_w, _h = map(int, SIZE.split("x"))
SWIPE = (_w // 2, int(_h * 0.77), _w // 2, int(_h * 0.41))


def adb(*args, timeout=30):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True, timeout=timeout).stdout


def sh(cmd, timeout=30):
    return adb("shell", cmd, timeout=timeout)


def focus():
    """当前前台窗口。华为等机型上 mCurrentFocus 经常是 null，
    改用 mFocusedApp / resumed activity 兜底，否则永远判不出"是否在首页"。"""
    out = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2").strip()
    if "null" not in out and out:
        return out
    # 再退一步：看哪个 Activity 处于 RESUMED
    alt = sh("dumpsys activity activities | grep -E 'ResumedActivity|mResumedActivity' | head -1").strip()
    return alt or out


def pid():
    return sh(f"pidof {PKG}").strip()


def vpn_count():
    return sh("dumpsys connectivity").count("ni{VPN")


def logcat():
    return adb("logcat", "-d", "-v", "time")


def dump():
    # 先删旧文件：华为上 dump 失败时会留下上一次的文件，读到的是过期界面
    sh("rm -f /sdcard/ui.xml")
    sh("uiautomator dump /sdcard/ui.xml")
    try:
        os.remove("/tmp/_vip2_ui.xml")
    except OSError:
        pass
    subprocess.run([ADB, "pull", "/sdcard/ui.xml", "/tmp/_vip2_ui.xml"], capture_output=True)
    try:
        return open("/tmp/_vip2_ui.xml", encoding="utf-8").read()
    except OSError:
        return ""


def nodes_on_screen(xml):
    """返回 [(文字, (x,y))]，只要有文字的节点。"""
    out = []
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        t = re.search(r'text="([^"]*)"', n).group(1)
        if not t:
            continue
        a, b, c, d = map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', n).group(1)))
        out.append((t, ((a + c) // 2, (b + d) // 2)))
    return out


def find(name):
    for t, xy in nodes_on_screen(dump()):
        if t == name:
            return xy
    return None


def tap(xy):
    sh(f"input tap {xy[0]} {xy[1]}")


def back(times=1):
    for _ in range(times):
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1.2)


def go_home():
    for _ in range(4):
        if HOME_ACT in focus():
            return True
        back()
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(6)
    return HOME_ACT in focus()


def tap_vip2_tab(timeout=30):
    """等列表数据回来、标签栏出现后点 VIP2；返回是否点到。"""
    t0 = time.time()
    while time.time() - t0 < timeout:
        for t, xy in nodes_on_screen(dump()):
            if t == "VIP2":
                tap(xy)
                time.sleep(1.5)
                return True
        time.sleep(1)
    return False


def open_list():
    tap(SERVER_BOX)
    time.sleep(2.5)
    if not tap_vip2_tab():
        tap(VIP2_TAB)
        time.sleep(1.5)


def chosen_in_prefs(name):
    """首页 dump 不可用时，直接读 App 保存的选中节点（debug 包可 run-as）。"""
    xml = sh(f"run-as {PKG} cat shared_prefs/{PKG}_preferences.xml")
    m = re.search(r'name="LOCATION_CHOOSE1">([^<]*)<', xml)
    return bool(m) and f"&quot;{name}&quot;" in m.group(1)


def all_vip2_names():
    """打开列表、切到 VIP2，滚动收集所有节点名（去掉 Best/Auto）。"""
    go_home()
    open_list()
    names, seen_rounds = [], 0
    for _ in range(12):
        new = 0
        for t, _xy in nodes_on_screen(dump()):
            if re.fullmatch(r"Test-[A-Za-z0-9\-]+", t) and t not in names:
                names.append(t)
                new += 1
        seen_rounds = 0 if new else seen_rounds + 1
        if seen_rounds >= 2:
            break
        sh(f"input swipe {SWIPE[0]} {SWIPE[1]} {SWIPE[2]} {SWIPE[3]} 400")
        time.sleep(1.2)
    back()
    go_home()
    return names


def select_node(name):
    go_home()
    open_list()
    xy = find(name)
    for _ in range(8):
        if xy:
            break
        sh(f"input swipe {SWIPE[0]} {SWIPE[1]} {SWIPE[2]} {SWIPE[3]} 400")
        time.sleep(1.2)
        xy = find(name)
    if not xy:
        back(2)
        return False, "列表里找不到节点"
    tap(xy)
    time.sleep(2)
    back()
    time.sleep(2)
    go_home()
    # 首页卡片：能 dump 就看卡片文字；dump 不可用（地球动画）就读保存的选中节点
    for _ in range(8):
        time.sleep(1.5)
        if HOME_ACT not in focus():
            continue
        card = [t for t, _ in nodes_on_screen(dump())]
        if name in card or chosen_in_prefs(name):
            return True, ""
    return False, "选中后首页卡片不是该节点"


def check_list_shows(names, timeout=30):
    """打开节点列表页，等 VIP2 标签出现后点它，确认 VIP2 的节点名能显示。返回 (ok, 可见文字数, 加载秒数)。"""
    go_home()
    tap(SERVER_BOX)
    t0 = time.time()
    tapped, visible, ok = False, set(), False
    tapped_at = 0.0
    while time.time() - t0 < timeout:
        time.sleep(1)
        items = nodes_on_screen(dump())
        visible = set(t for t, _ in items)
        if not tapped:
            # 标签栏比列表内容先出现：VIP2 文字一出现就点，点击会落在这个还在 layout 的
            # 页面上被丢掉（实测偶发：可见 22 个 Free 节点，说明标签根本没切过去）。
            # 等 Free 列表真正渲染出来（"Best / Auto" 是它的第一项）再点。
            ready = any(t == "Best / Auto" for t, _ in items)
            vip2 = [xy for t, xy in items if t == "VIP2"]
            if ready and vip2:
                tap(vip2[0]); tapped = True; tapped_at = time.time()
            continue
        if any(n in visible for n in names):
            ok = True
            break
        # 点了 VIP2 却没切过去（列表还在加载、点击落在遮罩上）。标签栏一直在，
        # 隔几秒补一次点击，否则一次点空就干等到超时，把"点击没生效"误报成"列表加载失败"。
        if tapped_at and time.time() - tapped_at > 5:
            vip2 = [xy for t, xy in items if t == "VIP2"]
            if vip2:
                tap(vip2[0]); tapped_at = time.time()
    secs = round(time.time() - t0)
    back()
    go_home()
    return ok, len(visible), secs


def wait_probe(timeout=60):
    t0 = time.time()
    while time.time() - t0 < timeout:
        log = logcat()
        if "探针通过" in log:
            return "OK", log
        if "探测流量不通" in log or "疑似 MTU" in log or "FATAL EXCEPTION" in log:
            return "FAIL", log
        time.sleep(1)
    return "TIMEOUT", logcat()


# 没有 curl 的设备用这个 dex 发 HTTP 请求。用 tools/httpcheck/build.sh 构建。
HTTPCHECK_DEX = os.environ.get(
    "HTTPCHECK_DEX",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "tools", "httpcheck", "classes.dex"))
_has_curl = None


def http_code(url):
    """设备端取 HTTP 状态码。有 curl 用 curl；没有（如华为）就用 app_process 跑一个小 dex，
    同样以 shell 身份发请求，流量一样走 VPN。"""
    global _has_curl
    if _has_curl is None:
        _has_curl = bool(sh("which curl").strip())
        if not _has_curl:
            subprocess.run([ADB, "push", HTTPCHECK_DEX, "/data/local/tmp/httpcheck.dex"],
                           capture_output=True)
    if _has_curl:
        return sh(f"curl -s -o /dev/null -w %{{http_code}} --max-time 20 {url}", timeout=40).strip()
    out = sh(f"CLASSPATH=/data/local/tmp/httpcheck.dex app_process / HttpCheck {url}", timeout=40).strip()
    return out.split()[0] if out else ""


def google_ok():
    # 用 gstatic 而不是 www.google.com：不少国内网络的 DNS 会把 www.google.com
    # 解析到无关地址（实测解析成 Facebook 的 69.171.235.22），连不上并不说明隧道有问题。
    # gstatic 同样返回 204 且解析稳定，更适合当作"出网了没有"的判定。
    code = http_code("https://www.gstatic.com/generate_204")
    route = sh("ip route get 142.250.80.46 2>/dev/null | head -1").strip()
    via_tun = " dev tun" in route or " dev wg" in route or " dev vpn" in route
    return code == "204", code, via_tun, route


def tunnel_mtu():
    """读隧道接口 MTU：解析 ifconfig（sysfs 在 Android 上不可读）。格式：接口名在块首行，MTU:N 在其后。"""
    out = sh("ifconfig")
    vals, name = [], None
    for line in out.splitlines():
        if line and not line[0].isspace():
            name = line.split()[0]
        m = re.search(r"MTU:(\d+)", line)
        if m and name and (name.startswith("tun") or name.startswith("wg")):
            vals.append(f"{name}={m.group(1)}")
    return ",".join(vals)


def disconnect():
    tap(CONNECT_BTN)
    t0 = time.time()
    while time.time() - t0 < 30:
        if vpn_count() == 0:
            break
        time.sleep(0.5)
    time.sleep(6)
    return vpn_count() == 0


def main():
    base = os.environ.get("RUNS_DIR",
                          os.path.join(os.path.dirname(os.path.abspath(__file__)), "runs"))
    out = os.path.join(base, "vip2_" + datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))
    os.makedirs(out, exist_ok=True)
    csv_path = os.path.join(out, "summary.csv")
    fields = ["node", "result", "select", "probe", "list_shows", "google", "google_via_tun", "mtu", "disconnect", "pid_same", "detail"]
    f = open(csv_path, "w", newline="", encoding="utf-8")
    w = csv.DictWriter(f, fieldnames=fields)
    w.writeheader()

    if vpn_count() != 0:
        print("开始前 VPN 已连接，请先断开"); sys.exit(2)
    if not go_home():
        print("App 无法回到首页：", focus()); sys.exit(2)

    names = all_vip2_names()
    only = os.environ.get("NODES")
    if only:
        names = [n for n in names if n in only.split(",")]
    print(f"VIP2 节点 {len(names)} 个：{', '.join(names)}", flush=True)
    if not names:
        print("没有找到 VIP2 节点，检查是否已登录"); sys.exit(2)

    run_pid = pid()
    for name in names:
        row = {k: "" for k in fields}
        row["node"] = name
        detail = []
        try:
            ok, why = select_node(name)
            row["select"] = "OK" if ok else "FAIL"
            if not ok:
                raise RuntimeError(why)

            sh("logcat -c")
            tap(CONNECT_BTN)
            probe, log = wait_probe(70)
            row["probe"] = probe
            with open(os.path.join(out, f"{name}_connect.log"), "w", encoding="utf-8") as lf:
                lf.write(log)
            if probe != "OK" or vpn_count() == 0:
                raise RuntimeError(f"探针 {probe}")

            list_ok, n_visible, list_secs = check_list_shows(names)
            row["list_shows"] = f"OK({list_secs}s)" if list_ok else f"FAIL({list_secs}s)"
            if not list_ok:
                detail.append(f"列表页看不到节点(可见{n_visible}个)")

            g_ok, code, via_tun, route = google_ok()
            row["google"] = code
            row["google_via_tun"] = "yes" if via_tun else "no"
            if not g_ok:
                detail.append(f"google 返回 {code}")
            if not via_tun:
                detail.append("路由不经 tun")
            row["mtu"] = tunnel_mtu()

            row["disconnect"] = "OK" if disconnect() else "FAIL"
            row["pid_same"] = "1" if pid() == run_pid else "0"

            problems = []
            if not row["list_shows"].startswith("OK"): problems.append("列表")
            if not g_ok or not via_tun: problems.append("google")
            if row["disconnect"] != "OK": problems.append("断开")
            if row["pid_same"] != "1": problems.append("pid变化")
            row["result"] = "PASS" if not problems else "FAIL"
            row["detail"] = "; ".join(detail) or "ok"
        except Exception as e:
            row["result"] = "FAIL"
            detail.append(str(e))
            row["detail"] = "; ".join(detail)
            if vpn_count() != 0:
                disconnect()
            go_home()
        w.writerow(row); f.flush()
        print(f"{name:<18} {row['result']:<4} probe={row['probe']:<7} list={row['list_shows']:<4} "
              f"google={row['google']:<4} tun={row['google_via_tun']:<3} mtu=[{row['mtu']}] "
              f"disc={row['disconnect']:<4} pid_same={row['pid_same']} | {row['detail']}", flush=True)

    f.close()
    print("汇总:", csv_path, flush=True)


if __name__ == "__main__":
    main()
