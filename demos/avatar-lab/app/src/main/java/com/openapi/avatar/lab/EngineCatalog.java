package com.openapi.avatar.lab;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class EngineCatalog {
    enum Kind { LOCAL, ONLINE_SAMPLE, ONLINE_GALLERY, ONLINE_THIRD_PARTY, REFERENCE }
    static final class Entry {
        final String id, name, status, description, tryList, source, url;
        final Kind kind;
        Entry(String id, String name, Kind kind, String status, String description, String tryList, String source, String url) {
            this.id=id; this.name=name; this.kind=kind; this.status=status; this.description=description;
            this.tryList=tryList; this.source=source; this.url=url;
        }
    }
    // 所有本地页由 WebViewAssetLoader 从 APK assets 提供;官方示例已整树随包,离线打开。
    static final String LOCAL_ORIGIN = "https://appassets.androidplatform.net/assets/";
    static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
        new Entry("local-scene", "本地 3D 场景", Kind.LOCAL, "本地集成 · Three.js + three-vrm", "离线场景与 CC0 VRM 角色，支持系统文件选择器导入用户 VRM。Three.js / three-vrm 使用 MIT；模型许可单独列于场景署名。", "陪伴：表情与头部；讲师：视线与站姿；全身：转动、缩放与动作。", "https://github.com/pixiv/three-vrm", LOCAL_ORIGIN + "scene/index.html"),
        new Entry("live2d-sample", "Live2D 官方样例 Hiyori", Kind.LOCAL, "本地集成 · Cubism Core + pixi-live2d-display", "官方 Hiyori 模型、动作组与 Cubism Core 已随包离线运行。Cubism Core 为 Live2D 专有 Redistributable；模型 Free Collection 仅限学习/评估。", "陪伴：视线跟随、官方待机/点击动作、直写 ParamMouthOpenY 口型。", "https://github.com/Live2D/CubismWebSamples", LOCAL_ORIGIN + "live2d/index.html"),
        new Entry("spine-sample", "Spine 官方样例 spineboy", Kind.LOCAL, "本地集成 · spine-webgl 4.3", "官方 spineboy 素材与运行时已随包离线运行。Spine Runtime 为专有许可，仅作内部评估。", "讲课：11 组官方动作切换与 setMix 过渡混合。", "https://esotericsoftware.com/spine-demos", LOCAL_ORIGIN + "spine/index.html"),
        new Entry("vrma-sample", "VRMA 全身动画", Kind.LOCAL, "本地集成 · three-vrm-animation", "pixiv 官方 test.vrma 驱动与本地场景同一个 CC0 角色（骨骼＋表情＋视线三条轨道），离线运行。MIT。", "全身：动画文件与角色文件分离，验证 VRMA 复用路线。", "https://github.com/pixiv/three-vrm", LOCAL_ORIGIN + "vrma/index.html"),
        new Entry("talkinghead-sample", "TalkingHead 离线示例", Kind.LOCAL, "本地集成 · vendored 源码 + CC0 mpfb", "vendored TalkingHead 源码与 CC0 mpfb.glb 离线运行；口型走随包 lipsync-en 词→viseme 时间轴，不请求在线 TTS。源码 MIT。", "陪伴：blendshape 表情与镜头切换；真语音需服务端 TTS。", "https://github.com/met4citizen/TalkingHead", LOCAL_ORIGIN + "talkinghead/index.html"),
        new Entry("inochi", "Inochi2D", Kind.REFERENCE, "资料，不计入运行验收", "项目主页与文档入口；此处没有可验收的官方运行 demo。", "阅读 2D 角色绑定、参数与表情工作流。", "https://github.com/Inochi2D/inochi2d", "https://inochi2d.com/"),
        new Entry("rive", "Rive 社区作品库", Kind.ONLINE_GALLERY, "在线作品库 · 非运行时集成", "打开 Rive 在线社区作品库；社区页面并非应用内 Rive Runtime。作品许可依发布者而异。", "查找可交互角色与状态动画案例。", "https://rive.app/community/", "https://rive.app/community/"),
        new Entry("live2d-source", "Live2D Cubism SDK 示例源码", Kind.REFERENCE, "资料，不计入运行验收", "Live2D 官方 SDK 示例源码；本应用尚未嵌入，主应用实现仅作基线。许可见仓库 LICENSE 与 Cubism SDK 条款。", "陪伴：评估原生 SDK 接入、参数绑定和口型路径。", "https://github.com/Live2D/CubismWebSamples", "https://github.com/Live2D/CubismWebSamples"),
        new Entry("duix", "Duix Avatar", Kind.REFERENCE, "资料，不计入运行验收", "官方源码入口。尚未链接原生栈、尚未测试；不能据此判定不支持 Android。", "讲师：口型、视频生成及原生部署条件。许可见官方仓库。", "https://github.com/duixcom/Duix-Avatar", "https://github.com/duixcom/Duix-Avatar"),
        new Entry("tao", "TaoAvatar / MNN", Kind.REFERENCE, "资料，不计入运行验收", "官方 Android 示例源码。尚未链接 MNN 原生栈、尚未测试；不能据此判定不支持。", "全身：实时骨骼、动作和移动端渲染成本。许可见 MNN 仓库与模型声明。", "https://github.com/alibaba/MNN/blob/master/apps/Android/MnnTaoAvatar/README.md", "https://github.com/alibaba/MNN/blob/master/apps/Android/MnnTaoAvatar/README.md")
    ));
    private EngineCatalog() {}

    static boolean isValid(Entry e) {
        if (e == null || e.id == null || e.name == null || e.status == null || e.kind == null) return false;
        try {
            URI u = URI.create(e.url);
            if (e.kind == Kind.LOCAL) return e.url.startsWith(LOCAL_ORIGIN) && "https".equals(u.getScheme()) && "appassets.androidplatform.net".equals(u.getHost()) && u.getPath().endsWith("/index.html");
            return "https".equals(u.getScheme()) && u.getHost() != null && e.source.startsWith("https://");
        } catch (RuntimeException ex) { return false; }
    }
    static Entry find(String id) { for (Entry e : ALL) if (e.id.equals(id)) return e; return ALL.get(0); }
}
