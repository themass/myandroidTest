package com.timeline.vpn.bean;

import com.timeline.vpn.bean.vo.HostVo;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.JsonResult;
import com.timeline.vpn.bean.vo.VpnProfile;
import com.timeline.vpn.common.util.GsonUtils;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;

import java.lang.reflect.Type;

/**
 * Created by gqli on 2016/3/10.
 */
public class DataBuilder {
    public static VpnProfile builderVpnProfile(long expire,String name,String pwd,HostVo vo){
        VpnProfile profile = new VpnProfile();
        profile.setExpire(expire);
        profile.setVpnType(Constants.vpnType);
        String ca = "-----BEGIN CERTIFICATE-----\n" +
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
        profile.setCert(ca);
        profile.setGateway(vo.gateway);
        profile.setName(vo.gateway);
        profile.setPort(vo.port);
        profile.setPassword(pwd);
        profile.setName(name);
        LogUtil.i(ca);
        LogUtil.i(vo.cert);
        return profile;
    }
    public static <T> JsonResult<T> parserVo(Class<T>clasz,String json){
        Type typeOfT = GsonUtils.type(JsonResult.class, clasz);
        JsonResult data = GsonUtils.getInstance().fromJson(json, typeOfT);
        return data;
    }
    public static <T> JsonResult<InfoListVo<T>> parserListVo(Class<T>clasz,String json){
        Type TypeItem = GsonUtils.type(InfoListVo.class, clasz);
        Type typeOfT = GsonUtils.type(JsonResult.class, TypeItem);
        JsonResult data = GsonUtils.getInstance().fromJson(json, typeOfT);
        return data;
    }
}
