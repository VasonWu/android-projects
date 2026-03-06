# 皮皮虾助手 (pipixia-assistant) Android App

一个本地语音助手前端 APP，用于通过 WebSocket 与本地 Python 后端进行实时通信。

## 功能特点

- 语音输入（默认）和文本输入切换
- 底部弹窗式界面，类似小爱同学
- 前台服务保持 WebSocket 连接
- 流式输出显示
- 状态提示
- 过程信息状态行（显示工具调用、执行状态等）
- 支持 status 和 tool_use 消息类型

## 重要：构建方式

### ⚠️ Termux proot ubuntu 环境

**在 Termux proot ubuntu 环境下，必须使用 GitHub Action 来构建，不要在本地构建！**

原因：
- Termux proot 环境下 Android SDK 配置复杂
- Gradle 依赖下载速度慢且不稳定
- GitHub Action 提供标准的 Ubuntu 构建环境
- 构建速度更快，结果更可靠

### 构建流程

1. **提交代码并推送到 GitHub**
   ```bash
   git add .
   git commit -m "更新描述"
   git push origin main
   ```

2. **GitHub Action 自动构建**
   - 推送后 `build-pipixia.yml` workflow 自动触发
   - 在 GitHub 仓库的 Actions 页面查看构建进度

3. **下载 APK**
   - 构建完成后，在 Action 页面下载 artifacts
   - 或使用 GitHub API 下载

4. **静默安装**
   ```bash
   adb install -r app/build/outputs/apk/debug/pipixia-v2.9-20-20260306.apk
   ```

## 本地开发环境构建（仅用于非 Termux 环境）

```bash
./gradlew assembleDebug
```

## 安装

```bash
adb install app/build/outputs/apk/debug/pipixia-v2.9-20-20260306.apk
```

## 配置

WebSocket 服务器地址在 `app/src/main/java/com/pipixia/network/WebSocketClient.java` 中配置：

- 默认: `ws://127.0.0.1:8765`
- 模拟器访问主机: `ws://10.0.2.2:8765`
- 局域网设备: `ws://192.168.x.x:8765`

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

### 服务端状态/过程信息：
```json
{"type":"status","data":"..."}
```

### 服务端工具调用：
```json
{"type":"tool_use","data":{"name":"...","input":...,"display":"..."}}
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

- `INTERNET` - 网络连接
- `RECORD_AUDIO` - 语音识别
- `FOREGROUND_SERVICE` - 前台服务
- `POST_NOTIFICATIONS` - 通知

## 版本历史

| 版本 | versionCode | 日期 | 说明 |
|------|-------------|------|------|
| 2.9 | 20 | 2026-03-06 | 添加状态行区域，支持 status 和 tool_use 消息类型，优化流式输出 |
| 2.8 | 19 | 2026-03-05 | 基础版本 |
