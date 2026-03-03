# ClaudeUI Android App

一个本地语音助手前端 APP，用于通过 WebSocket 与本地 Python 后端进行实时通信。

## 功能特点

- 语音输入（默认）和文本输入切换
- 底部弹窗式界面，类似小爱同学
- 前台服务保持 WebSocket 连接
- 流式输出显示
- 状态提示

## 构建

```bash
./gradlew assembleDebug
```

## 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 配置

WebSocket 服务器地址在 `app/src/main/java/com/example/helloworld/network/WebSocketClient.java` 中配置：

- 模拟器访问主机: `ws://10.0.2.2:8080`
- 局域网设备: `ws://192.168.x.x:8080`

## 通信协议

### 客户端发送：
```json
{"type":"input","text":"..."}
```

### 客户端取消：
```json
{"type":"cancel"}
```

### 服务端输出：
```json
{"type":"output","data":"..."}
```

### 服务端错误：
```json
{"type":"error","data":"..."}
```

### 服务端退出：
```json
{"type":"exit","code":0}
```

## 权限

- `INTERNET - 网络连接
- `RECORD_AUDIO` - 语音识别
- `FOREGROUND_SERVICE` - 前台服务
- `POST_NOTIFICATIONS` - 通知
