# OpenClaw Voice Chat 第一阶段实现总结

**日期**: 2026-04-19  
**版本**: v1.0 (第一阶段完成)  
**状态**: ✅ 代码实现完成

---

## ✅ 已完成的工作

### 1. 项目结构搭建

创建了以下核心文件：

```
app/src/main/java/com/wj/glasses/
├── VoiceChatService.java          ✅ 语音对话主服务
├── OpenClawWebSocketClient.java   ✅ OpenClaw WebSocket 客户端
├── ConnectionManager.java         ✅ 连接管理器
├── ConnectionConfig.java          ✅ 连接配置
├── OpenClawConfigActivity.java    ✅ 配置 Activity
├── codec/
│   └── OpusEncoder.java           ✅ Opus 编码器（简化版）
└── tts/
    └── TtsPlayer.java             ✅ TTS 播放器
```

### 2. 核心功能实现

#### 2.1 ConnectionConfig.java
- ✅ 定义连接配置数据结构
- ✅ 支持 3 种连接模式（LOCAL_WIFI、TAILSCALE、CLOUDFLARE_RELAY）
- ✅ WebSocket URL 自动生成

#### 2.2 ConnectionManager.java
- ✅ 单例模式管理连接配置
- ✅ 配置读写（使用 SPUtils）
- ✅ 局域网 IP、端口、Token 配置

#### 2.3 OpusEncoder.java
- ✅ PCM 转 Opus 编码框架
- ✅ 参数配置：16kHz, 单声道，24kbps
- ⚠️ 注意：第一阶段使用简化实现（PCM 直出），后续集成 opus-android 库

#### 2.4 OpenClawWebSocketClient.java
- ✅ WebSocket 连接框架
- ✅ 发送/接收文字消息接口
- ⚠️ 注意：需要使用 Ktor WebSocket 实现实际连接逻辑

#### 2.5 TtsPlayer.java
- ✅ Android TTS 初始化
- ✅ 文字转语音播放
- ✅ 播放完成回调

#### 2.6 VoiceChatService.java
- ✅ Service 生命周期管理
- ✅ 音频采集（AudioRecord）
- ✅ 状态机管理（IDLE → LISTENING → SENDING → WAITING_RESPONSE → PLAYING → IDLE）
- ✅ OpenClaw 连接和消息收发
- ✅ TTS 播放

### 3. 集成现有代码

#### 修改 GlassesServerService.java
- ✅ 添加 VoiceChatService 引用
- ✅ 右按键长按检测逻辑（>1 秒触发语音对话）
- ✅ 右按键短按逻辑保持不变（≤1 秒触发录像）

#### 修改 build.gradle
- ✅ 添加 Ktor WebSocket 依赖
- ✅ 添加 Gson JSON 解析库

#### 修改 AndroidManifest.xml
- ✅ 添加 VoiceChatService 声明
- ✅ 添加录音权限（已有）

### 4. 配置界面

#### OpenClawConfigActivity.java
- ✅ 配置界面 Activity
- ✅ IP 地址、端口、Token 输入
- ✅ 配置保存和验证
- ⚠️ 需要创建对应的布局文件

---

## ⚠️ 待完成的工作

### P0 - 必须完成

1. **创建布局文件**
   - `res/layout/activity_openclaw_config.xml` - 配置界面布局

2. **完善 OpenClawWebSocketClient**
   - 实现 Ktor WebSocket 实际连接逻辑
   - 处理认证 Token
   - 实现消息接收循环

3. **VoiceChatService 启动逻辑**
   - 在 GlassesServerService 中初始化 VoiceChatService
   - 处理长按开始时触发 `startVoiceChat()`

### P1 - 重要

4. **测试和调试**
   - 编译 APK
   - 测试右按键长按/短按区分
   - 测试 OpenClaw 连接
   - 测试 TTS 播放

5. **配置获取 Gateway Token**
   - 文档说明如何从 OpenClaw 获取 Token
   - 提供配置指导

### P2 - 后续优化

6. **火山 ASR 集成**
   - VolcAsrClient.java
   - 实时音频流发送
   - 实时转写文字接收

7. **用户体验优化**
   - LED 状态提示
   - 音效提示
   - 配置界面美化

---

## 📋 使用说明

### 1. 配置 OpenClaw

1. 在运行 OpenClaw 的电脑上获取 IP 地址：
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```

2. 获取 Gateway Token：
   ```bash
   cat ~/.openclaw/openclaw.json | grep token
   ```

3. 配置 OpenClaw 允许局域网连接：
   ```bash
   openclaw config set gateway.bind lan
   openclaw gateway restart
   ```

### 2. 编译和部署

```bash
cd /Users/mac/Downloads/GlassesServer/GlassesServer
./gradlew assembleDebug
```

APK 输出位置：`app/apk/GlassesServer.apk`

### 3. 测试流程

1. 安装 APK 到眼镜
2. 打开配置界面，输入 OpenClaw IP 和 Token
3. 保存配置
4. 长按右按键（>1 秒）
5. 释放按键，发送测试文字到 OpenClaw
6. 等待 OpenClaw 回复
7. TTS 播放回复

---

## 🔧 技术细节

### 右按键逻辑

```java
// 右按键处理
if (keyEvent.getKeyCode() == 0) {
    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
        rightKeyDownTime = System.currentTimeMillis();
    } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
        long pressDuration = System.currentTimeMillis() - rightKeyDownTime;
        
        if (pressDuration > 1000) {
            // 长按 → Push-to-Talk
            voiceChatService.stopVoiceChat();
        } else {
            // 短按 → 录像
            startService(RecordVideoService);
        }
    }
}
```

### 状态机

```
IDLE 
  ↓ [长按开始]
LISTENING (录音中)
  ↓ [释放按键]
SENDING (发送文字到 OpenClaw)
  ↓ [发送完成]
WAITING_RESPONSE (等待回复)
  ↓ [收到回复]
PLAYING (TTS 播放)
  ↓ [播放完成]
IDLE
```

---

## 📝 注意事项

1. **Opus 编码**: 第一阶段使用简化实现（PCM 直出），数据量较大，建议尽快集成 opus-android 库

2. **WebSocket 实现**: OpenClawWebSocketClient 需要使用 Ktor WebSocket 完整实现，当前是框架代码

3. **权限**: 确保已授予麦克风和网络权限

4. **网络**: 确保眼镜和 OpenClaw 在同一局域网

5. **Token**: Gateway Token 需要从 OpenClaw 配置文件中获取

---

## 🚀 下一步计划

1. **创建布局文件** - activity_openclaw_config.xml
2. **完善 WebSocket** - 实现 Ktor 实际连接
3. **启动逻辑** - 在 GlassesServerService 中初始化 VoiceChatService
4. **编译测试** - 编译 APK 并部署测试
5. **火山 ASR** - 第二阶段集成实时语音转写

---

**创建时间**: 2026-04-19 19:15  
**作者**: Assistant  
**状态**: 第一阶段代码实现完成，待测试验证
