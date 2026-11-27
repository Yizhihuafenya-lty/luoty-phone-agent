# LuoTy Phone Agent

手机上的原生 AI 编程 Agent：Kotlin + Jetpack Compose，直接在 Android 上跑多会话、工具调用、
文件编辑、命令执行、网页预览、远程 Linux 操控，对标桌面端 DeepSeek Harness 的交互。

> 开源项目，MIT License。仓库：<https://github.com/Yizhihuafenya-lty/luoty-phone-agent>

## 功能

- **多会话 / 多模型**：会话独立运行，切换不打断；每个模型单独保存上下文长度与多模态开关。
- **Agent 循环**：流式输出、工具调用、并行会话、中止与续跑；中间步骤折叠成一行，点开才看细节。
- **工具**：读写/编辑/搜索/删除文件、执行 shell、建任务清单、反问用户、起本机 Web 服务、SSH 远程执行。
- **远程 Linux**：设置里添加主机（密码/私钥，Keystore 加密），「+」里选服务器后直接操控；
  主机指纹首次连接需手动信任，危险命令强制确认，所有命令写审计日志。
- **上下文管理**：懒加载最近 3 轮；自动压缩 + 手动压缩；系统提示词只读，动态信息走每轮注入的
  `[runtime-context]` / `[skill-catalog]`，保持前缀稳定以提高缓存命中率。
- **附件**：图片（多模态）、PDF（PDFBox 抽文本）、Word（docx）、TXT/代码、ZIP（列目录）。
- **输入栏**：单行胶囊输入框、`/` 指令、权限/模型/服务器状态、排队消息可插队编辑。
- **反馈服务端**（`server/feedback/`）：零依赖 Python + SQLite，保留 365 天、每 IP 限速、
  1 GiB 上限，只监听 127.0.0.1，通过 Cloudflare Tunnel 暴露。

## 技术栈

- Kotlin 2.2.21 / AGP 8.13.2 / Gradle 8.14.3，compileSdk 36，minSdk 26
- Jetpack Compose（BOM 2025.06.01）+ Material 3
- OkHttp 5.1.0 手写 SSE 流式客户端，兼容 OpenAI Chat Completions
- PDFBox-Android、JSch（mwiede fork）
- 会话/设置/提供方/服务器均本地持久化，原子写入 + `.bak` 备份

## 构建

```bash
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:assembleRelease      # 发布包（当前用 debug 签名）
```

APK 输出在 `app/build/outputs/apk/`。首次启动在「设置 → 模型与接口」里填接口地址、密钥、模型。

## 目录

```
app/src/main/java/com/luoty/agent/
  agent/       AgentEngine、Tools、SystemPrompt、RemoteCommandPolicy、SshManager、LocalWebServer
  data/        Models、Settings、Providers、Servers（Keystore 加密）、SessionRepository、AttachmentStore
  net/         DeepSeekClient（SSE / 工具调用 / 提示缓存统计）
  ui/          ChatScreen、SettingsScreen、AppRoot、SessionDrawer、Markdown、CodeHighlighter
server/feedback/   反馈收集服务（app.py + systemd unit + install.sh）
tools/             mock SSE 服务器、部署与测试脚本
```

## 安全

- API Key、服务器密码/私钥只存本机；私钥密码用 Android Keystore AES-GCM 加密。
- SSH 强制校验主机指纹，指纹变化直接拒绝连接。
- 远程命令策略：删根/格式化/关机等硬性禁止；只读模式只放行查看命令；危险命令逐次确认；全量审计。
- 反馈服务不记录请求正文到日志，管理接口 Bearer Token + 常量时间比较。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

覆盖：静态 Web 服务（200/404/越权 403）、远程命令策略（硬禁、只读、确认、危险命令）。

## 开源声明

本项目以 **MIT License** 开源（见 [LICENSE](LICENSE)）。你可以自由使用、修改、分发，
包括商业用途，但需保留版权声明与许可证文本。

- 仓库内**不包含**任何 API Key、SSH 密码、私钥或 Cloudflare Token。
- 第三方依赖遵循各自许可证（OkHttp/Apache-2.0、PDFBox/Apache-2.0、JSch/BSD 等）。
- 反馈服务端 `server/feedback` 同样以 MIT 授权。
- 提交 Issue / PR 前请先跑一遍单元测试，并避免在代码或日志中硬编码凭据。

## 状态

已完成：多会话、工具循环、任务/排队/目标、压缩、懒加载、附件、Web 服务、SSH 远程、反馈服务端。
规划中：设置二级菜单与去重、三步撤回（JGit）、无障碍屏幕操作、反馈上传接入 App。
