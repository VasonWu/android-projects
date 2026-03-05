# 皮皮虾 (ClaudeUI) App 功能设计需求文档

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 2.3 | 2026-03-04 | 固定签名密钥、APK 自定义命名 |
| 2.2 | 2026-03-04 | 自动权限申请，MANAGE_EXTERNAL_STORAGE 支持 |
| 2.1 | 2026-03-04 | 修复存储权限问题 |
| 2.0 | 2026-03-04 | 重新设计音频播放架构，直接 Intent 通信 |
| 1.9 | 2026-03-04 | 修复通知栏显示问题 |
| 1.8 | 2026-03-04 | 添加音频播放功能、通知栏颜色状态 |
| 1.7 | 2026-03-04 | 用户输入前缀、调换按钮位置、App名称 |
| 1.6 | 2026-03-04 | 紧凑输入布局、Markdown显示、50行历史限制 |
| 1.5 | 2026-03-04 | 回复换行显示 |
| 1.4 | 2026-03-04 | Markdown支持、历史对话50行限制 |
| 1.3 | 2026-03-04 | 会话管理功能 |
| 1.0 | 2026-03-01 | 初始版本设计 |

---

## 一、项目目标

开发一个 Android 语音助手应用 **"皮皮虾"** (原 ClaudeUI)：

* 形态参考「小米手机小爱同学」的下方弹窗式语音助手界面
* 默认使用语音输入
* 支持随时切换为文本输入
* 通过 WebSocket 与本地 claude 后端服务进行实时通信
* 在界面关闭后，仍能在后台保持连接并继续与后端交互
* 支持本地音频播放功能，可通过外部 Intent 控制
* 支持会话管理，可创建和切换多个对话会话

该应用仅用于本地个人使用，不考虑安全与上线发布问题。

---

## 二、整体架构

### 架构目标

采用以下三层结构：

* UI 层（Activity）
* 后台通信与语音处理层（Foreground Service）
* 网络通信层（WebSocket Client）

核心设计要求：

* WebSocket 连接必须由后台 Service 管理
* UI 仅作为显示和输入界面
* UI 关闭后，Service 继续运行并保持连接
* 音频播放由独立的 Foreground Service 管理，支持外部 Intent 控制

### 主要组件

| 组件 | 说明 |
|------|------|
| MainActivity | 主界面，底部弹窗式布局 |
| ClaudeWebSocketService | WebSocket 通信前台服务 |
| AudioPlayerManager | 音频播放前台服务 |
| WebSocketClient | WebSocket 客户端实现 |
| SpeechRecognizerManager | 语音识别管理 |

---

## 三、运行环境约束

* Android 版本：目标 Android 13（API 33）
* 最低支持版本：Android 6.0（API 23）
* 不需要任何 TLS、证书、token、加密等安全策略
* 后端服务运行在本机局域环境（如 127.0.0.1 或局域网 IP）
* 默认 WebSocket 地址：`ws://127.0.0.1:8765`

---

## 四、与后端的通信协议

### 客户端发送

| 类型 | 说明 | JSON 格式 |
|------|------|-----------|
| input | 发送用户输入 | `{"type":"input","text":"..."}` |
| cancel | 取消当前操作 | `{"type":"cancel"}` |
| session_create | 创建新会话 | `{"type":"session_create"}` |
| session_select | 选择会话 | `{"type":"session_select","session_id":"..."}` |
| session_list | 列出会话 | `{"type":"session_list"}` |

### 服务端返回

| 类型 | 说明 | JSON 格式 |
|------|------|-----------|
| output | 助手输出内容 | `{"type":"output","data":"..."}` |
| error | 错误信息 | `{"type":"error","data":"..."}` |
| exit | 退出代码 | `{"type":"exit","code":0}` |
| session_created | 会话已创建 | `{"type":"session_created","session_id":"..."}` |
| session_selected | 会话已选择 | `{"type":"session_selected","session_id":"..."}` |
| session_list | 会话列表 | `{"type":"session_list","sessions":[...]}` |
| process_started | 进程已启动 | `{"type":"process_started","session_id":"..."}` |
| process_stopped | 进程已停止 | `{"type":"process_stopped","session_id":"...","reason":"..."}` |
| process_crashed | 进程已崩溃 | `{"type":"process_crashed","session_id":"..."}` |

说明：

* 所有通信通过单一 WebSocket 连接完成
* 服务端是流式输出，客户端必须支持分块实时接收并显示
* 允许根据 Android 端实现需要，对服务端做小幅兼容性修改，但必须保持该协议结构不变

---

## 五、语音输入设计（核心）

### 基本要求

* 默认进入界面即为语音输入模式
* 使用系统 SpeechRecognizer（在线识别即可）
* 不需要离线唤醒
* 不需要热词或唤醒词
* 识别语言配置为中文（zh-CN）

### 语音识别行为

* 语音识别完成后，将识别结果直接作为一条 input 消息发送给后端
* 不需要本地语义处理
* 麦克风按钮点击切换录音状态，录音时按钮显示红色

### 语音助手 Intent 支持

支持通过以下 Intent 启动并自动开始语音识别：
- `ACTION_ASSIST` - 系统助手意图
- `android.intent.action.VOICE_ASSIST` - 语音助手意图
- `ACTION_SEARCH` - 搜索意图
- `android.speech.action.WEB_SEARCH` - 语音搜索意图

### 不需要语音播报（TTS）

明确要求：

* 不要实现任何语音播报
* 所有回复仅显示为文本

---

## 六、文本输入切换设计

### 需求

必须支持：

* 在语音输入与文本输入之间快速切换
* 提供明显的麦克风按钮和文本输入框

设计要求：

* 默认显示文本输入框和麦克风按钮
* 点击麦克风按钮启动语音识别
* 点击输入框弹出软键盘进行文本输入
* 发送后清空输入框并隐藏键盘
* 界面布局：[发送] [输入框] [麦克风]

---

## 七、UI 设计要求

### 整体风格

* 界面只显示在屏幕下方
* 类似小爱同学的半透明底部弹窗
* 背景带轻微透明或毛玻璃效果
* 不要全屏遮挡
* 最大高度 400dp

### UI 内容必须包括

* **顶部操作栏**：状态文字 + 新会话按钮 (+)
* **输出区域**：一块滚动显示区域，用于显示 claude 的返回文本（流式追加）
* **底部输入区域**：紧凑输入行 [发送] [输入框] [麦克风]

### 对话显示格式

* 用户输入前缀：`🐢: `
* 助手回复前缀：`🦐: `
* 支持 Markdown 格式显示（使用 Markwon 库）
* 最多显示 50 行历史对话
* 超出 50 行时自动删除最早的行

### 明确要求

* 不需要引导页
* 不需要隐私条款页面
* 不需要设置页面
* 不需要历史记录管理界面（会话管理通过后端协议实现）

---

## 八、后台 Service 设计（关键）

必须实现两个可长期运行的后台服务：

### 1. ClaudeWebSocketService

#### 服务类型
* 使用 Foreground Service（符合 Android 13 规范）
* 显示常驻通知，用于系统允许后台运行

#### 主要职责
* 管理 WebSocket 生命周期
* 管理 claude 会话
* 接收 UI 发来的输入请求
* 向 UI 分发后端返回内容
* 维护输出缓冲区（最多 50 行）
* 通知栏显示连接状态

#### 行为要求
* UI 打开时，绑定 Service
* UI 关闭时，不关闭 Service
* Service 继续保持 WebSocket 连接
* 下次打开 UI 时，重新绑定并继续显示当前会话输出

#### 通知栏状态指示

使用不同的小图标和颜色区分连接状态：

| 状态 | 图标 | 颜色 | 说明 |
|------|------|------|------|
| IDLE / CONNECTED | `ic_dialog_info` | 🔵 蓝色 | 已连接/准备就绪 |
| CONNECTING / WAITING / SENDING | `ic_popup_sync` | 🟡 黄色 | 正在连接/等待中/发送中 |
| LISTENING / RECEIVING | `ic_btn_speak_now` | 🟢 绿色 | 正在聆听/正在接收 |
| DISCONNECTED | `ic_dialog_alert` | ⚪ 灰色 | 已断开连接 |
| ERROR | `ic_delete` | 🔴 红色 | 出错了 |

**通知标题**: "皮皮虾"

#### 状态枚举

```java
IDLE, CONNECTING, CONNECTED, LISTENING, SENDING,
WAITING, RECEIVING, DISCONNECTED, ERROR
```

### 2. AudioPlayerManager

#### 服务类型
* 使用 Foreground Service（符合 Android 13 规范）
* 显示常驻通知，带播放控制按钮
* Service 支持 exported=true，可接收外部 Intent

#### 主要职责
* 内部音频播放器 (MediaPlayer)
* 支持播放列表管理
* 通知栏控制按钮
* 按顺序播放，不循环
* 播放完成自动从列表删除

#### 通知栏
- **标题**: "皮皮虾播放器"
- **内容**: 显示当前播放文件名和剩余数量
- **按钮**: [播放/暂停] [下一首] [清空]

---

## 九、UI 与 Service 通信方式

### ClaudeWebSocketService 通信
* 使用 LiveData 方式
* UI 订阅 Service 中的输出流和状态流
* Service 内部维护当前会话输出缓冲
* 通过 Binder 进行服务绑定和方法调用

### AudioPlayerManager 通信
* 通过 Intent 直接通信（exported=true）
* 支持外部应用通过 `adb shell am startservice` 发送控制命令

---

## 十、音频播放 Intent 接口

### 添加单个文件到播放列表
```
Action: com.example.helloworld.ADD_TO_PLAYLIST
Extra: file_path (String) - 音频文件完整路径
```

### 设置播放列表
```
Action: com.example.helloworld.SET_PLAYLIST
Extra: file_path_list (ArrayList<String>) - 音频文件路径列表
```

### 播放控制
```
Action: com.example.helloworld.PLAY    - 播放或继续
Action: com.example.helloworld.PAUSE   - 暂停
Action: com.example.helloworld.NEXT    - 下一首
Action: com.example.helloworld.CLEAR   - 清空列表
```

### 音频播放 Intent 流程
```
pipixia-audio-player.py (技能脚本)
    ↓ adb shell am startservice
AudioPlayerManager (直接接收)
    ↓
MediaPlayer 播放
```

---

## 十一、WebSocket 客户端设计

要求：

* 使用稳定成熟的 WebSocket 客户端库（Java-WebSocket 1.5.4）
* 支持自动重连（简单重试即可，3秒间隔）
* 连接地址可在代码中固定配置（例如 ws://127.0.0.1:8765）
* 使用 Gson 进行 JSON 解析
* 客户端 ID 管理（使用 UUID，存储在 SharedPreferences）

---

## 十二、应用启动方式

应用启动后：

* 直接显示底部语音助手界面
* 不需要启动页
* 自动启动并绑定 ClaudeWebSocketService
* 自动申请所有必需权限

---

## 十三、权限与系统配置

### 权限列表

| 权限 | 用途 | 申请方式 |
|------|------|----------|
| `INTERNET` | WebSocket 网络连接 | 安装时授权 |
| `RECORD_AUDIO` | 语音输入 | 运行时权限申请 |
| `FOREGROUND_SERVICE` | 前台服务 | 安装时授权 |
| `POST_NOTIFICATIONS` | 显示通知 (Android 13+) | 运行时权限申请 |
| `MODIFY_AUDIO_SETTINGS` | 音频播放 | 安装时授权 |
| `READ_EXTERNAL_STORAGE` | 读取存储 (Android < 13) | 运行时权限申请 |
| `WRITE_EXTERNAL_STORAGE` | 写入存储 (Android < 13) | 运行时权限申请 |
| `MANAGE_EXTERNAL_STORAGE` | 访问所有文件 (Android 11+) | 自动打开设置页面，需手动开启 |
| `READ_MEDIA_AUDIO` | 读取音频 (Android 13+) | 运行时权限申请 |

### 自动权限申请 (v2.2+)

#### 功能概述
- App 启动时自动申请所有必需权限
- 每次 onResume 时重新检查权限状态
- 自动打开 MANAGE_EXTERNAL_STORAGE 设置页面

#### MANAGE_EXTERNAL_STORAGE 特殊说明
- 这是特殊权限，无法像普通权限那样直接申请
- App 会自动打开系统设置页面
- 需要手动在设置中开启"允许访问所有文件"
- 开启后，App 可以访问整个 /sdcard 目录

#### 权限申请流程
```
App 启动 (onCreate)
    ↓
requestAllPermissions()
    ↓
┌─────────────────────────────────────┐
│ 检查 MANAGE_EXTERNAL_STORAGE        │
│ (Android 11+)                        │
└─────────────────────────────────────┘
    ↓ 未授权
打开设置页面
    ↓
┌─────────────────────────────────────┐
│ 申请其他运行时权限                  │
│ (RECORD_AUDIO, NOTIFICATIONS 等)   │
└─────────────────────────────────────┘
    ↓
onResume() 时重新检查
```

---

## 十四、异常与状态处理

必须处理以下场景：

* WebSocket 断开
* 后端进程退出（收到 exit 消息）
* 语音识别失败
* 网络连接失败

UI 中应以简单文本状态提示反馈给用户。

---

## 十五、项目结构要求

项目必须清晰区分：

* `ui` - UI 相关
* `service` - 后台服务
* `network` - 网络通信
* `speech` - 语音识别
* `audio` - 音频播放
* `util` - 工具类
* `network/protocol` - 协议消息类

### 依赖库

| 库 | 用途 | 版本 |
|----|------|------|
| androidx.appcompat:appcompat | AndroidX 兼容库 | 1.6.1 |
| com.google.android.material:material | Material Design | 1.9.0 |
| org.java-websocket:Java-WebSocket | WebSocket 客户端 | 1.5.4 |
| com.google.code.gson:gson | JSON 解析 | 2.10.1 |
| androidx.lifecycle:lifecycle-livedata | LiveData | 2.6.1 |
| androidx.lifecycle:lifecycle-service | Service 支持 | 2.6.1 |
| io.noties.markwon:core | Markdown 渲染 | 4.6.2 |

---

## 十六、默认目录

- **音乐目录**: `/sdcard/Music/`
- **下载目录**: `/sdcard/Download/temp/`

---

## 十七、与现有开发流程的适配要求（必须明确实现）

本项目必须明确支持如下开发与部署流程，并在项目中提供对应说明文件（如 README）：

* 使用已有 Android 开发技能编写代码
* 将项目源码上传到 GitHub
* 使用 GitHub Actions 自动构建 APK（或 AAB）
* 通过 adb 安装到宿主机或实体手机

要求：

* 项目中必须包含可直接运行的 GitHub Actions workflow
* 使用官方 Android SDK 构建方式
* 不依赖本地特殊环境

---

## 十八、构建配置 (v2.3+)

### APK 命名规则
APK 输出文件名格式:
```
皮皮虾-v{versionName}-{versionCode}-{yyyyMMdd}.apk
```

示例:
```
皮皮虾-v2.3-14-20260304.apk
```

### 签名配置
- 使用固定的 debug.keystore（如果存在）
- Keystore 位置: `ClaudeUI/keystore/debug.keystore`
- Keystore 密码: `android`
- Key 别名: `androiddebugkey`
- Key 密码: `android`

### 生成固定 keystore
```bash
keytool -genkey -v \
  -keystore keystore/debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

### 说明
- 如果 keystore 文件不存在，自动使用 Android SDK 默认签名
- 使用固定 keystore 后，可以直接覆盖安装，无需卸载旧版本

---

## 十九、明确禁止项

本项目中请不要实现：

* 任何加密、TLS、证书、鉴权、登录
* 任何云端服务依赖
* 任何账号体系
* 任何唤醒词系统
* 任何语音播报（TTS）
* 任何非必要动画和复杂视觉效果
* 任何第三方统计或分析 SDK

---

## 二十、版本号

| 版本 | versionCode | versionName |
|------|-------------|-------------|
| 当前 | 14 | 2.3 |

---

## 二十一、技能模块集成

### pipixia-audio-player
- 位置: `/home/claude/her/core/skills/pipixia-audio-player/`
- 功能: 通过 Intent 控制皮皮虾 App 播放音频
- 触发词: "用皮皮虾播放"、"皮皮虾播放器"、"pipixia"

### 使用示例
```bash
# 播放单个音频
pipixia-audio-player play /sdcard/Music/song.mp3

# 设置播放列表
pipixia-audio-player playlist song1.mp3 song2.mp3

# 播放控制
pipixia-audio-player play    # 播放或继续
pipixia-audio-player pause   # 暂停
pipixia-audio-player next    # 下一首
pipixia-audio-player clear   # 清空列表
```
