# 皮皮虾 (ClaudeUI) App 设计文档

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
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

---

## 概述

**皮皮虾** (原 ClaudeUI) 是一个 Android 语音助手应用，通过 WebSocket 连接后端 claude-websocket-server，提供实时语音对话功能，并支持本地音频播放。

**包名**: `com.example.helloworld`

**主 Activity**: `.MainActivity`

---

## 功能特性

### 1. 聊天功能

#### 1.1 输入方式
- **语音输入**: 通过系统语音识别进行语音输入
- **文本输入**: 通过键盘输入文字

#### 1.2 对话显示
- 用户输入前缀: `🐢: `
- 助手回复前缀: `🦐: `
- 支持 Markdown 格式显示
- 最多显示 50 行历史对话
- 超出 50 行时自动删除最早的行

#### 1.3 界面布局
- 顶部: 状态文字 + 新会话按钮 (+)
- 中部: 对话输出区域 (ScrollView)
- 底部: 紧凑输入行 [发送] [输入框] [麦克风]

#### 1.4 会话管理
- 创建新会话
- 选择会话
- 列出会话
- 新会话创建时同时清空界面历史

---

### 2. 通知栏状态指示

使用不同的小图标和颜色区分连接状态：

| 状态 | 图标 | 颜色 | 说明 |
|------|------|------|------|
| IDLE / CONNECTED | `ic_dialog_info` | 🔵 蓝色 | 已连接/准备就绪 |
| CONNECTING / WAITING / SENDING | `ic_popup_sync` | 🟡 黄色 | 正在连接/等待中/发送中 |
| LISTENING / RECEIVING | `ic_btn_speak_now` | 🟢 绿色 | 正在聆听/正在接收 |
| DISCONNECTED | `ic_dialog_alert` | ⚪ 灰色 | 已断开连接 |
| ERROR | `ic_delete` | 🔴 红色 | 出错了 |

**通知标题**: "皮皮虾"

---

### 3. 音频播放功能

#### 3.1 功能概述
- 内部音频播放器 (MediaPlayer)
- 支持播放列表管理
- 通知栏控制按钮
- 按顺序播放，不循环
- 播放完成自动从列表删除

#### 3.2 Intent 接口

##### 添加单个文件到播放列表
```
Action: com.example.helloworld.ADD_TO_PLAYLIST
Extra: file_path (String) - 音频文件完整路径
```

##### 设置播放列表
```
Action: com.example.helloworld.SET_PLAYLIST
Extra: file_path_list (ArrayList<String>) - 音频文件路径列表
```

##### 播放控制
```
Action: com.example.helloworld.PLAY    - 播放或继续
Action: com.example.helloworld.PAUSE   - 暂停
Action: com.example.helloworld.NEXT    - 下一首
Action: com.example.helloworld.CLEAR   - 清空列表
```

#### 3.3 音频播放器通知栏
- **标题**: "皮皮虾播放器"
- **内容**: 显示当前播放文件名和剩余数量
- **按钮**: [播放/暂停] [下一首] [清空]

---

### 4. 自动权限申请 (v2.2+)

#### 4.1 功能概述
- App 启动时自动申请所有必需权限
- 每次 onResume 时重新检查权限状态
- 自动打开 MANAGE_EXTERNAL_STORAGE 设置页面

#### 4.2 权限列表

| 权限 | 用途 | 申请方式 |
|------|------|----------|
| `MANAGE_EXTERNAL_STORAGE` | 访问所有文件 (Android 11+) | 自动打开设置页面，需手动开启 |
| `RECORD_AUDIO` | 语音输入 | 运行时权限申请 |
| `POST_NOTIFICATIONS` | 显示通知 (Android 13+) | 运行时权限申请 |
| `READ_EXTERNAL_STORAGE` | 读取存储 (Android < 13) | 运行时权限申请 |
| `WRITE_EXTERNAL_STORAGE` | 写入存储 (Android < 13) | 运行时权限申请 |
| `READ_MEDIA_AUDIO` | 读取音频 (Android 13+) | 运行时权限申请 |

#### 4.3 MANAGE_EXTERNAL_STORAGE 特殊说明
- 这是特殊权限，无法像普通权限那样直接申请
- App 会自动打开系统设置页面
- 需要手动在设置中开启"允许访问所有文件"
- 开启后，App 可以访问整个 /sdcard 目录

---

## WebSocket 协议

### 连接信息
- **默认地址**: `ws://127.0.0.1:8765`

### 消息格式

#### 客户端 → 服务器

| 类型 | 说明 |
|------|------|
| `input` | 发送用户输入 |
| `cancel` | 取消当前操作 |
| `session_create` | 创建新会话 |
| `session_select` | 选择会话 |
| `session_list` | 列出会话 |

#### 服务器 → 客户端

| 类型 | 说明 |
|------|------|
| `output` | 助手输出内容 |
| `error` | 错误信息 |
| `exit` | 退出代码 |
| `session_created` | 会话已创建 |
| `session_selected` | 会话已选择 |
| `session_list` | 会话列表 |
| `process_started` | 进程已启动 |
| `process_stopped` | 进程已停止 |
| `process_crashed` | 进程已崩溃 |

---

## 服务组件

### 1. ClaudeWebSocketService
- 前台服务，保持 WebSocket 连接
- 通知栏显示连接状态

### 2. AudioPlayerManager
- 前台服务，管理音频播放
- 直接接收外部 Intent (exported=true)
- 播放列表管理
- 通知栏控制按钮
- MediaPlayer 封装

## 架构说明

### 音频播放 Intent 流程 (v2.0+)
```
pipixia-audio-player.py (技能脚本)
    ↓ adb shell am startservice
AudioPlayerManager (直接接收)
    ↓
MediaPlayer 播放
```

**变更说明**:
- v1.x: 通过 ClaudeWebSocketService 转发
- v2.0+: 直接发送给 AudioPlayerManager，简化架构

### 权限申请流程 (v2.2+)
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

## 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | WebSocket 网络连接 |
| `RECORD_AUDIO` | 语音输入 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `POST_NOTIFICATIONS` | 显示通知 |
| `MODIFY_AUDIO_SETTINGS` | 音频播放 |
| `READ_EXTERNAL_STORAGE` | 读取存储 (Android < 13) |
| `WRITE_EXTERNAL_STORAGE` | 写入存储 (Android < 13) |
| `MANAGE_EXTERNAL_STORAGE` | 访问所有文件 (Android 11+) |
| `READ_MEDIA_AUDIO` | 读取音频 (Android 13+) |

---

## 默认目录

- **音乐目录**: `/sdcard/Music/`
- **下载目录**: `/sdcard/Download/temp/`

---

## 使用示例 (皮皮虾音频播放器技能)

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

---

## 技能模块

### pipixia-audio-player
- 位置: `/home/claude/her/core/skills/pipixia-audio-player/`
- 功能: 通过 Intent 控制皮皮虾 App 播放音频
- 触发词: "用皮皮虾播放"、"皮皮虾播放器"、"pipixia"

---

## 版本号

| 版本 | versionCode | versionName |
|------|-------------|-------------|
| 当前 | 13 | 2.2 |

