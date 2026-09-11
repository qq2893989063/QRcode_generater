# QR Studio

一个基于 Kotlin、Jetpack Compose 和 Material Design 3 的 Android 二维码生成器。面向 `arm64-v8a` 手机，支持文本或链接输入、纠错等级、留白、颜色、中心图像和 PNG 导出。

## 功能

- 内容：输入文本或链接，独立预览并点击生成。
- 样式：自定义 L / M / Q / H 纠错等级、0-12 模块留白、前景色和背景色。
- 中心图像：从系统图片选择器添加 PNG/JPG，自动裁切为正方形并保留白色安全区。
- 导出：以 1024 × 1024 PNG 保存到系统图库，或通过系统分享面板发送。
- 构建：APK 仅声明 `arm64-v8a` ABI。

## 构建

使用 Android Studio 打开此目录，安装 Android SDK 35 后运行：

```text
gradlew.bat assembleDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

工程默认使用 Android Gradle Plugin 8.7.3、Kotlin 2.0.21 和 Gradle 8.9 wrapper。二维码编码由 ZXing Core 完成，中心图像合成在本地 Bitmap 上进行，不上传用户内容或图片。
