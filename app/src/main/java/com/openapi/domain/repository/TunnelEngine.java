package com.openapi.domain.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.domain.model.TunnelProtocol;

/**
 * 一种隧道协议在端上的实现。
 * <p>
 * 抽象的目的：上层（UI、选路、自动选优）只依赖这个接口，新增协议时只需要
 * 提供一个新实现并在 {@code TunnelEngineRegistry} 注册，不必改动调用方。
 * <p>
 * 重要架构约束：Android 同一时刻只允许**一个** {@code VpnService} 持有 TUN 设备。
 * 因此多个引擎不能并行运行，切换协议必须先停旧的、再起新的；这条由
 * {@code VpnSessionRepository} 统一保证，引擎自己不做互斥。
 */
public interface TunnelEngine {

    /** 本引擎对应的协议。 */
    @NonNull
    TunnelProtocol protocol();

    /**
     * 当前环境是否可用（例如缺少原生库、系统版本不支持）。
     * 用于 UI 置灰与自动选优时跳过。
     */
    boolean isAvailable();

    /**
     * 建立隧道。
     * <p>
     * 必须在调用前完成权限申请；实现方不做 {@code VpnService.prepare()}。
     *
     * @param request 连接参数
     * @param callback 生命周期回调，实现方需保证回调在主线程
     */
    void connect(@NonNull TunnelRequest request, @NonNull Callback callback);

    /** 断开隧道。允许在未连接时调用。 */
    void disconnect();

    /** 当前是否已连接。 */
    boolean isConnected();

    /** 释放资源；进程退出或引擎被替换时调用。 */
    void release();

    /**
     * 不发任何状态回调地停掉本引擎残留的隧道（如果有）。
     * 切换协议或断开时用来清理"不是当前协议"的引擎：它们的回调早已过期，
     * 再回调会把界面状态改错（例如 IKEv2 还在断开中，界面却被 sing-box 的回调改成已断开）。
     */
    default void stopSilently() {
    }

    /** 引擎生命周期回调。 */
    interface Callback {
        void onConnecting();

        void onConnected();

        void onDisconnecting();

        void onDisconnected();

        /**
         * @param reason 面向开发者的诊断信息；面向用户的文案由上层根据 errorCode 决定
         */
        void onError(@NonNull ErrorCode errorCode, @Nullable String reason);
    }

    /** 协议无关的错误分类，让上层不必了解各协议的具体错误码。 */
    enum ErrorCode {
        /** 认证失败 */
        AUTH_FAILED,
        /** 对端证书/身份校验失败 */
        PEER_AUTH_FAILED,
        /** 域名解析失败 */
        LOOKUP_FAILED,
        /** 网络不可达 */
        UNREACHABLE,
        /** 协商/配置不被接受（如算法不匹配） */
        NO_PROPOSAL,
        /** 权限或配置问题 */
        CONFIG_INVALID,
        /** 其它 */
        GENERIC
    }
}
