package com.openapi.myapp.bean.vo;

/**
 * 后端 {@code WgRegisterVo} 的客户端镜像：注册 WireGuard 公钥的结果。
 * clientAddress 是服务端分配给本设备的、整个 WireGuard 网络里唯一的隧道内地址（CIDR），
 * 建隧道时用它，而不是节点列表里的 clientAddress（那是每个节点一个固定值，多台设备会冲突）。
 */
public class WgRegisterVo {
    public String clientAddress;
}
