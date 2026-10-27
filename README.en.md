# ImageVStax

An Android app for stitching images together — horizontally, vertically, or in a grid layout.

[简体中文](README.md) | **English**

## Features

- **Pick images** from the file manager via system document picker
- **Share to ImageVStax** — receive images from any app via Android's share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`)
- **Three stitch modes**:
  - **Horizontal** — arrange images left to right, with optional custom height
  - **Vertical** — arrange images top to bottom, with optional custom width
  - **Grid** — arrange images in a configurable column grid
- **Image management** — reorder (move up/down), remove individual images
- **Gap control** — adjustable spacing between images (0–50 px)
- **Save** stitched result to gallery (`Pictures/ImageVStax`)
- **Share** stitched result to other apps via FileProvider

## Tech Stack

- **Language:** Kotlin 2.0.21
- **Build:** Android Gradle Plugin 8.7.3, Gradle 8.9
- **SDK:** compileSdk 35, minSdk 24, targetSdk 35
- **UI:** Material Design 3 (Material Components for Android)
- **Image processing:** Android framework `Bitmap` / `Canvas` APIs only, no third-party image libraries

## Build

Requires JDK 17 and an Android SDK with platform android-35.

```bash
./gradlew :app:assembleDebug     # debug build
./gradlew :app:assembleRelease   # release build (R8 + resource shrinking)
```

Outputs land in `app/build/outputs/apk/` under `debug/` and `release/`.

### Signing

Release signing credentials are read from `keystore.properties` in the project root:

```properties
storeFile=imagevstax-release.jks
storePassword=******
keyAlias=imagevstax
keyPassword=******
```

Both that file and `*.jks` are listed in `.gitignore`. When absent, `assembleRelease`
produces an unsigned APK.

## Project Structure

```
├── app/
│   └── src/main/
│       ├── java/io/github/sylphinford/imagevstax/
│       │   ├── MainActivity.kt         # Main UI, image picker, share receiver, About dialog
│       │   ├── StitchEngine.kt         # Core stitching logic
│       │   └── ImageListAdapter.kt     # RecyclerView adapter for image list
│       ├── res/
│       │   ├── layout/                 # XML layouts
│       │   ├── menu/                   # Toolbar menu
│       │   ├── raw/                    # License and third-party notice text
│       │   ├── values/                 # Strings, themes
│       │   ├── drawable/               # Vector drawables
│       │   ├── mipmap-anydpi-v26/      # Adaptive icon
│       │   └── xml/                    # FileProvider paths
│       └── AndroidManifest.xml
├── gradle/wrapper/                     # Gradle wrapper
├── gradlew / gradlew.bat               # Wrapper scripts
├── build.gradle                        # App module build config
├── settings.gradle
└── gradle.properties
```

## License

Copyright 2026 sylphinford

Released under the [Apache License 2.0](LICENSE).

The license text and third-party notices are bundled into the APK
(`res/raw/license.txt`, `notice.txt`) and can be viewed in the app under **⋮ → About**.

### Third-Party Notices

| Library | Version | License |
|---|---|---|
| AndroidX Core KTX | 1.13.1 | Apache 2.0 |
| AndroidX AppCompat | 1.7.0 | Apache 2.0 |
| Material Components for Android | 1.12.0 | Apache 2.0 |
| AndroidX Activity KTX | 1.9.3 | Apache 2.0 |
| AndroidX RecyclerView | 1.3.2 | Apache 2.0 |
| Kotlin Standard Library | 2.0.21 | Apache 2.0 |

See [NOTICE](NOTICE) for details.
