# ShieldWing Privacy Policy

**Last updated: November 14, 2026**

This policy explains how ShieldWing (the "app") collects, uses and protects your information. It applies to the ShieldWing Android application and nothing else.

Questions: **justbegin010@gmail.com**.

## 1. What this app is

ShieldWing is a VPN client. Using Android's **VpnService API**, it creates an encrypted tunnel between your device and our servers, and encrypts the network traffic that passes through that tunnel.

**ShieldWing does not provide web browsing, content feeds, chat or social features.**

## 2. What we collect

We collect only the categories needed to run the service. This table matches the Data Safety declaration filed with Google Play.

| Data type | What exactly | Why | Required? |
|---|---|---|---|
| **Device or other IDs** | Device identifier (`devId`), Android Advertising ID | Identify the device to allocate node quota and prevent abuse. The advertising ID is used only to serve and measure ads. | Yes. The advertising ID can be reset or deleted in Android settings. |
| **IP address** | The public IP you connect from | Pick a nearby node, diagnose connection failures, prevent abuse | Yes |
| **App activity** | Connect/disconnect times, session duration, bytes uploaded and downloaded, selected node, protocol used, and features used such as favourites and split tunneling | Usage statistics, node quality, understanding which features are used | Yes |
| **Crash logs and diagnostics** | Crash stack traces and error logs | Fix defects | Yes |
| **Account information (optional)** | Username, e-mail, password hash, membership tier and points | Sign-in, membership benefits, password recovery | Only if you register |

### What we do not collect

- **We do not record the websites you visit, your DNS queries, page content or communications.** Traffic inside the tunnel is not decrypted, stored or analysed.
- We do not read your contacts, SMS, call log, photo library, microphone, camera or precise location.
- We do not use GPS. The "my location" marker on the globe is derived from your system time zone, computed on the device only, and never uploaded.

## 3. How we use it

We use the information above to:

1. Establish and maintain the VPN connection;
2. Allocate nodes and prevent a single device from abusing quota;
3. Show usage statistics, which stay on your device by default;
4. Diagnose connection failures and crashes;
5. Serve and measure advertising for the free tier; and
6. Provide sign-in and membership benefits when you register.

## 4. Sharing

We **do not sell** your personal information. The following count as sharing:

| Recipient | Data | Purpose |
|---|---|---|
| **Google AdMob** | Advertising ID, IP address, device information, in-app interaction events | Serving and measuring ads. AdMob may use this to build advertising profiles across apps. See Google's privacy policy. |
| **Cloud hosting provider** | The server-side data described above | Data processor only; stores data on our instructions and does not use it for its own purposes |

Because the advertising SDK may use device identifiers for cross-app advertising, Google Play treats this as "sharing", and we declare it as such in the Data Safety form.

## 5. Retention

| Data | Retained for |
|---|---|
| Daily usage summary (duration, traffic, node, feature use) | 24 months, then aggregated into statistics that cannot identify a person |
| Crash logs | 12 months |
| Account information | Until you delete your account |
| Advertising ID | Controlled by you through Android settings; we do not retain it separately |

## 6. Your rights

### Deleting your account

You can delete your account inside the app under **Settings → Account → Delete account**, confirmed with your password. After deletion:

- The account record, any nodes you saved, and your login credentials are permanently deleted;
- Usage statistics already collected are kept free of any link to you, as anonymous records; and
- The action cannot be undone.

**If you cannot sign in, or have already uninstalled the app**, submit a deletion request at <https://file.ok123find.top/account-delete.html> or e-mail **justbegin010@gmail.com**. We handle these requests within 30 days.

### Other rights

- **Access and correction:** view your account information in the app, or contact us to correct it.
- **Opt out of personalised advertising:** Android Settings → Privacy → Ads → delete advertising ID or turn off personalised ads.
- **Withdraw consent:** users in the EEA, UK and Switzerland see a consent form; it can be triggered again later from Settings → Privacy in the app.

## 7. Children

This app is not directed at children under 13, and we do not knowingly collect personal information from them. If you become aware of such a case, contact us and we will delete it.

## 8. Security

- Traffic inside the tunnel uses industry-standard encryption (IKEv2/IPsec, WireGuard and VLESS-Reality);
- Client-to-server API traffic uses HTTPS; and
- Passwords are stored as hashes; we cannot recover the plaintext.

No system can guarantee absolute security. A VPN protects the segment between your device and our servers; websites you visit can still see your requests after the tunnel exit.

## 9. International transfers

Our servers are located in several countries and regions. When you use the app, your connection data may be transferred to servers outside your country. We protect this data with encrypted transport and access controls.

## 10. Changes

When we update this policy we change the "last updated" date on the published page, and significant changes are announced in the app. Continuing to use the app means you accept the updated policy.

## 11. Contact

E-mail: **justbegin010@gmail.com**
