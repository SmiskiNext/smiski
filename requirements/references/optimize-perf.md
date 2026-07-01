# LiveKit — Tối ưu hiệu năng streaming

Tài liệu tham chiếu nội bộ về tối ưu hiệu năng streaming LiveKit cho dự án
smiski (`meeting-management`: video + gRPC + SSE). Mục tiêu: giảm
**CPU / RAM / bandwidth / latency** ở cả client và server.

Xem thêm `livekit.md` cho permission, token, tunnel, custom recording.

## Nguyên tắc nền tảng

LiveKit là **SFU** (Selective Forwarding Unit) — chỉ forward packet, **không
transcode** mặc định. Hệ quả:

- Chi phí **SFU** = hàm của (số track × số subscriber × packet rate) — chủ yếu
  là packet processing + network I/O, không phải video codec.
- Chi phí **encode / decode** nằm ở **client**.
- **Backend không proxy media** — chỉ lo token / room / webhook.

Kim chỉ nam: phân bổ tối ưu đúng tầng, đừng đặt gánh nặng sai chỗ.

## Mục lục

- [1. Web client (React / Next.js)](#1-web-client-react--nextjs)
- [2. Backend (Java / Spring)](#2-backend-java--spring)
- [3. LiveKit server / SFU](#3-livekit-server--sfu)
- [4. Tóm tắt ưu tiên cho smiski](#4-tóm-tắt-ưu-tiên-cho-smiski)
- [Nguồn](#nguồn)

---

## 1. Web client (React / Next.js)

### 1.1. Ba công tắc cốt lõi trong RoomOptions

| Option | Tác dụng | CPU | RAM | Bandwidth | Latency |
| --- | --- | --- | --- | --- | --- |
| `adaptiveStream` | Tự chỉnh quality subscribe theo kích thước/visibility của video element | ↓ | ↓ | ↓↓↓ | → |
| `dynacast` | Tạm dừng simulcast layer không ai subscribe (phía publisher) | ↓↓ | → | ↓↓ | → |
| `simulcast` | Publish nhiều layer chất lượng để subscriber chọn | ↑↑ | → | ↑↑ | → |

Bộ ba `adaptiveStream + dynacast + simulcast` bổ trợ nhau: publisher encode
nhiều layer, server tạm dừng layer không dùng, subscriber nhận đúng size cần.

```typescript
const room = new Room({
  adaptiveStream: true,
  dynacast: true,
  publishDefaults: {
    videoCodec: "vp8",
    videoEncoding: { maxBitrate: 1_500_000, maxFramerate: 30 },
    videoSimulcastLayers: [
      { width: 640, height: 360, encoding: { maxBitrate: 500_000, maxFramerate: 20 } },
      { width: 320, height: 180, encoding: { maxBitrate: 150_000, maxFramerate: 15 } },
    ],
    screenShareEncoding: { maxBitrate: 3_000_000, maxFramerate: 30 },
    red: true, // audio redundancy — giảm glitch, +~5% bandwidth
    dtx: true, // discontinuous transmission — tắt frame khi im lặng
  },
  stopLocalTrackOnUnpublish: true,
  disconnectOnPageLeave: true,
});
```

Lưu ý quan trọng:

- `adaptiveStream` **chỉ hoạt động** khi render qua `Track.attach()` hoặc
  component `<VideoTrack>` — không dùng được với `<video src>` thô.
- Với SVC codec (VP9/AV1), dynacast chỉ pause được toàn stream, không pause
  từng layer.
- `dynacast` ảnh hưởng **publisher** (bên gửi), `adaptiveStream` ảnh hưởng
  **subscriber** (bên nhận).

### 1.2. Codec — tradeoff CPU vs bandwidth

| Codec | CPU encode | CPU decode | Hiệu quả nén | SVC | Tương thích |
| --- | --- | --- | --- | --- | --- |
| H.264 | Cao (có HW accel) | Trung | Cơ bản | Không | Rộng nhất |
| VP8 | Cao | Trung | Cơ bản | Không | Chrome, Firefox |
| VP9 | Trung | Trung | +~30% vs H.264 | Có | Chrome, Safari 15+ |
| AV1 | Cao | Trung | +~50% vs VP9 | Có | Chrome mới |

Khuyến nghị:

- **Balanced / mặc định:** VP8 (hoặc H.264 fallback).
- **Bandwidth thấp, publisher CPU khỏe:** VP9 SVC hoặc AV1.
- SVC (VP9/AV1) cho phép instant layer switching, không chờ keyframe.

### 1.3. Subscription điều khiển thủ công

Cho webinar / large room, tắt auto-subscribe và chỉ nhận track cần thiết:

```typescript
await room.connect(url, token, { autoSubscribe: false });

room.on(RoomEvent.TrackPublished, (publication) => {
  publication.setSubscribed(true); // chỉ subscribe speaker cần thiết
});

publication.setEnabled(false); // tắt track off-screen, bật lại khi vào view
```

Tác động: CPU decode ↓↓, bandwidth ↓↓↓, RAM ↓↓.

### 1.4. Patterns React / Next.js

- **Client-only:** bọc component LiveKit bằng `"use client"`; khởi tạo `Room`
  trong `useEffect` để tránh chạy trên server → hydration mismatch.

  ```typescript
  "use client";
  const [room, setRoom] = useState<Room | null>(null);
  useEffect(() => {
    setRoom(new Room({ adaptiveStream: true, dynacast: true }));
  }, []);
  ```

- **Render hiệu quả:** `useTracks([Track.Source.Camera], { onlySubscribed: true })`
  để tránh re-render thừa.

- **Virtualize grid** (react-window) khi >25 participant → giảm mạnh
  CPU/DOM/RAM (300 tile → ~10-15 DOM node).

- **Cleanup bắt buộc** khi unmount để tránh memory leak:

  ```typescript
  useEffect(() => {
    const onSub = (track) => {
      const el = track.attach();
      containerRef.current?.appendChild(el);
    };
    room?.on(RoomEvent.TrackSubscribed, onSub);
    return () => {
      room?.off(RoomEvent.TrackSubscribed, onSub);
      room?.disconnect();
    };
  }, [room]);
  ```

### 1.5. Khuyến nghị theo scenario

| Scenario | Cấu hình chính |
| --- | --- |
| Conference (10–50) | adaptiveStream + dynacast + 1–2 simulcast layer |
| Webinar (ít speaker, nhiều viewer) | `autoSubscribe: false`, viewer chỉ subscribe speaker; speaker bật dynacast |
| Mobile / bandwidth thấp | `maxBitrate ~500k` + framerate 15, tắt simulcast, tắt video track không visible |

### 1.6. Bảng tổng hợp tác động (client)

| Knob | CPU | RAM | Bandwidth | Latency | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| adaptiveStream | ↓ | ↓ | ↓↓↓ | → | subscriber-side, essential |
| dynacast | ↓↓ | → | ↓↓ | → | publisher-side |
| simulcast | ↑↑ | → | ↑↑ | → | flexibility vs resource |
| VP9/AV1 SVC | ↓/↑ | → | ↓↓ | → | AV1 encode CPU cao |
| manual subscription | ↓↓ | ↓↓ | ↓↓ | → | cần app logic |
| virtualize grid | ↓↓↓ | ↓↓ | → | → | với 25+ participant |
| RED (audio) | → | → | ↑~5% | → | giảm glitch |
| DTX (audio) | → | → | ↓ | → | tắt silence frame |

---

## 2. Backend (Java / Spring)

Backend chỉ làm token / room / webhook — tối ưu quanh đó.

| Hạng mục | Vấn đề | Giải pháp | Impact chính |
| --- | --- | --- | --- |
| Token generation | Mỗi request ký JWT tốn CPU | Cache token ở Redis (TTL 5–10 phút), pre-compute `VideoGrant` hằng số | CPU ↓ 40–50%, latency endpoint ↓ 80% |
| Server API (Twirp) | Tạo client mới mỗi request → TCP/TLS handshake | `RoomServiceClient` singleton, tận dụng HTTP/2 keep-alive của OkHttp | network ↓ 60%, latency ↓ 20% |
| State sync | Polling `listParticipants` liên tục | Dùng **webhook** thay polling, giữ state ở Redis/DB | network ↓↓, CPU ↓ |
| Webhook handling | Xử lý sync block request thread | Verify chữ ký rồi publish Kafka, xử lý async ở consumer | latency ↓ 80%, throughput ↑ 3–5x |
| Egress orchestration | Start egress mất 3–5s | Gọi `@Async`, trả `202 Accepted`, theo dõi qua webhook | latency ↓, không block |
| Thread pool | Default Tomcat pool nhỏ | Tune `spring.task.execution.pool` + bounded queue | tránh thread starvation |

### 2.1. Token caching + pre-compute grants

```java
private static final VideoGrant PUBLISHER_GRANT =
    new VideoGrant().setCanPublish(true).setCanSubscribe(true);
private static final VideoGrant SUBSCRIBER_GRANT =
    new VideoGrant().setCanPublish(false).setCanSubscribe(true);
```

- Cache key Redis: `livekit:token:{roomId}:{identity}`, TTL 5–10 phút.
- TTL token: 24–48h (long-lived) để LiveKit tự refresh, tránh reconnect storm.

### 2.2. RoomServiceClient singleton

```java
@Bean
public RoomServiceClient roomServiceClient() {
  // OkHttp mặc định: HTTP/2, keep-alive 5 phút, connection pool
  return RoomServiceClient.create("https://livekit-server:7880", "api-key", "api-secret");
}
```

HTTP/2 multiplexing → 1 connection phục vụ N request đồng thời, bỏ được
3-way handshake + TLS re-negotiation mỗi lần gọi.

### 2.3. Webhook async qua Kafka (khớp kiến trúc smiski)

```java
@PostMapping("/webhooks/livekit-events")
public ResponseEntity<Void> handle(
    @RequestBody byte[] payload,
    @RequestHeader("Authorization") String auth) {
  WebhookEvent event = webhookReceiver.receive(new String(payload), auth);
  kafkaTemplate.send("livekit-webhook-events", event.getEvent(), event);
  return ResponseEntity.ok().build(); // trả 200 ngay, xử lý nặng ở consumer
}
```

```java
@KafkaListener(topics = "livekit-webhook-events")
public void process(LivekitWebhookEvent event) {
  switch (event.getEvent()) {
    case "participant_joined" -> handleJoined(event);
    case "track_published" -> handleTrackPublished(event);
    case "egress_updated" -> handleEgressUpdated(event);
  }
}
```

- Chỉ verify chữ ký (HMAC ~1–2ms) + publish Kafka trong handler → return 200.
- Update DB/Redis, emit SSE ở consumer thread pool (block OK).
- Nếu cần đảm bảo thứ tự event theo room → partition key Kafka = `roomId`.

### 2.4. Egress async

```java
@PostMapping("/meetings/{roomId}/start-recording")
public ResponseEntity<EgressResponse> start(@PathVariable String roomId) {
  egressService.startRecordingAsync(roomId); // @Async
  return ResponseEntity.accepted().body(new EgressResponse().setStatus("requested"));
}
```

Theo dõi trạng thái qua webhook `egress_started` / `egress_updated` /
`egress_ended`, không polling.

### 2.5. Nguyên tắc bổ sung

- Token **stateless** → scale ngang tự do; state chia sẻ chỉ ở
  Redis / Postgres / Kafka, không giữ in-memory cục bộ.
- Bounded queue (`queue-capacity`) để tránh memory explosion khi burst; reject
  503 khi đầy.
- Circuit breaker cho token service để tránh pile-up khi downstream lỗi.
- Co-locate backend + LiveKit server cùng region để giảm RTT token/API.

---

## 3. LiveKit server / SFU

### 3.1. Tuning config.yaml

| Config | Tác dụng | Impact |
| --- | --- | --- |
| `rtc.udp_port` (UDP mux, N = số core) | Giảm lock contention, thay cho `port_range` | CPU ↓ 5–10% |
| `rtc.congestion_control.enabled: true` | SFU theo dõi RTCP, adapt bitrate | latency ↓ (mặc định bật) |
| `rtc.congestion_control.allow_pause: true` | Pause track ưu tiên thấp khi nghẽn | ưu tiên speaker |
| `rtc.packet_buffer_size_video/audio` | Buffer jitter/loss | RAM ↑, latency ↑ nếu tăng |
| `rtc.batch_io` | Gộp syscall ghi packet | CPU ↓ 5–15% ở throughput cao |
| `rtc.use_ice_lite: true` | Bỏ full ICE phía server | CPU ↓ ~10% (NAT đơn giản) |
| `rtc.pli_throttle` | Giới hạn tần suất keyframe request | CPU ↓ nhưng black screen lâu hơn khi join |
| `room.empty_timeout` / `departure_timeout` | Vòng đời room in-memory | RAM vs tốc độ reuse |
| `room.playout_delay` | Buffer đồng bộ A/V | latency ↑ nhưng hết fluttering |
| `room.max_participants` | Chặn overflow | CPU ổn định |

```yaml
rtc:
  port_range_start: 50000
  port_range_end: 60000
  tcp_port: 7881
  use_external_ip: true
  congestion_control:
    enabled: true
    allow_pause: true
  batch_io:
    batch_size: 128
    max_flush_interval: 2ms
room:
  empty_timeout: 300
  max_participants: 500
```

### 3.2. Scaling multi-node

- Cần **Redis** để phân tán: node selector chọn node phù hợp.

  ```yaml
  node_selector:
    kind: regionaware
    sort_by: sysload
    sysload_limit: 0.7
  ```

- **1 room phải fit trên 1 node** (giới hạn SFU). Scale bằng multi-node +
  region-aware routing.
- `regionaware` → user tới node gần nhất, latency ↓ 30–100ms.
- Graceful shutdown: SIGTERM → node drain (không nhận room mới, giữ room cũ tới
  khi hết participant) → zero-downtime deploy.

### 3.3. Benchmark tham chiếu

GCP `c2-standard-16` (16 vCPU, compute-optimized):

| Scenario | Publishers | Subscribers | CPU |
| --- | --- | --- | --- |
| Large audio (10 speaker) | 10 | 3000 | 80% |
| Large meeting (all video) | 150 | 150 | 85% |
| Livestream (1 streamer) | 1 | 3000 | 92% |

Audio scale rất tốt (packet nhẹ); video là packet-heavy, CPU-bound.
Test bằng `lk load-test`.

### 3.4. Offload & audio

- Egress / Ingress chạy **service riêng** (transcode nặng CPU) → không đè SFU.
  RoomComposite 1080p ≈ 2–4 core; track recording ≈ 0.5–1 core.
- Audio: RED (chống mất gói, +~15% bandwidth) + DTX (tắt silence, bandwidth ↓
  30–50% khi im lặng) + Opus.

### 3.5. Host / kernel tuning (Linux + Docker)

```bash
docker run --network host livekit/livekit-server   # bỏ NAT overhead

sysctl -w net.core.rmem_max=134217728              # UDP rx buffer ~128MB
sysctl -w net.core.wmem_max=134217728              # UDP tx buffer ~128MB
sysctl -w net.core.netdev_max_backlog=100000
ulimit -n 65535                                     # nhiều kết nối đồng thời
```

Tăng UDP buffer khi >100k packet/s; `--network host` cho perf tốt hơn bridge.

### 3.6. Observability

- Expose `prometheus_port`; theo dõi `livekit_room_num_participants`,
  `livekit_room_bytes_out_per_sec`, `livekit_rtc_packet_loss_*`,
  `process_cpu_seconds_total`, `process_resident_memory_bytes`.
- Trigger scale khi CPU >80% trên nhiều node, p99 latency >100ms, hoặc packet
  loss >1%.

---

## 4. Tóm tắt ưu tiên cho smiski

1. **Web:** bật `adaptiveStream` + `dynacast`, cấu hình simulcast 2 layer,
   virtualize grid khi đông người, cleanup track khi unmount.
2. **Backend:** webhook async qua Kafka, `RoomServiceClient` singleton, cache
   token Redis, egress `@Async`, tune thread pool + bounded queue.
3. **Server:** bật congestion control + batch_io, dùng Redis cho multi-node +
   regionaware, tách Egress/Ingress, giám sát Prometheus.

Bảng ưu tiên backend (theo effort/impact):

| Ưu tiên | Tối ưu | Effort | Impact |
| --- | --- | --- | --- |
| P0 | Webhook async (Kafka) | M | CPU ↓ 40%, latency ↓ 80% |
| P0 | RoomServiceClient singleton | S | network ↓ 60%, latency ↓ 20% |
| P1 | Token caching (Redis) | S | latency endpoint ↓ 80% |
| P1 | Egress async (@Async) | S | request latency ↓ 50% |
| P1 | Thread pool tuning | XS | tránh thread starvation |
| P2 | Pre-compute grants | XS | token gen CPU ↓ 10% |

---

## Nguồn

- Subscribing to tracks — https://docs.livekit.io/home/client/tracks/subscribe/
- Publishing / advanced tracks — https://docs.livekit.io/home/client/tracks/advanced/
- WebRTC codecs guide — https://livekit.com/webrtc/codecs-guide
- React quickstart — https://docs.livekit.io/transport/sdk-platforms/react/
- LiveKit Components (React) — https://github.com/livekit/components-js
- Server APIs (Twirp) — https://docs.livekit.io/reference/server/server-apis/
- Server webhooks — https://docs.livekit.io/home/server/webhooks/
- Managing rooms — https://docs.livekit.io/home/server/managing-rooms/
- Tokens & grants — https://docs.livekit.io/frontends/reference/tokens-grants/
- Deploying LiveKit — https://docs.livekit.io/home/self-hosting/deployment/
- Distributed setup — https://docs.livekit.io/home/self-hosting/distributed/
- Benchmarks — https://docs.livekit.io/home/self-hosting/benchmark/
- config-sample.yaml — https://github.com/livekit/livekit/blob/master/config-sample.yaml
- Scaling WebRTC (blog) — https://livekit.com/blog/scaling-webrtc-with-distributed-mesh
- Spring task execution — https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
