# 构建与测试

## 环境

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1（仓库 Wrapper） |
| Android Gradle Plugin | 8.9.2 |
| Android SDK Platform / Build Tools | 35 / 35.0.0 |
| Node.js | 22 或更新，用于 JavaScript 测试 |

Android Studio 打开仓库根目录，安装上述 SDK，并将 Gradle JDK 设为17。首次同步需要从 Gradle、Google Maven 和 Maven Central 下载依赖。SDK 位置由 Android Studio 写入本机 `local.properties`，该文件不提交到仓库。

## 构建

macOS / Linux：

```sh
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

测试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。调试签名与发布签名不同，不能直接覆盖已安装的正式版。开发时可使用独立包名保留正式版：

```sh
./gradlew -PappId=edu.hitwh.fieldnote.dev :app:assembleDebug
```

未签名发行构建：

```sh
./gradlew :app:assembleRelease
```

输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，不能作为正式安装包直接分发。维护者在仓库外保管原签名，按以下方式签署：

```sh
export ANDROID_HOME=/path/to/android-sdk
bash tools/sign-release.sh /private/signing /output/Xixu-HITWH-0.7.6.apk
```

私有目录包含 `Xixu-news-release.p12` 和 `password.txt`。不要提交这些文件，也不要把密码放进命令行或构建日志。自行签名的构建不能覆盖官方发行签名的安装包。

## 离线测试

```sh
node --test tests/*.test.cjs
bash tools/test-java.sh
```

新闻与缓存测试另需 Maven Central 的 `org.json:json:20240303`，将下载的 JAR 放在仓库外，再传入路径：

```sh
bash tools/test-news.sh /path/to/json-20240303.jar
bash tools/test-news-cache.sh /path/to/json-20240303.jar
python3 tests/news-crawler-lifecycle.py /path/to/json-20240303.jar
python3 tools/test-news-client.py "$ANDROID_HOME/platforms/android-35/android.jar" /path/to/json-20240303.jar
```

可选的复制按钮 DOM 检查使用 jsdom 26.1.0，见 `tests/news-copy-jsdom.cjs`。Chromium 界面测试见 `tests/task-ui-dom.cjs`、`tests/news-copy-dom.cjs` 的文件头说明。它们使用模拟数据，不登录学校、不调用模型。

更新界面资源后，可运行 `python3 tools/build-preview.py` 刷新单文件 `preview.html`。预览使用示例数据，不能代替 Android 真机、通知权限、VPN 登录和后台调度验证。

GitHub Actions 仅进行离线测试和调试 APK 构建，不读取学校账号、模型密钥或发行签名。
