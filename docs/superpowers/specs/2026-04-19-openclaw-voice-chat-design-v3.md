# OpenClaw Voice Chat 设计文档（右按键 PTT 方案）

**日期**: 2026-04-19  
**版本**: v3.0 (右按键 PTT + 火山实时 ASR)  
**状态**: 待实现

---

## 1. 概述

为智能眼镜添加与云端 OpenClaw 服务的语音对话功能，通过**右按键长按触发**，实现"按住说话 → 实时 ASR → 释放发送 → AI 回复 → TTS 播放"的完整语音交互流程。

### 1.1 核心需求

- **触发方式**: 右按键长按触发（复用现有 HardKeyManager）
- **音频处理**: 眼镜端 Opus 编码，通过火山 API 实时转写
- **通信方式**: 
  - 语音 → 火山 API（实时 ASR）
  - 文字 → OpenClaw Gateway（WebSocket）
- **TTS 播放**: 眼镜端本地合成播放

### 1.2 设计原则

- **独立性**: 新增独立 Service，不影响现有 RTSP/拍照/录像功能
- **复用性**: 最大化复用现有工具类和基础设施
- **低延迟**: Opus 编码 + 火山实时 ASR
- **灵活性**: 支持多种连接模式，适配不同网络环境

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        眼镜端 (Android)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   右按键      │  │ VoiceChat    │  │  AudioTrack          │  │
│  │  (HardKey)   │→ │   Service    │→ │  (TTS 播放)           │  │
│  └──────────────┘  │              │  └──────────────────────┘  │
│                    │  ┌──────────┐ │                            │
│  ┌──────────────┐  │  │ Volc    │ │                            │
│  │  麦克风采集   │  │  │ ASR     │ │                            │
│  │  (AudioRecord)│→ │  │ Client  │ │                            │
│  └──────────────┘  │  └──────────┘ │                            │
│                    │       ↓        │                            │
│                    │  ┌──────────┐  │                            │
│                    │  │ Opus     │  │                            │
│                    │  │ Encoder  │  │                            │
│                    │  └──────────┘  │                            │
│                    │  ┌──────────┐  │                            │
│                    │  │OpenClaw  │  │                            │
│                    │  │WebSocket │  │                            │
│                    │  │  Client  │  │                            │
│                    │  └──────────┘  │                            │
│                    └───────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                    ↓                        ↓
        ┌───────────────────┐    ┌───────────────────┐
        │   火山引擎 ASR     │    │   OpenClaw LLM    │
        │  (实时语音转写)   │    │   (对话引擎)      │
        └───────────────────┘    └───────────────────┘
```

### 2.2 工作流程

```
1. 用户按住右按键
       ↓
2. 开始录音（16kHz PCM）
       ↓
3. Opus 编码（16-32 Kbps）
       ↓
4. 发送到火山 ASR API（实时流式）
       ↓
5. 接收实时转写文字（增量更新）
       ↓
6. 用户释放右按键
       ↓
7. 发送完整文字到 OpenClaw Gateway
       ↓
8. 接收 OpenClaw 文字回复
       ↓
9. TTS 合成播放
       ↓
10. 播放完成，返回 IDLE 状态
```

---

## 3. 按键方案

### 3.1 右按键功能定义

| 按键 | 触发方式 | 功能 | 状态 |
|------|----------|------|------|
| **右按键** | 短按 (≤1000ms) | 开始/停止录像 | ✅ 保留 |
| **右按键** | 长按 (>1000ms) | Push-to-Talk 语音对话 | ✅ 新增 |

### 3.2 按键处理逻辑

```java
@Override
public void onHardKeyEvent(KeyEvent keyEvent) {
    // 右按键处理 (keyCode = 0)
    if (keyEvent.getKeyCode() == 0) {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            rightKeyDownTime = System.currentTimeMillis();
            // 不立即启动，等待判断长按/短按
        } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
            long pressDuration = System.currentTimeMillis() - rightKeyDownTime;
            
            if (pressDuration > 1000) {
                // 长按 → Push-to-Talk（释放时停止）
                if (isCameraFree()) {
                    VoiceChatService.stopVoiceChat();
                }
            } else if (isCameraFree()) {
                // 短按 → 录像
                startService(RecordVideoService);
            }
        }
    }
}
```

### 3.3 PTT 状态机

```
IDLE → [长按开始] → LISTENING → [实时 ASR] → TRANSCRIBING 
     → [释放按键] → SENDING → [等待回复] → WAITING_RESPONSE 
     → [收到回复] → PLAYING → [播放完成] → IDLE
```

---

## 4. 组件设计

### 4.1 VoiceChatService

**职责**: 语音对话主服务，协调音频采集、ASR 转写、网络通信、播放流程

**核心方法**:
```java
public class VoiceChatService extends Service {
    // 生命周期
    void onCreate()
    void onStartCommand()
    void onDestroy()
    
    // PTT 控制
    void startVoiceChat()      // 开始录音 + ASR
    void stopVoiceChat()       // 停止录音，发送文字到 OpenClaw
    
    // ASR 处理
    void sendAudioChunk()      // 发送音频到火山 ASR
    void onAsrResult()         // 接收 ASR 转写结果
    
    // OpenClaw 通信
    void sendTextToOpenClaw(String text)  // 发送文字
    void onOpenClawResponse()  // 接收 OpenClaw 回复
    
    // 连接管理
    void setConnectionMode(ConnectionMode mode)
    void configureConnection(String url, String token)
}
```

### 4.2 VolcAsrClient（新增）

**职责**: 与火山引擎 ASR API 建立 WebSocket 连接，实现实时语音转写

**依赖**: Ktor (项目已有)

**接口**:
```java
public class VolcAsrClient {
    // 初始化
    void init(String appId, String cluster, String token)
    
    // 连接管理
    void connect()
    void disconnect()
    
    // 音频发送
    void sendAudio(byte[] opusData)
    
    // 监听器
    void setListener(AsrListener listener)
    
    // 回调接口
    interface AsrListener {
        void onPartialResult(String text);  // 实时部分结果
        void onFinalResult(String text);    // 最终结果
        void onError(String error);         // 错误回调
    }
}
```

**火山 ASR WebSocket 协议**:

```json
// 客户端 → 服务端（认证消息）
{
  "app": {
    "appid": "your_app_id",
    "cluster": "volcano",
    "token": "your_token"
  },
  "user": {
    "uid": "user_001"
  },
  "request": {
    "reqid": "unique_request_id",
    "show_utterances": false,
    "sequence": 1
  },
  "audio": {
    "format": "opus",
    "rate": 16000,
    "language": "zh-CN",
    "bits": 16,
    "channel": 1
  }
}

// 客户端 → 服务端（音频数据）
// 直接发送 Opus 二进制数据

// 服务端 → 客户端（转写结果）
{
  "code": 0,
  "message": "Success",
  "result": {
    "text": "实时转写的文字",
    "is_final": false,  // true=最终结果，false=部分结果
    "utterance_id": 1
  }
}
```

### 4.3 OpenClawWebSocketClient（新增）

**职责**: 与云端 OpenClaw 服务建立 WebSocket 连接，发送文字消息

**依赖**: Ktor (项目已有)

**接口**:
```java
public class OpenClawWebSocketClient {
    // 连接管理
    void connect(String url, String token)
    void disconnect()
    
    // 数据发送
    void sendText(String text)
    
    // 监听器
    void setListener(MessageListener listener)
    
    interface MessageListener {
        void onMessage(String text);
        void onError(String error);
    }
}
```

### 4.4 OpusEncoder（新增）

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
- 码率：24 kbps (火山 ASR 推荐)

### 4.5 TtsPlayer（新增）

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

### 4.6 复用组件

| 组件 | 来源文件 | 用途 |
|------|----------|------|
| EventHelper | `utils/EventHelper.java` | 事件回调 |
| HardKeyManager | 系统库 | 物理按键监听 |
| WifiMgr | `utils/WifiMgr.java` | 网络状态检查 |
| GlassesLog | `utils/GlassesLog.java` | 日志 |
| SPUtils | `utils/SPUtils.java` | 配置存储 |

---

## 5. 火山引擎 ASR 配置

### 5.1 开通服务

1. 登录火山引擎控制台：https://console.volcengine.com
2. 搜索"语音识别" → 开通"实时语音识别"服务
3. 创建应用，获取以下信息：
   - **AppID**: 应用唯一标识
   - **Cluster**: 集群名称（默认 `volcano`）
   - **Token**: 访问令牌（可通过 API 获取）

### 5.2 获取 Token

```bash
# 方式一：通过 API 获取
curl -X POST "https://openspeech.bytedance.com/api/v1/token" \
  -H "Content-Type: application/json" \
  -d '{
    "appkey": "your_app_key",
    "secret": "your_secret"
  }'

# 返回
{
  "code": 0,
  "data": {
    "token": "your_token_here",
    "expire_time": "2026-05-19T00:00:00Z"
  }
}
```

### 5.3 WebSocket 地址

```
wss://openspeech.bytedance.com/api/v3/asr
```

### 5.4 音频格式要求

| 参数 | 值 | 说明 |
|------|-----|------|
| 采样率 | 16000 Hz | 必须匹配 |
| 位深 | 16 bit | PCM 标准 |
| 声道 | 1 (Mono) | 单声道 |
| 编码 | Opus | 推荐，也支持 PCM |
| 帧长 | 20-60 ms | 推荐 20ms |

### 5.5 定价（参考）

| 服务 | 价格 | 免费额度 |
|------|------|----------|
| 实时语音识别 | ¥0.02/分钟 | 每月 500 分钟 |

---

## 6. 数据流

### 6.1 完整语音对话流程

```
1. 用户按住右按键（>1000ms）
       ↓
2. HardKeyManager 触发回调
       ↓
3. VoiceChatService.startVoiceChat()
       ↓
4. 检查网络连接状态
       ↓
5. 初始化 AudioRecord (16kHz, 单声道，16bit)
       ↓
6. 启动 OpusEncoder
       ↓
7. 连接火山 ASR WebSocket
       ↓
8. 循环读取 PCM 数据 → Opus 编码 → 发送火山 ASR
       ↓
9. 接收火山 ASR 实时转写（部分结果）
       ↓
10. 用户释放右按键
       ↓
11. 停止录音，获取最终转写文字
       ↓
12. 断开火山 ASR 连接
       ↓
13. 连接 OpenClaw Gateway
       ↓
14. 发送文字到 OpenClaw
       ↓
15. 等待 OpenClaw 回复
       ↓
16. 接收文字回复
       ↓
17. TtsPlayer.speak() 合成播放
       ↓
18. 播放完成，返回 IDLE 状态
```

### 6.2 音频参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 采样率 | 16000 Hz | 火山 ASR 要求 |
| 位深 | 16 bit | PCM 标准 |
| 声道 | 1 (Mono) | 单声道 |
| 帧长 | 20 ms | 推荐值 |
| Opus 码率 | 24 Kbps | 火山推荐 |
| 原始数据率 | 320 KB/s | 16000 × 16 × 1 |
| 压缩后数据率 | 24 Kbps | 压缩比约 13:1 |

---

## 7. 文件结构

```
app/src/main/java/com/wj/glasses/
├── VoiceChatService.java          # 新增：语音对话服务
├── VolcAsrClient.java             # 新增：火山 ASR 客户端
├── OpenClawWebSocketClient.java   # 新增：OpenClaw WebSocket 客户端
├── ConnectionManager.java         # 新增：连接管理器
├── ConnectionConfig.java          # 新增：连接配置
├── codec/
│   └── OpusEncoder.java           # 新增：Opus 编码器
├── tts/
│   └── TtsPlayer.java             # 新增：TTS 播放器
└── utils/ (复用现有)
```

---

## 8. 配置管理

### 8.1 火山引擎配置

```java
// SPUtils 存储
String KEY_VOLC_APPID = "volc_app_id";
String KEY_VOLC_CLUSTER = "volc_cluster";
String KEY_VOLC_TOKEN = "volc_token";
String KEY_VOLC_WS_URL = "volc_ws_url";  // 默认：wss://openspeech.bytedance.com/api/v3/asr
```

### 8.2 OpenClaw 配置

```java
// SPUtils 存储
String KEY_CONNECTION_MODE = "connection_mode";
String KEY_LOCAL_IP = "local_ip";
String KEY_TAILSCALE_IP = "tailscale_ip";
String KEY_RELAY_URL = "relay_url";
String KEY_DEVICE_TOKEN = "device_token";
int KEY_CONNECTION_TIMEOUT = 5000;
```

### 8.3 音频配置

```java
// 默认值
int KEY_OPUS_BITRATE = 24000;           // Opus 码率 (bps)
int KEY_SAMPLE_RATE = 16000;            // 采样率 (Hz)
int KEY_MAX_RECORD_DURATION = 60;       // 最大录音时长 (秒)
int KEY_PTT_LONG_PRESS = 1000;          // 长按阈值 (ms)
```

---

## 9. 错误处理

| 场景 | 处理方式 |
|------|----------|
| Wi-Fi 未连接 | 显示提示，无法启动语音 |
| 火山 ASR 连接失败 | 重试 3 次，失败后提示 |
| OpenClaw 连接失败 | 重试 3 次，失败后提示 |
| 音频采集失败 | 检查权限，记录日志 |
| TTS 初始化失败 | 降级为文字显示（如有屏幕） |
| ASR 转写超时 | 30 秒超时，提示用户重试 |
| OpenClaw 超时 | 30 秒超时，提示用户重试 |
| Token 无效 | 提示重新配置 |

---

## 10. 依赖项

build.gradle 新增依赖：
```gradle
// Opus 编码（使用开源库）
implementation 'com.github.tyounan:opus-android:1.3.1'

// Ktor WebSocket
implementation "io.ktor:ktor-client-core:2.3.0"
implementation "io.ktor:ktor-client-websockets:2.3.0"
implementation "io.ktor:ktor-client-okhttp:2.3.0"

// JSON 解析（已有）
implementation 'com.google.code.gson:gson:2.10.1'
```

---

## 11. 测试要点

### 11.1 单元测试
- [ ] OpusEncoder 编码正确性
- [ ] VolcAsrClient 消息序列化/反序列化
- [ ] ConnectionManager 配置读写

### 11.2 集成测试
- [ ] 火山 ASR 实时转写
- [ ] OpenClaw 文字收发
- [ ] PTT 按键响应

### 11.3 UI 测试
- [ ] 长按/短按区分
- [ ] 连接状态提示
- [ ] 录音状态提示（LED/声音）

### 11.4 性能测试
- [ ] ASR 转写延迟（目标 < 500ms）
- [ ] 端到端延迟（目标 < 3 秒）
- [ ] 长时间运行稳定性

---

## 12. 验收标准

- [ ] 长按 1000ms 触发语音对话
- [ ] 短按 1000ms 内触发录像
- [ ] ASR 转写准确率 > 95%（普通话）
- [ ] ASR 实时延迟 < 500ms
- [ ] 端到端延迟 < 3 秒
- [ ] 支持最长 60 秒连续对话
- [ ] 网络断开时优雅降级提示
- [ ] 自动重连成功率 > 95%

---

## 13. 部署检查清单

### 13.1 火山引擎配置
- [ ] 开通实时语音识别服务
- [ ] 创建应用，获取 AppID
- [ ] 获取 Token（测试用/生产用）
- [ ] 测试 WebSocket 连接

### 13.2 OpenClaw 环境
- [ ] OpenClaw Gateway 已安装并运行
- [ ] Gateway Token 已获取
- [ ] 防火墙规则已配置（允许 18789 端口）

### 13.3 网络连接
- [ ] 局域网模式：确认 IP 地址
- [ ] Tailscale 模式：设备已加入同一网络
- [ ] Relay 模式：Cloudflare Worker 已部署

### 13.4 眼镜端
- [ ] APK 编译成功
- [ ] 权限已授予（麦克风、网络）
- [ ] 配置已保存（火山 + OpenClaw）

---

## 14. 实现优先级

### Phase 1: 基础功能（1-2 周）
1. ✅ VoiceChatService 框架
2. ✅ 右按键长按检测
3. ✅ AudioRecord + OpusEncoder
4. ✅ VolcAsrClient 基础连接
5. ✅ TtsPlayer 基础播放

### Phase 2: 实时 ASR（1 周）
1. ✅ 火山 WebSocket 实时音频流
2. ✅ 接收部分结果（实时显示）
3. ✅ 接收最终结果（释放按键）
4. ✅ 错误处理 + 重试机制

### Phase 3: OpenClaw 集成（1 周）
1. ✅ OpenClawWebSocketClient
2. ✅ 发送文字到 OpenClaw
3. ✅ 接收 OpenClaw 回复
4. ✅ 连接模式切换

### Phase 4: 优化与测试（1 周）
1. ✅ 性能优化（延迟、内存）
2. ✅ 稳定性测试
3. ✅ 用户体验优化（LED 提示、音效）
4. ✅ 文档完善

---

## 15. 参考资料

### 15.1 火山引擎 ASR
- **官方文档**: https://www.volcengine.com/docs/6561/79817
- **WebSocket API**: https://www.volcengine.com/docs/6561/138363
- **SDK 下载**: https://www.volcengine.com/docs/6561/138366

### 15.2 Clawket 项目
- **GitHub**: https://github.com/p697/clawket
- **文档**: `/Users/mac/.openclaw/workspace/docs/clawket-research-guide.md`

### 15.3 OpenClaw 项目
- **GitHub**: https://github.com/openclaw/openclaw
- **文档**: https://docs.openclaw.ai

### 15.4 相关技术
- **Opus 编码**: https://opus-codec.org
- **Ktor WebSocket**: https://ktor.io/docs/websockets.html
- **Android TTS**: https://developer.android.com/reference/android/speech/tts/TextToSpeech

---

## 16. 下一步计划

1. **确认设计** - 用户审核本设计文档 ✅
2. **创建实现计划** - 调用 `writing-plans` 技能
3. **开发实现** - 按优先级实现各组件
4. **测试验证** - 单元测试 + 集成测试 + 现场测试
5. **部署上线** - 编译 APK，部署到眼镜

---

**文档版本**: v3.0  
**最后更新**: 2026-04-19  
**审核状态**: 待审核  
**作者**: Assistant
