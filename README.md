# ImageVStax

一个把多张图片拼在一起的 Android 应用 —— 支持横向、纵向和网格三种拼接方式。

**简体中文** | [English](README.en.md)

## 功能

- **选择图片** —— 通过系统文档选择器批量选图
- **分享到 ImageVStax** —— 在其他应用中通过系统分享面板发送图片（`ACTION_SEND` / `ACTION_SEND_MULTIPLE`）
- **三种拼接模式**：
  - **横向** —— 图片从左到右排列，可自定义输出高度
  - **纵向** —— 图片从上到下排列，可自定义输出宽度
  - **网格** —— 按可配置的列数排成网格
- **图片管理** —— 调整顺序（上移 / 下移）、移除单张图片
- **间距调节** —— 图片之间的空隙可调（0–50 px）
- **保存** 拼接结果到相册（`Pictures/ImageVStax`）
- **分享** 拼接结果到其他应用（通过 FileProvider）

## 技术栈

- **语言：** Kotlin 2.0.21
- **构建：** Android Gradle Plugin 8.7.3、Gradle 8.9
- **SDK：** compileSdk 35、minSdk 24、targetSdk 35
- **UI：** Material Design 3（Material Components for Android）
- **图像处理：** 仅使用 Android framework 的 `Bitmap` / `Canvas` API，不依赖第三方图像库

## 构建

需要 JDK 17 和带 android-35 platform 的 Android SDK。

```bash
./gradlew :app:assembleDebug     # 调试版
./gradlew :app:assembleRelease   # 正式版（R8 混淆 + 资源压缩）
```

产物位于 `app/build/outputs/apk/` 下的 `debug/` 与 `release/`。

### 签名配置

正式版签名信息从项目根目录的 `keystore.properties` 读取：

```properties
storeFile=imagevstax-release.jks
storePassword=******
keyAlias=imagevstax
keyPassword=******
```

该文件与 `*.jks` 均已列入 `.gitignore`。缺少时 `assembleRelease` 产出未签名 APK。

## 项目结构

```
├── app/
│   └── src/main/
│       ├── java/io/github/sylphinford/imagevstax/
│       │   ├── MainActivity.kt         # 主界面、选图、分享接收、关于对话框
│       │   ├── StitchEngine.kt         # 拼接核心逻辑
│       │   └── ImageListAdapter.kt     # 图片列表的 RecyclerView 适配器
│       ├── res/
│       │   ├── layout/                 # 布局 XML
│       │   ├── menu/                   # 工具栏菜单
│       │   ├── raw/                    # 许可证与第三方声明文本
│       │   ├── values/                 # 字符串、主题
│       │   ├── drawable/               # 矢量图标
│       │   ├── mipmap-anydpi-v26/      # 自适应图标
│       │   └── xml/                    # FileProvider 路径配置
│       └── AndroidManifest.xml
├── gradle/wrapper/                     # Gradle wrapper
├── gradlew / gradlew.bat               # Wrapper 脚本
├── build.gradle                        # App 模块构建配置
├── settings.gradle
└── gradle.properties
```

## 许可证

Copyright 2026 sylphinford

本项目基于 [Apache License 2.0](LICENSE) 授权。

许可证全文与第三方声明随 APK 打包（`res/raw/license.txt`、`notice.txt`），
可在应用内通过 **⋮ → 关于** 查看。

### 第三方声明

| 库 | 版本 | 许可证 |
|---|---|---|
| AndroidX Core KTX | 1.13.1 | Apache 2.0 |
| AndroidX AppCompat | 1.7.0 | Apache 2.0 |
| Material Components for Android | 1.12.0 | Apache 2.0 |
| AndroidX Activity KTX | 1.9.3 | Apache 2.0 |
| AndroidX RecyclerView | 1.3.2 | Apache 2.0 |
| Kotlin Standard Library | 2.0.21 | Apache 2.0 |

详见 [NOTICE](NOTICE)。
