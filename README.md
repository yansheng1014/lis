# Lis 听书

为三星 Galaxy Watch（Wear OS / One UI Watch）打造的离线 TTS 听书应用。

- **导入**：txt / epub（epub 用内置极简解析器，无需第三方库）
- **自动分章**：识别「第 X 章 / Chapter N / 序章 / 番外」等标题，无标题时按固定块切分
- **逐句朗读**：精确定位到「哪一章哪一句」，退出重进自动续读
- **系统媒体控制**：基于 Media3 `MediaSessionService`，手表系统媒体控制条、表盘播放指示器都能控制本应用（播放/暂停/上一章/下一章）
- **Wear OS 磁贴（Tile）**：Material 3 磁贴，显示当前书籍/章节/句子，带上一章、播放暂停、下一章
- **原生 Material 3 UI**：Wear Compose 1.6 + Material 3 Expressive
- **存储**：Wear 没有文件管理器，首次可走系统 SAF 导入，或用 Shizuku 一键授权「所有文件访问」后扫描 `Download`/`Documents` 目录

## 架构

```
app/src/main/java/com/lis/wear/
├── book/      # 解析：txt 编码检测 / epub 解包 / 分章 / 分句
├── data/      # 书架与进度（DataStore + JSON）
├── tts/       # 系统 TTS 封装（一次一句，精确定位）
├── playback/  # TtsBookEngine + Media3 SimpleBasePlayer + MediaSessionService
├── tile/      # Wear OS 磁贴
├── fs/        # 存储权限 + Shizuku 授权 + 文件扫描
└── ui/        # Compose 界面（书架/播放/章节/句子/设置/文件选择）
```

## 构建

```bash
./gradlew :app:assembleRelease
```

产物 `app/build/outputs/apk/release/app-release.apk`（仅 `armeabi-v7a`，对应 Galaxy Watch7）。

GitHub Actions 在每次 push 到 `main` 后自动构建并上传 APK。

## 安装到手表

1. 手表开启开发者选项 → ADB 调试 + 无线调试
2. `adb connect <手表IP:端口>`（Wear OS 4+ 需先 `adb pair` 配对）
3. `adb install app-release.apk`

## 首次使用

1. 打开 App → 设置 → 「用 Shizuku 授权」（手表需已运行 Shizuku），或「系统设置里授权」
2. 或直接在书架点「导入书籍」走系统文件选择
3. 导入后即可在磁贴、系统媒体控制条、表盘播放指示器上控制播放

## 许可证

MIT
