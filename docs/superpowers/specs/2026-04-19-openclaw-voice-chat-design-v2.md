# OpenClaw Voice Chat 设计文档（增强版）

**日期**: 2026-04-19  
**版本**: v2.0 (整合 Clawket 远程连接方案)  
**状态**: 待实现

---

## 1. 概述

为智能眼镜添加与云端 OpenClaw 服务的语音对话功能，通过物理按键触发，实现"采集 - 发送 - 接收 - 播放"的完整语音交互流程。

### 1.1 核心需求

- **触发方式**: 物理按键触发（复用现有 HardKeyManager）
- **音频处理**: 眼镜端 Opus 编码，云端 ASR + LLM
- **通信方式**: WebSocket over Wi-Fi（支持直连/Relay 两种模式）
- **TTS 播放**: 眼镜端本地合成播放
- **远程连接**: 支持局域网直连、Tailscale、Cloudflare Relay

### 1.2 设计原则

- **独立性**: 新增独立 Service，不影响现有 RTSP/拍照/录像功能
- **复用性**: 最大化复用现有工具类和基础设施
- **低延迟**: Opus 编码 + WebSocket 流式传输
- **灵活性**: 支持多种连接模式，适配不同网络环境

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        眼镜端 (Android)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   物理按键    │  │ VoiceChat    │  │  AudioTrack          │  │
│  │  (HardKey)   │→ │   Service    │→ │  (TTS 播放)           │  │
│  └──────────────┘  │              │  └──────────────────────┘  │
│                    │  ┌──────────┐ │                            │
│  ┌──────────────┐  │  │OpenClaw  │ │                            │
│  │  麦克风采集   │  │  │WebSocket│ │                            │
│  │  (AudioRecord)│→ │  │  Client  │ │                            │
│  └──────────────┘  │  └──────────┘ │                            │
│                    │       ↓        │                            │
│                    │  ┌──────────┐  │                            │
│                    │  │ Opus     │  │                            │
│                    │  │ Encoder  │  │                            │
│                    │  └──────────┘  │                            │
│                    └───────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓ WebSocket
┌─────────────────────────────────────────────────────────────────┐
│                    连接模式选择                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  局域网直连      │  │   Tailscale     │  │  Cloudflare     │ │
│  │  (同一 WiFi)    │  │   (跨网络)      │  │    Relay        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
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

### 2.2 连接模式对比

| 模式 | 适用场景 | 配置难度 | 延迟 | 推荐度 |
|------|----------|----------|------|--------|
| **局域网直连** | 眼镜和 OpenClaw 在同一 WiFi | ⭐ | 最低 | ⭐⭐⭐⭐⭐ |
| **Tailscale 直连** | 跨网络访问，有 Tailscale | ⭐⭐ | 低 | ⭐⭐⭐⭐ |
| **Cloudflare Relay** | 生产环境，需要自动配对 | ⭐⭐⭐ | 中 | ⭐⭐⭐ |

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
    
    // 连接管理
    void setConnectionMode(ConnectionMode mode)  // 设置连接模式
    void configureConnection(String url, String token)  // 配置连接参数
    
    // 回调处理
    void onKeyPress()          // 按键触发
    void onServerResponse()    // 接收服务端回复
    void onConnectionStateChanged()  // 连接状态变化
}
```

**状态机**:
```
IDLE → CONNECTING → LISTENING → SENDING → WAITING_RESPONSE → PLAYING → IDLE
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
    // 连接管理
    void connect(String url, String token)
    void connectWithRelay(String relayUrl, String deviceToken)
    void disconnect()
    
    // 数据发送
    void sendAudio(byte[] data)
    void sendText(String text)
    
    // 监听器
    void setListener(MessageListener listener)
    void close()
}
```

**连接模式支持**:

```java
public enum ConnectionMode {
    LOCAL_WIFI,      // 局域网直连
    TAILSCALE,       // Tailscale 直连
    CLOUDFLARE_RELAY // Cloudflare Relay
}
```

**消息协议**:
```json
// 客户端 → 服务端
{
  "type": "audio_chunk",
  "data": "<base64 opus data>",
  "timestamp": 1234567890,
  "session_id": "xxx"
}

// 服务端 → 客户端
{
  "type": "text_response",
  "content": "回复文字内容",
  "session_id": "xxx",
  "timestamp": 1234567890
}

// 控制消息
{
  "type": "connection_ack",
  "device_id": "glasses_001",
  "status": "connected"
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

### 3.5 ConnectionManager（新增）

**职责**: 管理 OpenClaw 连接配置和模式切换

**接口**:
```java
public class ConnectionManager {
    // 单例模式
    static ConnectionManager getInstance()
    
    // 配置管理
    void saveConfig(ConnectionConfig config)
    ConnectionConfig loadConfig()
    
    // 连接模式
    ConnectionMode getCurrentMode()
    void setMode(ConnectionMode mode)
    
    // 地址解析
    String resolveGatewayUrl()
}
```

**配置数据结构**:
```java
public class ConnectionConfig {
    ConnectionMode mode;
    String localIp;           // 局域网 IP
    String tailscaleIp;       // Tailscale IP
    String relayUrl;          // Relay 服务器地址
    String deviceToken;       // 设备认证 Token
    int connectionTimeout;    // 连接超时 (毫秒)
    boolean autoReconnect;    // 自动重连
}
```

### 3.6 复用组件

| 组件 | 来源文件 | 用途 |
|------|----------|------|
| EventHelper | `utils/EventHelper.java` | 事件回调 |
| HardKeyManager | 系统库 | 物理按键监听 |
| WifiMgr | `utils/WifiMgr.java` | 网络状态检查 |
| GlassesLog | `utils/GlassesLog.java` | 日志 |
| SPUtils | `utils/SPUtils.java` | 配置存储 |

---

## 4. 连接方案详解

### 4.1 方案一：局域网直连（推荐）

**适用场景**: 眼镜和运行 OpenClaw 的电脑在同一 WiFi 网络

**架构**:
```
眼镜 (Android) ←→ WiFi 路由器 ←→ OpenClaw Gateway (192.168.x.x:18789)
```

**配置步骤**:

1. **获取 OpenClaw Gateway 地址**
```bash
# 在运行 OpenClaw 的电脑上
ifconfig | grep "inet " | grep -v 127.0.0.1
# 输出：192.168.1.100
```

2. **配置 OpenClaw 允许局域网连接**
```bash
openclaw config set gateway.bind lan
openclaw gateway restart
```

3. **眼镜端配置**
```java
ConnectionConfig config = new ConnectionConfig();
config.mode = ConnectionMode.LOCAL_WIFI;
config.localIp = "192.168.1.100";
config.deviceToken = "your-gateway-token";
ConnectionManager.getInstance().saveConfig(config);
```

**优点**:
- ✅ 配置简单
- ✅ 延迟最低
- ✅ 无需额外服务

**缺点**:
- ❌ 仅限同一网络

---

### 4.2 方案二：Tailscale 直连

**适用场景**: 眼镜和 OpenClaw 不在同一网络，需要跨网络访问

**架构**:
```
眼镜 (Android) ←→ Tailscale Network ←→ OpenClaw Gateway (100.x.x.x:18789)
```

**配置步骤**:

1. **安装 Tailscale**
```bash
# 在运行 OpenClaw 的电脑上
brew install tailscale  # Mac
tailscale up
# 记录 Tailscale IP: 100.x.x.x
```

2. **眼镜安装 Tailscale**
- 从 Google Play 下载 Tailscale App
- 登录同一账号
- 连接 Tailscale 网络

3. **配置 OpenClaw**
```bash
openclaw config set gateway.bind tailnet
openclaw gateway restart
```

4. **眼镜端配置**
```java
ConnectionConfig config = new ConnectionConfig();
config.mode = ConnectionMode.TAILSCALE;
config.tailscaleIp = "100.x.x.x";
config.deviceToken = "your-gateway-token";
ConnectionManager.getInstance().saveConfig(config);
```

**优点**:
- ✅ 跨网络访问
- ✅ P2P 直连，延迟较低
- ✅ 加密传输

**缺点**:
- ❌ 需要安装 Tailscale
- ❌ 依赖第三方服务

---

### 4.3 方案三：Cloudflare Relay（生产环境）

**适用场景**: 生产环境，需要自动配对和全球访问

**架构**:
```
眼镜 (Android) ←→ Cloudflare Relay ←→ Bridge CLI ←→ OpenClaw Gateway
```

**配置步骤**:

1. **部署 Cloudflare Relay**
```bash
# 在服务器上
git clone https://github.com/p697/clawket.git
cd clawket
npm install

# 配置 Cloudflare
cp apps/relay-registry/wrangler.local.example.toml apps/relay-registry/wrangler.local.toml
# 编辑配置文件，填入 account_id 等

# 部署
npm run relay:deploy:registry
npm run relay:deploy:worker
```

2. **运行 Bridge CLI**
```bash
npm install -g @p697/clawket
clawket pair --server https://registry.yourdomain.com
# 生成二维码和配对信息
```

3. **眼镜端配置**
```java
ConnectionConfig config = new ConnectionConfig();
config.mode = ConnectionMode.CLOUDFLARE_RELAY;
config.relayUrl = "wss://relay.yourdomain.com";
config.deviceToken = "paired-device-token";
ConnectionManager.getInstance().saveConfig(config);
```

**优点**:
- ✅ 全球访问
- ✅ 自动配对
- ✅ 无需公网 IP

**缺点**:
- ❌ 配置复杂
- ❌ 依赖 Cloudflare
- ❌ 延迟相对较高

---

## 5. 数据流

### 5.1 语音对话流程

```
1. 用户按下物理按键
       ↓
2. HardKeyManager 触发回调
       ↓
3. VoiceChatService.startVoiceChat()
       ↓
4. 检查网络连接状态
       ↓
5. 根据配置选择连接模式 (LOCAL_WIFI / TAILSCALE / RELAY)
       ↓
6. 建立 WebSocket 连接
       ↓
7. 初始化 AudioRecord (16kHz, 单声道，16bit)
       ↓
8. 循环读取 PCM 数据 → OpusEncoder 编码
       ↓
9. WebSocket 发送 Opus 分块 (每 20ms 一帧)
       ↓
10. 云端处理 (ASR → LLM → 返回文字)
       ↓
11. 接收文字回复
       ↓
12. TtsPlayer.speak() 合成播放
       ↓
13. 播放完成，返回 IDLE 状态
```

### 5.2 音频参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 采样率 | 16000 Hz | 语音场景标准 |
| 位深 | 16 bit | PCM 标准 |
| 声道 | 1 (Mono) | 单声道 |
| 帧长 | 20 ms | Opus 推荐 |
| 原始数据率 | 320 KB/s | 16000 × 16 × 1 |
| Opus 码率 | 16-32 Kbps | 压缩比约 10:1 |

---

## 6. 文件结构

```
app/src/main/java/com/wj/glasses/
├── VoiceChatService.java          # 新增：语音对话服务
├── OpenClawWebSocketClient.java   # 新增：WebSocket 客户端
├── ConnectionManager.java         # 新增：连接管理器
├── ConnectionConfig.java          # 新增：连接配置
├── codec/
│   └── OpusEncoder.java           # 新增：Opus 编码器
├── tts/
│   └── TtsPlayer.java             # 新增：TTS 播放器
└── utils/ (复用现有)
```

---

## 7. 权限配置

AndroidManifest.xml 新增权限：
```xml
<!-- TTS 不需要额外权限 -->
<!-- 网络权限已有 -->
<!-- 录音权限已有 -->
<!-- 如果需要 Tailscale -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 8. 配置管理

### 8.1 配置界面（可选）

如果眼镜有屏幕或配套 App，可提供配置界面：

```java
// 配置 Activity
public class OpenClawConfigActivity extends AppCompatActivity {
    // 连接模式选择
    Spinner spinnerConnectionMode;
    
    // 地址配置
    EditText editLocalIp;
    EditText editTailscaleIp;
    EditText editRelayUrl;
    
    // Token 配置
    EditText editDeviceToken;
    
    // 保存按钮
    Button btnSave;
}
```

### 8.2 配置文件存储

```java
// SPUtils 存储
String KEY_CONNECTION_MODE = "connection_mode";
String KEY_LOCAL_IP = "local_ip";
String KEY_TAILSCALE_IP = "tailscale_ip";
String KEY_RELAY_URL = "relay_url";
String KEY_DEVICE_TOKEN = "device_token";
String KEY_OPUS_BITRATE = "opus_bitrate";
int KEY_MAX_RECORD_DURATION = 60;  // 最大录音时长 (秒)
int KEY_CONNECTION_TIMEOUT = 5000; // 连接超时 (毫秒)
```

---

## 9. 错误处理

| 场景 | 处理方式 |
|------|----------|
| Wi-Fi 未连接 | 显示提示，无法启动语音 |
| WebSocket 连接失败 | 重试 3 次，失败后提示 |
| 音频采集失败 | 检查权限，记录日志 |
| TTS 初始化失败 | 降级为文字显示（如有屏幕） |
| 服务端超时 | 30 秒超时，提示用户重试 |
| 连接模式切换失败 | 回退到上一模式，记录日志 |
| Token 无效 | 提示重新配对 |

---

## 10. 依赖项

build.gradle 新增依赖：
```gradle
// Opus 编码（使用开源库）
implementation 'com.github.tyounan:opus-android:1.3.1'

// Ktor 已有，无需添加
implementation "io.ktor:ktor-client-core:2.3.0"
implementation "io.ktor:ktor-client-websockets:2.3.0"
implementation "io.ktor:ktor-client-okhttp:2.3.0"

// 可选：Tailscale SDK（如果需要深度集成）
// implementation 'com.tailscale:tailscale-android:0.1.0'
```

---

## 11. 测试要点

### 11.1 单元测试
- [ ] OpusEncoder 编码正确性
- [ ] ConnectionManager 配置读写
- [ ] WebSocket 消息序列化/反序列化

### 11.2 集成测试
- [ ] WebSocket 消息收发
- [ ] 连接模式切换
- [ ] 自动重连机制

### 11.3 UI 测试
- [ ] 按键触发响应时间
- [ ] 连接状态提示
- [ ] 配置界面交互

### 11.4 性能测试
- [ ] 端到端延迟（目标 < 2 秒）
- [ ] 不同网络环境下的稳定性
- [ ] 长时间运行内存泄漏检测

---

## 12. 验收标准

- [ ] 按键触发后 500ms 内开始录音
- [ ] 云端返回文字后 1 秒内开始 TTS 播放
- [ ] 端到端延迟 < 3 秒
- [ ] 支持最长 60 秒连续对话
- [ ] 网络断开时优雅降级提示
- [ ] 支持 3 种连接模式切换
- [ ] 配置持久化存储
- [ ] 自动重连成功率 > 95%

---

## 13. 部署检查清单

### 13.1 开发环境
- [ ] Android Studio 已安装
- [ ] Gradle 配置完成
- [ ] Opus 库集成测试通过

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
- [ ] 配置已保存

---

## 14. 参考资料

### 14.1 Clawket 项目
- **GitHub**: https://github.com/p697/clawket
- **文档**: `/Users/mac/.openclaw/workspace/docs/clawket-research-guide.md`
- **连接模式**: 直连/Tailscale/Cloudflare Relay

### 14.2 OpenClaw 项目
- **GitHub**: https://github.com/openclaw/openclaw
- **文档**: https://docs.openclaw.ai
- **Gateway 配置**: `openclaw config` 命令

### 14.3 相关技术
- **Opus 编码**: https://opus-codec.org
- **Ktor WebSocket**: https://ktor.io/docs/websockets.html
- **Android TTS**: https://developer.android.com/reference/android/speech/tts/TextToSpeech

---

## 15. 下一步计划

1. **确认设计** - 用户审核本设计文档
2. **创建实现计划** - 调用 `writing-plans` 技能
3. **开发实现** - 按优先级实现各组件
4. **测试验证** - 单元测试 + 集成测试 + 现场测试
5. **部署上线** - 编译 APK，部署到眼镜

---

**文档版本**: v2.0  
**最后更新**: 2026-04-19  
**审核状态**: 待审核
