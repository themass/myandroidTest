package com.timeline.vpn.api;

import com.timeline.vpn.service.utils.VpnType;

/**
 * Created by gqli on 2016/3/7.
 */
public class NetApi {
    public static String getCert(){
        return "-----BEGIN CERTIFICATE-----\n" +
                "MIIDNDCCAhygAwIBAgIIYZKnlsSZupswDQYJKoZIhvcNAQEFBQAwODELMAkGA1UE\n" +
                "BhMCQ04xETAPBgNVBAoTCHRpbWVsaW5lMRYwFAYDVQQDEw1zdHJvbmdTd2FuIENB\n" +
                "MB4XDTE2MDMwNzE1MDI1MVoXDTE5MDMwNzE1MDI1MVowODELMAkGA1UEBhMCQ04x\n" +
                "ETAPBgNVBAoTCHRpbWVsaW5lMRYwFAYDVQQDEw1zdHJvbmdTd2FuIENBMIIBIjAN\n" +
                "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn7PC6woHV7MsUR6UQ3AGdVRhIVpz\n" +
                "teQELSR4x8x2GmTwr+qc/pQM6a3ON+clXXWn1MWGlSoXtFIYYN58aKJoRVscwtvv\n" +
                "kUGlZvrkfbd8xaJNOu8+7EESspee4vNsXiiPnDWgIFxGvgnZHOi0fkEMNkLeAbp8\n" +
                "gGsU/2jMkMIMrCK2E+CewAEmUkLUBWf/7He2W7FbEU96Ti/qWS46Rg2YdWTVDERI\n" +
                "ohYOCtFIb0REl8qe2Z87Ew8Piup+GxqAV2rPDiE7B8ZtBXKWwcu43CFtGD8gLkKQ\n" +
                "Av2Z37DKyewE58TAQq4ia7G7glKHvGHamI5RBmkujS/jufiK/ENlD8c29QIDAQAB\n" +
                "o0IwQDAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQU\n" +
                "xBifVNA7HPUDlNZ4sWHed/dXrVQwDQYJKoZIhvcNAQEFBQADggEBAG61czb8yaFX\n" +
                "djiXEbVQpDvdH0XkcdvHobSl8OghP1pO4IFfX27AoYYWrjBzwKg2nT1D/TzarJMl\n" +
                "pSQZ0a/0nfNm7lqgammkMeC2wkXIeuFBOc0MC6TjmNKWWJjxzqn63XJhOQcPNlyZ\n" +
                "klkzMb68vu2X9XvwkQmlpMIHUFgbyM93TzWU9BizG/K41n2nd4cbAdIqY7lcMHvL\n" +
                "kfHrdtenGVSr74fe3DbTCsCcSXP2TC+DNi9wv0NYll9pUsjYt4NsqwV3MmubRXG3\n" +
                "d3hFsH8Awh1MXQ6yr1ehZ+t/0jqMDgAyINK7ESq/5guVH23Zw1EMVEzqTiDkHqQ/\n" +
                "FxyormgtRvU=\n" +
                "-----END CERTIFICATE-----";
    }
    public static String getGateWay(){
        return "104.236.179.81";
    }
    public static String getName(){
        return "123";
    }
    public static String getPwd(){
        return "123456";
    }
    public static String getPort(){
        return "500";
    }
    public static String getVpnType(){
        return VpnType.IKEV2_EAP.getIdentifier();
    }
    public static String getMtu(){
        return null;
    }
}
