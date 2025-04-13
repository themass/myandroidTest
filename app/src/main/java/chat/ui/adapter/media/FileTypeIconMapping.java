package chat.ui.adapter.media;

import com.openapi.ks.chat.R;

import java.util.HashMap;
import java.util.Map;

public class FileTypeIconMapping {
    // 定义文件类型与图标的映射
    private static final Map<String, Integer> MIME_TYPE_TO_ICON = new HashMap<>();
    private static final Map<String, Integer> EXTENSION_TO_ICON = new HashMap<>();

    static {
        // 初始化 MIME 类型与图标的映射
        MIME_TYPE_TO_ICON.put("text/plain", R.drawable.src_assets_files_txt);
        MIME_TYPE_TO_ICON.put("application/pdf", R.drawable.src_assets_files_pdf);
        MIME_TYPE_TO_ICON.put("application/msword", R.drawable.src_assets_files_word);
        MIME_TYPE_TO_ICON.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", R.drawable.src_assets_files_word);
        MIME_TYPE_TO_ICON.put("application/vnd.ms-excel", R.drawable.src_assets_files_excel);
        MIME_TYPE_TO_ICON.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",R.drawable.src_assets_files_excel);
        MIME_TYPE_TO_ICON.put("application/vnd.ms-powerpoint", R.drawable.src_assets_files_ppt);
        MIME_TYPE_TO_ICON.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", R.drawable.src_assets_files_ppt);
        MIME_TYPE_TO_ICON.put("image/jpeg", R.drawable.src_assets_images_file);
        MIME_TYPE_TO_ICON.put("image/png", R.drawable.src_assets_images_file);
        MIME_TYPE_TO_ICON.put("image/gif", R.drawable.src_assets_images_file);
        MIME_TYPE_TO_ICON.put("application/zip", R.drawable.src_assets_files_other);
        MIME_TYPE_TO_ICON.put("application/x-rar-compressed", R.drawable.src_assets_files_other);
        MIME_TYPE_TO_ICON.put("application/x-tar", R.drawable.src_assets_files_other);
        MIME_TYPE_TO_ICON.put("*/*", R.drawable.src_assets_files_other);

        // 初始化文件扩展名与图标的映射
        EXTENSION_TO_ICON.put("txt", R.drawable.src_assets_files_txt);
        EXTENSION_TO_ICON.put("pdf", R.drawable.src_assets_files_pdf);
        EXTENSION_TO_ICON.put("doc", R.drawable.src_assets_files_word);
        EXTENSION_TO_ICON.put("docx", R.drawable.src_assets_files_word);
        EXTENSION_TO_ICON.put("xls",R.drawable.src_assets_files_excel);
        EXTENSION_TO_ICON.put("xlsx",R.drawable.src_assets_files_excel);
        EXTENSION_TO_ICON.put("ppt", R.drawable.src_assets_files_ppt);
        EXTENSION_TO_ICON.put("html", R.drawable.src_assets_files_url);
        EXTENSION_TO_ICON.put("pptx", R.drawable.src_assets_files_ppt);
        EXTENSION_TO_ICON.put("jpg", R.drawable.src_assets_images_file);
        EXTENSION_TO_ICON.put("jpeg", R.drawable.src_assets_images_file);
        EXTENSION_TO_ICON.put("png", R.drawable.src_assets_images_file);
        EXTENSION_TO_ICON.put("gif", R.drawable.src_assets_images_file);
        EXTENSION_TO_ICON.put("mp3", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("wav", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("mp4", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("avi", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("mov", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("zip", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("rar", R.drawable.src_assets_files_other);
        EXTENSION_TO_ICON.put("tar", R.drawable.src_assets_files_other);
    }
    public static Integer getIconByMimeType(String type) {
        return EXTENSION_TO_ICON.getOrDefault(type.toLowerCase(), MIME_TYPE_TO_ICON.getOrDefault(type, MIME_TYPE_TO_ICON.get("*/*")));
    }

}