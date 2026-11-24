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
    static final String LOCAL_URL = "https://appassets.androidplatform.net/assets/scene/index.html";
    static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
        new Entry("local-scene", "本地 3D 场景", Kind.LOCAL, "本地集成 · Three.js + three-vrm", "离线场景与 VRM 样例模型，支持系统文件选择器导入用户 VRM。Three.js / three-vrm 使用 MIT；模型许可单独列于场景署名。", "陪伴：表情与头部；讲师：视线与站姿；全身：转动、缩放与动作。", "https://github.com/pixiv/three-vrm", LOCAL_URL),
        new Entry("talkinghead", "TalkingHead", Kind.ONLINE_SAMPLE, "在线官方示例 · 非本地集成", "项目官方在线交互示例；网页可用性不代表已集成到应用。", "观察说话头像、口型与表情控制。", "https://github.com/met4citizen/TalkingHead", "https://met4citizen.github.io/TalkingHead/"),
        new Entry("spine", "Spine Demos", Kind.ONLINE_SAMPLE, "在线官方示例 · 非本地集成", "Spine 官方演示页面，展示运行时动画示例。", "比较全身动作、待机与动作切换。", "https://esotericsoftware.com/spine-demos", "https://esotericsoftware.com/spine-demos"),
        new Entry("vrm-expressions", "VRM 表情", Kind.ONLINE_SAMPLE, "在线官方示例 · 非本地集成", "three-vrm 官方 expressions 在线示例。", "观察表情预设与面部控制。", "https://github.com/pixiv/three-vrm", "https://pixiv.github.io/three-vrm/packages/three-vrm/examples/expressions.html"),
        new Entry("vrm-humanoid", "VRM 人形动画", Kind.ONLINE_SAMPLE, "在线官方示例 · 非本地集成", "three-vrm 官方 humanoid animation 在线示例。", "比较全身骨骼动作和角色比例。", "https://github.com/pixiv/three-vrm", "https://pixiv.github.io/three-vrm/packages/three-vrm/examples/humanoidAnimation/index.html"),
        new Entry("inochi", "Inochi2D", Kind.REFERENCE, "资料，不计入运行验收", "项目主页与文档入口；此处没有可验收的官方运行 demo。", "阅读 2D 角色绑定、参数与表情工作流。", "https://github.com/Inochi2D/inochi2d", "https://inochi2d.com/"),
        new Entry("rive", "Rive 社区作品库", Kind.ONLINE_GALLERY, "在线作品库 · 非运行时集成", "打开 Rive 在线社区作品库；社区页面并非应用内 Rive Runtime。作品许可依发布者而异。", "查找可交互角色与状态动画案例。", "https://rive.app/community/", "https://rive.app/community/"),
        new Entry("live2d", "Live2D 模型查看器", Kind.ONLINE_THIRD_PARTY, "第三方在线查看器 · 非 Live2D 官方 demo", "pixi-live2d-display 社区查看器。网页打开不代表已集成 Live2D Runtime；模型与 Cubism Core 有单独许可条款。", "陪伴：观察模型展示、表情参数与口型。", "https://github.com/guansss/pixi-live2d-display", "https://guansss.github.io/live2d-viewer-web/"),
        new Entry("live2d-source", "Live2D Cubism SDK 示例源码", Kind.REFERENCE, "资料，不计入运行验收", "Live2D 官方 SDK 示例源码；本应用尚未嵌入，主应用实现仅作基线。许可见仓库 LICENSE 与 Cubism SDK 条款。", "陪伴：评估原生 SDK 接入、参数绑定和口型路径。", "https://github.com/Live2D/CubismWebSamples", "https://github.com/Live2D/CubismWebSamples"),
        new Entry("duix", "Duix Avatar", Kind.REFERENCE, "资料，不计入运行验收", "官方源码入口。尚未链接原生栈、尚未测试；不能据此判定不支持 Android。", "讲师：口型、视频生成及原生部署条件。许可见官方仓库。", "https://github.com/duixcom/Duix-Avatar", "https://github.com/duixcom/Duix-Avatar"),
        new Entry("tao", "TaoAvatar / MNN", Kind.REFERENCE, "资料，不计入运行验收", "官方 Android 示例源码。尚未链接 MNN 原生栈、尚未测试；不能据此判定不支持。", "全身：实时骨骼、动作和移动端渲染成本。许可见 MNN 仓库与模型声明。", "https://github.com/alibaba/MNN/blob/master/apps/Android/MnnTaoAvatar/README.md", "https://github.com/alibaba/MNN/blob/master/apps/Android/MnnTaoAvatar/README.md")
    ));
    private EngineCatalog() {}

    static boolean isValid(Entry e) {
        if (e == null || e.id == null || e.name == null || e.status == null || e.kind == null) return false;
        try {
            URI u = URI.create(e.url);
            if (e.kind == Kind.LOCAL) return LOCAL_URL.equals(e.url) && "https".equals(u.getScheme()) && "appassets.androidplatform.net".equals(u.getHost());
            return "https".equals(u.getScheme()) && u.getHost() != null && e.source.startsWith("https://");
        } catch (RuntimeException ex) { return false; }
    }
    static Entry find(String id) { for (Entry e : ALL) if (e.id.equals(id)) return e; return ALL.get(0); }
}
