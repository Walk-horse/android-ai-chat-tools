# API Agent 工具箱 (Android)

一款轻量 Android 工具 App，两大功能：

1. **AI Agent 对话** — 基于 OpenAI **Responses API** 格式，兼容任意 OpenAI 兼容网关（OpenAI / DeepSeek / 硅基流动 / 本地 Ollama 网关等），流式输出。
2. **curl 模拟调用** — 粘贴 curl 命令，自动解析出 Method / URL / Headers / Body，一键发送并查看格式化响应。

## 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 2.1.20 |
| Jetpack Compose | BOM 2024.12.01 (Material 3) |
| AGP / Gradle | 8.13.0 / 8.13 |
| minSdk / targetSdk | 24 / 35 |
| HTTP | OkHttp 4.12.0 |

## 功能一：AI Agent 对话

- 右上角 ⚙ 配置：**Base URL** / **API Key** / **模型** / **System Prompt**，配置持久化到本地。
- 请求格式：`POST {BaseURL}/responses`（OpenAI Responses API）
- 自动兼容 Base URL 写法：`https://x.com/v1`、`https://x.com`、`https://x.com/v1/responses` 均可。
- 流式 SSE 输出，逐字显示「思考中」过程；消息历史自动带入上下文。
- 示例模型名：`gpt-4o-mini`、`deepseek-chat`、`qwen-plus` 等。

请求体示例：

```json
{
  "model": "gpt-4o-mini",
  "instructions": "你是一个乐于助人的 AI 助手。",
  "input": [{ "role": "user", "content": "你好" }],
  "stream": true
}
```

## 功能二：curl 模拟调用

支持的 curl 参数：

| 参数 | 说明 |
|---|---|
| `-X / --request` | 指定方法（默认 GET，带 data 默认 POST） |
| `-H / --header` | 请求头（自动跳过 `HTTP/1.1 200` 响应行） |
| `-d / --data / --data-raw / --data-binary` | 请求体（多个自动 `&` 拼接） |
| `--data-urlencode` | URL 编码后拼接 |
| `-F / --form` | 表单体（提示 multipart） |
| `-u / --user` | 自动转 `Authorization: Basic` |
| `-A / --user-agent`、`-b / --cookie`、`-e / --referer` | 对应请求头 |
| `-G` | data 拼接到 URL query |
| `-s -k -v -L -i -o --max-time ...` | 忽略项 |

## 构建

```bash
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/ht/apigent/
├── MainActivity.kt          # 底部导航（AI Agent / curl 工具）
├── agent/
│   ├── ResponsesClient.kt   # OpenAI Responses API 客户端（流式 SSE）
│   └── ChatViewModel.kt     # 对话状态与配置持久化
├── curl/
│   ├── CurlParser.kt        # curl 命令解析器
│   ├── CurlExecutor.kt      # 请求发送
│   └── CurlViewModel.kt     # curl 工具状态
└── ui/
    ├── ChatScreen.kt        # 对话界面
    ├── CurlScreen.kt        # curl 工具界面
    └── theme/               # Material3 主题
```
