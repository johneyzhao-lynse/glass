# OpenClaw Voice Chat 设计文档

**日期**: 2026-04-18  
**作者**: Claude  
**状态**: 待实现

---

## 1. 概述

为智能眼镜添加与云端 OpenClaw 服务的语音对话功能，通过物理按键触发，实现"采集 - 发送 - 接收 - 播放"的完整语音交互流程。

### 1.1 核心需求

- **触发方式**: 物理按键触发（复用现有 HardKeyManager）
- **音频处理**: 眼镜端 Opus 编码，云端 ASR + LLM
- **通信方式**: WebSocket over Wi-Fi
- **TTS 播放**: 眼镜端本地合成播放

### 1.2 设计原则

- **独立性**: 新增独立 Service，不影响现有 RTSP/拍照/录像功能
- **复用性**: 最大化复用现有工具类和基础设施
- **低延迟**: Opus 编码 + WebSocket 流式传输

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        眼镜端                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   物理按键    │  │ VoiceChat    │  │  AudioTrack          │  │
│  │  (HardKey)   │→ │   Service    │→ │  (TTS 播放)           │  │
│  └──────────────┘  │              │  └──────────────────────┘  │
│                    │  ┌──────────┐ │                            │
│  ┌──────────────┐  │  │ WebSocket│ │                            │
│  │  麦克风采集   │  │  │  Client  │ │                            │
│  │  (AudioRecord)│→ │  └──────────┘ │                            │
│  └──────────────┘  │       ↓        │                            │
│                    │  ┌──────────┐  │                            │
│                    │  │ Opus     │  │                            │
│                    │  │ Encoder  │  │                            │
│                    │  └──────────┘  │                            │
│                    └───────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓ WebSocket
┌─────────────────────────────────────────────────────────────────┐
│                        云端服务                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  WebSocket   │  │     ASR      │  │   OpenClaw LLM       │  │
│  │   Gateway    │→ │  (语音识别)   │→ │   (对话引擎)          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                              ↓                                   │
│                    ┌──────────────┐                             │
│                    │    Response  │                             │
│                    │   (文字回复)  │                             │
│                    └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 组件设计

### 3.1 VoiceChatService

**职责**: 语音对话主服务，协调音频采集、网络通信、播放流程

**核心方法**:
```java
public class VoiceChatService extends Service {
    // 生命周期
    void onCreate()
    void onStartCommand()
    void onDestroy()
    
    // 语音流程
    void startVoiceChat()      // 开始对话
    void stopVoiceChat()       // 结束对话
    void sendAudioChunk()      // 发送音频分块
    
    // 回调处理
    void onKeyPress()          // 按键触发
    void onServerResponse()    // 接收服务端回复
}
```

**状态机**:
```
IDLE → LISTENING → SENDING → WAITING_RESPONSE → PLAYING → IDLE
```

### 3.2 OpusEncoder（新增）

**职责**: 将 AudioRecord 采集的 PCM 数据编码为 Opus 格式

**接口**:
```java
public class OpusEncoder {
    void init(int sampleRate, int channels, int bitrate)
    byte[] encode(short[] pcmData)
    void release()
}
```

**参数**:
- 采样率：16kHz (语音场景足够)
- 声道：单声道
- 码率：16-32 kbps (平衡质量与带宽)

### 3.3 OpenClawWebSocketClient（新增）

**职责**: 与云端 OpenClaw 服务建立 WebSocket 连接

**依赖**: Ktor (项目已有)

**接口**:
```java
public class OpenClawWebSocketClient {
    void connect(String url)
    void sendAudio(byte[] data)
    void sendText(String text)
    void setListener(MessageListener listener)
    void close()
}
```

**消息协议**:
```json
// 客户端 → 服务端
{
  "type": "audio_chunk",
  "data": "<base64 opus data>",
  "timestamp": 1234567890
}

// 服务端 → 客户端
{
  "type": "text_response",
  "content": "回复文字内容",
  "session_id": "xxx"
}
```

### 3.4 TtsPlayer（新增）

**职责**: 本地 TTS 合成与播放

**依赖**: Android TTS Engine (`android.speech.tts.TextToSpeech`)

**接口**:
```java
public class TtsPlayer {
    void init(Context context)
    void speak(String text, OnSpeakCompleteListener listener)
    void stop()
    void release()
}
```

### 3.5 复用组件

| 组件 | 来源文件 | 用途 |
|------|----------|------|
| EventHelper | `utils/EventHelper.java` | 事件回调 |
| HardKeyManager | 系统库 | 物理按键监听 |
| WifiMgr | `utils/WifiMgr.java` | 网络状态检查 |
| GlassesLog | `utils/GlassesLog.java` | 日志 |
| SPUtils | `utils/SPUtils.java` | 配置存储 |

---

## 4. 数据流

### 4.1 语音对话流程

```
1. 用户按下物理按键
       ↓
2. HardKeyManager 触发回调
       ↓
3. VoiceChatService.startVoiceChat()
       ↓
4. 初始化 AudioRecord (16kHz, 单声道，16bit)
       ↓
5. 循环读取 PCM 数据 → OpusEncoder 编码
       ↓
6. WebSocket 发送 Opus 分块 (每 20ms 一帧)
       ↓
7. 云端处理 (ASR → LLM → 返回文字)
       ↓
8. 接收文字回复
       ↓
9. TtsPlayer.speak() 合成播放
       ↓
10. 播放完成，返回 IDLE 状态
```

### 4.2 音频参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 采样率 | 16000 Hz | 语音场景标准 |
| 位深 | 16 bit | PCM 标准 |
| 声道 | 1 (Mono) | 单声道 |
| 帧长 | 20 ms | Opus 推荐 |
| 原始数据率 | 320 KB/s | 16000 × 16 × 1 |
| Opus 码率 | 16-32 Kbps | 压缩比约 10:1 |

---

## 5. 文件结构

```
app/src/main/java/com/wj/glasses/
├── VoiceChatService.java          # 新增：语音对话服务
├── OpenClawWebSocketClient.java   # 新增：WebSocket 客户端
├── codec/
│   └── OpusEncoder.java           # 新增：Opus 编码器
├── tts/
│   └── TtsPlayer.java             # 新增：TTS 播放器
└── utils/ (复用现有)
```

---

## 6. 权限配置

AndroidManifest.xml 新增权限：
```xml
<!-- TTS 不需要额外权限 -->
<!-- 网络权限已有 -->
<!-- 录音权限已有 -->
```

---

## 7. 错误处理

| 场景 | 处理方式 |
|------|----------|
| Wi-Fi 未连接 | 显示提示，无法启动语音 |
| WebSocket 连接失败 | 重试 3 次，失败后提示 |
| 音频采集失败 | 检查权限，记录日志 |
| TTS 初始化失败 | 降级为文字显示（如有屏幕） |
| 服务端超时 | 30 秒超时，提示用户重试 |

---

## 8. 配置项

```java
// SPUtils 存储
String KEY_OPENCLAW_URL = "openclaw_url"  // 云端服务地址
String KEY_OPUS_BITRATE = "opus_bitrate"  // Opus 码率 (默认 24000)
int KEY_MAX_RECORD_DURATION = 60          // 最大录音时长 (秒)
```

---

## 9. 测试要点

1. **单元测试**: OpusEncoder 编码正确性
2. **集成测试**: WebSocket 消息收发
3. **UI 测试**: 按键触发响应时间
4. **性能测试**: 端到端延迟（目标 < 2 秒）

---

## 10. 依赖项

build.gradle 新增依赖：
```gradle
// Opus 编码（使用开源库）
implementation 'com.github.tyounan:opus-android:1.3.1'

// Ktor 已有，无需添加
```

---

## 11. 验收标准

- [ ] 按键触发后 500ms 内开始录音
- [ ] 云端返回文字后 1 秒内开始 TTS 播放
- [ ] 端到端延迟 < 3 秒
- [ ] 支持最长 60 秒连续对话
- [ ] 网络断开时优雅降级提示

---

## 12. 待确认事项

1. **OpenClaw 服务端 API 地址和协议细节** - 需确认 WebSocket 端点 URL 和消息格式
2. **TTS 引擎选择** - 使用系统 TTS 还是集成第三方（讯飞/Google）
3. **Opus 库选择** - 使用 JNI 绑定还是纯 Java 实现
4. **按键映射** - 确认使用哪个物理按键触发

---

**下一步**: 用户确认设计后，调用 `writing-plans` 技能创建实现计划
