# 安全事项

> 记录本项目已知的安全债与处理状态。核对日期：2026-11-01

---

## 1. Release keystore 口令泄露（未解决）

### 事实

`app/build.gradle` 原先硬编码了签名配置：

```groovy
signingConfigs {
    config {
        keyAlias 'themass'
        keyPassword 'themass'
        storeFile file('/Users/liguoqing/soft/myapp/key')
        storePassword 'themass'
    }
}
```

这段内容存在于 git 历史中（提交 `c44e3822` 及更早）。

### 已做

- 配置改为读取 `keystore.properties`，该文件已加入 `.gitignore`
- 提供了模板 `keystore.properties.example`

### 未做（必须由你处理）

**读配置的方式改了，但密钥本身没有换。** 任何拿到这份仓库历史的人都能用该口令解开对应的 keystore。必须：

```bash
keytool -genkeypair -v -keystore release.jks -alias <new-alias> \
        -keyalg RSA -keysize 4096 -validity 10000
```

然后把新 keystore 的路径与口令写进 `keystore.properties`。

**注意**：更换签名密钥意味着无法用新包覆盖已发布的旧版本（应用商店会拒绝签名不一致的更新）。若已在 Google Play 上架，应走 Play App Signing 的上传密钥轮换流程；若尚未发布，现在换是最低成本的时机。

## 2. 明文 HTTP 传输（未解决）

- `network_security_config.xml` 全局 `cleartextTrafficPermitted="true"`
- API 基址是 `http://api.ok123find.top`，`Constants.getUrl()` 拼的是 `http://`

这意味着账号、密码、token 都以明文在网络上传输。上架 Google Play 时这是明确的合规风险，实际使用中是真实的中间人攻击面。

建议：服务端启用 HTTPS → 客户端 `getUrl()` 改 `https://` → 把 `cleartextTrafficPermitted` 收窄为只允许必要的调试域名（或直接设 false）。

## 3. 设备指纹认证强度不足（未解决）

`yewu/net/request/BaseRequest.java` 的 `authkey` 由 `UA + time + Md5(devid|time)` 派生，
本质是"知道设备 ID 就能在该时间窗内重放"。没有服务端参与签名，也没有真正的会话凭证。

## 4. WireGuard 私钥存储未加密（部分解决）

`WireGuardKeyStore` 当前用普通 `SharedPreferences` 存私钥 Base64。代码注释里写了"生产环境应走 Keystore"，但没有实现。

WireGuard 私钥泄露等于身份被盗（别人可以用你的公钥登录节点）。应改为：
- 用 Android Keystore 生成 AES 密钥加密私钥后落盘，或
- 直接把 WireGuard 私钥导入 Android Keystore（API 23+ 的 `KeyStore` 支持非对称，但 X25519 支持有限，前一种更现实）

## 5. 其他

- `Constants.java` 内含 Mobvista AppKey、AdMob 单元 ID、反馈 appKey。这些是广告 SDK 的公开标识，泄露风险远低于签名密钥，但如需严格隔离可移到 `BuildConfig`。
- 原生库升级到 6.1.0 后已修复 strongSwan 侧的 11 个 CVE（含 CVE-2026-78131 x509 use-after-free）。
