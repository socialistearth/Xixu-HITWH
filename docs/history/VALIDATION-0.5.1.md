# 汐序 0.5.2 名称修正

本轮将应用名称恢复为“汐序”，递增版本号。重新编译并检查 APK 标签、包名及与 0.5.1 的签名一致性，不重复联网测试。原有功能验证记录如下。

# 汐序 0.5.1 验证记录（2026-09-21）

- JavaScript：57 项通过，包含删除时丢弃尚未返回的同步/登录结果、已发起应用请求的旧响应不覆盖新界面状态。
- QueryCache.java：37 项 JVM 断言通过，验证当前/全部学期、课表/考试分开清理、元信息/颜色、待确认导入、无关数据保留、输入拒绝及重复清理。
- 原有新闻协议解析：24 项断言通过；课程日期、启动去重和冷却断言通过。
- Docker 项目：32 项 Python 测试通过，含原有 HTTP 接口与新增首次配置的格式、文件权限、拒绝覆盖和异常回滚。
- Gradle assembleRelease / lintRelease 成功；0 错误，5 项旧有 WebView/文本国际化警告。
- APK 使用 0.5.0 的同一私钥签名，包名保持 edu.hitwh.fieldnote.news，versionCode 7 / versionName 0.5.1，支持覆盖更新新闻版 0.5.0。

本轮没有访问学校或付费模型，没有使用学校账号，没有真机运行或 Docker 引擎。首次配置的自动化测试使用合成值；不将静态配置验证宣称为真实容器、TLS 或后台通知到达测试。

```bash
node --test tests/*.cjs
bash tools/test-java.sh
bash tools/test-query-cache.sh /绝对路径/json-20240303.jar
bash tools/test-news.sh /绝对路径/json-20240303.jar
gradle :app:assembleRelease :app:lintRelease
```

JVM 测试所需 org.json 测试依赖：https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar 。APK 本身使用 Android 系统 org.json，不添加此运行库。

后端在 hit-daily 目录安装 requirements.txt 后运行 `python -m unittest discover -s tests -v`。
