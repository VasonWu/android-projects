# Claude UI - Android 语音助手

一个本地语音助手 Android 应用，通过 WebSocket 与 claude-cli 后端服务进行实时通信。

## 功能特性

- 类似小爱同学的底部弹窗式语音助手界面
- 默认语音输入，支持随时切换为文本输入
- 使用系统 SpeechRecognizer 进行语音识别
- 实时流式显示 Claude 的响应
- Foreground Service 保持后台连接
- WebSocket 自动重连

## 配置 WebSocket 地址

在 `app/src/main/java/com/claudeui/app/network/WebSocketClient.kt` 中修改：

```kotlin
class WebSocketClient(
    private val serverUrl: String = "ws://10.0.2.2:8765"  // 修改这里
)
```

- 模拟器访问本机: `ws://10.0.2.2:8765`
- 真机访问局域网: `ws://192.168.x.x:8765`
