package com.openapi.common.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 各模块自己声明的目录名。**只在同模块内使用**，`:app` 通过 {@code StorageCatalog.register}
 * 把名字翻译成用户能读懂的条目。
 *
 * 为什么集中放一份：目录名散落在各模块的 private 常量里时，改名字不会有任何编译错误，
 * 但用户看到的「解题历史 2.3MB」会突然变成 0。这里集中一份，至少 grep 得到。
 * 注意 `:common` 是第 0 层，不能反向依赖各模块，所以这里只是**字符串清单**，不含逻辑。
 */
public final class StorageDirs {

    /** 拍照解题的历史记录（索引 + 题目图 + 缩略图）。{@code :feature-photosolve}。 */
    public static final String PHOTOSOLVE_HISTORY = "photosolve_history";

    /** 对话附件暂存。{@code :feature-chat}。 */
    public static final String CHAT_ATTACHMENTS = "chat_attachments";

    /** 对话里拍照产生的原图。{@code :feature-chat}。 */
    public static final String CHAT_CAMERA = "camera";

    /** 语音合成的临时音频。{@code :feature-chat}。 */
    public static final String CHAT_TTS = "tts";

    /** 图片本地编辑导出的结果（{@code cacheDir}）。{@code :mediaview}。 */
    public static final String PHOTO_EDIT = "photo_edit";

    /** 分享用的临时文件（{@code cacheDir}）。{@code :common}。 */
    public static final String SHARE = "share";

    /** 对话数据导出（{@code cacheDir}）。{@code :app}。 */
    public static final String AGENT_EXPORT = "export";

    /** 2D 形象资源包。{@code :feature-companion}。 */
    public static final String AVATARS = "avatars";

    /** 本地音乐的自定义歌手图。 */
    public static final String CUSTOM_ARTIST_IMAGES = "custom_artist_images";

    private StorageDirs() {
    }

    /** 供测试与调试打印用。 */
    public static List<String> all() {
        List<String> list = new ArrayList<>();
        list.add(PHOTOSOLVE_HISTORY);
        list.add(CHAT_ATTACHMENTS);
        list.add(CHAT_CAMERA);
        list.add(CHAT_TTS);
        list.add(PHOTO_EDIT);
        list.add(SHARE);
        list.add(AGENT_EXPORT);
        list.add(AVATARS);
        list.add(CUSTOM_ARTIST_IMAGES);
        return list;
    }
}
