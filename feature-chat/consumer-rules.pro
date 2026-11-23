# Markwon 的 image 模块自带 SVG、GIF 解码器，它们引用的 androidsvg、android-gif-drawable 是可选依赖。
# 对话里不渲染 SVG/GIF，所以不引入这两个库，只让 R8 不报缺类。
-dontwarn com.caverock.androidsvg.**
-dontwarn pl.droidsonroids.gif.**
