# LiveKit — Tài liệu tham chiếu

Tài liệu tham chiếu nội bộ về LiveKit phục vụ cho `meeting-management` (video,
gRPC, SSE) trong dự án smiski. Tổng hợp từ tài liệu chính thức LiveKit
(`docs.livekit.io`).

## Mục lục

- [1. Permission (VideoGrant)](#1-permission-videogrant)
- [2. Cấu trúc Token (JWT)](#2-cấu-trúc-token-jwt)
- [3. Tunnel & Connectivity](#3-tunnel--connectivity)
- [4. Custom Layout Recording (Egress)](#4-custom-layout-recording-egress)
- [Nguồn](#nguồn)

> Tối ưu hiệu năng streaming (client / backend / SFU): xem
> [`optimize-perf.md`](./optimize-perf.md).

---

## 1. Permission (VideoGrant)

`video` grant xác định participant được phép làm gì trong room.

| Field                  | Kiểu     | Ý nghĩa                                                                      |
| ---------------------- | -------- | ---------------------------------------------------------------------------- |
| `roomJoin`             | boolean  | Được join room                                                               |
| `room`                 | string   | Tên room (bắt buộc khi `roomJoin` hoặc `roomAdmin`)                          |
| `roomCreate`           | boolean  | Tạo/xóa room                                                                 |
| `roomList`             | boolean  | Liệt kê room khả dụng                                                        |
| `roomAdmin`            | boolean  | Điều phối (moderate) room                                                    |
| `roomRecord`           | boolean  | Dùng Egress service (recording)                                              |
| `ingressAdmin`         | boolean  | Dùng Ingress service                                                         |
| `canPublish`           | boolean  | Publish tracks                                                               |
| `canSubscribe`         | boolean  | Subscribe tracks của người khác                                              |
| `canPublishData`       | boolean  | Publish data messages                                                        |
| `canPublishSources`    | string[] | Giới hạn nguồn: `camera`, `microphone`, `screen_share`, `screen_share_audio` |
| `canUpdateOwnMetadata` | boolean  | Tự cập nhật metadata                                                         |
| `hidden`               | boolean  | Ẩn participant khỏi danh sách                                                |
| `kind`                 | string   | `standard`, `ingress`, `egress`, `sip`, `agent`, `connector`                 |
| `destinationRoom`      | string   | Room mà participant có thể được forward tới                                  |

### Ví dụ grant

Subscribe-only (audience):

```json
{
    "video": {
        "room": "myroom",
        "roomJoin": true,
        "canSubscribe": true,
        "canPublish": false,
        "canPublishData": false
    }
}
```

Camera-only (speaker):

```json
{
    "video": {
        "room": "myroom",
        "roomJoin": true,
        "canSubscribe": true,
        "canPublish": true,
        "canPublishSources": ["camera"]
    }
}
```

### Cập nhật permission lúc runtime

- Dùng `UpdateParticipant` API (yêu cầu `roomAdmin` grant).
- Các field cập nhật được: `can_subscribe`, `can_publish`, `can_publish_data`,
  `can_publish_sources`, `hidden`, `can_update_metadata`,
  `can_subscribe_metrics`.
- Client nhận event `ParticipantPermissionChanged`.
- Revoke `canPublish` → tất cả track đã publish tự động bị unpublish.
- Trên LiveKit Cloud, đổi permission → token cũ tự động bị revoke.

Ví dụ (Node.js):

```javascript
await roomService.updateParticipant(roomName, identity, undefined, {
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
});
```

---

## 2. Cấu trúc Token (JWT)

Access token là JWT được ký bằng API secret để chống giả mạo.

### Các field trong payload

| Field        | Ý nghĩa                                     |
| ------------ | ------------------------------------------- |
| `exp`        | Thời điểm hết hạn (Unix timestamp)          |
| `iss`        | Issuer — chính là API key                   |
| `sub`        | Subject — identity duy nhất của participant |
| `nbf`        | Not-before — thời điểm token bắt đầu hợp lệ |
| `video`      | Video grant (xem mục 1)                     |
| `metadata`   | Participant metadata (tùy chọn)             |
| `attributes` | Cặp key/value (tùy chọn)                    |
| `sip`        | SIP grant (tùy chọn)                        |

Ví dụ token đã decode:

```json
{
    "exp": 1621657263,
    "iss": "APIMmxiL8rquKztZEoZJV9Fb",
    "sub": "myidentity",
    "nbf": 1619065263,
    "video": { "room": "myroom", "roomJoin": true },
    "metadata": ""
}
```

### Tạo token (server SDK)

```typescript
import { AccessToken, VideoGrant } from 'livekit-server-sdk';

const at = new AccessToken('api-key', 'secret-key', {
    identity: 'participant-name',
});

const grant: VideoGrant = {
    room: 'myroom',
    roomJoin: true,
    canPublish: true,
    canSubscribe: true,
};

at.addGrant(grant);
const token = await at.toJwt();
```

### Vòng đời & Revocation

- **TTL**: chỉ ảnh hưởng kết nối ban đầu, không ảnh hưởng reconnect.
- **Refresh**: server chủ động cấp token mới cho client đã connect; token mới
  hết hạn sau 10 phút hoặc thời gian còn lại của token gốc (cái nào lâu hơn).
- **Revocation** (LiveKit Cloud): khi permission đổi hoặc participant bị xóa,
  token cũ tự bị revoke qua `revoke_token_ts` (Unix timestamp, phải nằm trong 60
  giây so với server time). `RemoveParticipant` API cũng có thể pass
  `revoke_token_ts` để ngăn reconnect bằng token cũ.

### Best practices bảo mật

- Luôn tạo token ở backend, không expose API key/secret ra client.
- Least privilege: chỉ cấp grant cần thiết.
- Đặt TTL hợp lý (thường 1–24 giờ tùy use case).
- Đồng bộ clock (revocation cutoff phụ thuộc server time).

---

## 3. Tunnel & Connectivity

> **Lưu ý**: LiveKit **không có** command `lk tunnel` chính thức để expose local
> server. "Tunnel" ở đây ám chỉ cơ chế relay/kết nối vượt firewall.

### TURN server (relay "tunnel")

LiveKit có embedded TURN server, relay media khi kết nối trực tiếp thất bại (hữu
ích với firewall doanh nghiệp chặn UDP hoặc TCP non-secure).

- **TURN/TLS**: port `5349`, hoặc `443` nếu không có load balancer (giả dạng
  HTTPS để vượt firewall).
- **TURN/UDP**: port `3478`, hoặc `443`.

```yaml
turn:
    enabled: true
    tls_port: 443 # dùng 443 khi không có load balancer
    domain: turn.myhost.com
    cert_file: /path/to/turn.crt
    key_file: /path/to/turn.key
```

### ICE fallback chain

```
UDP trực tiếp (host candidates)
  → TCP fallback (tcp_port 7881)
  → TURN/UDP relay (3478)
  → TURN/TLS relay (5349 / 443)
```

### Cấu hình RTC (self-host)

```yaml
rtc:
    port: 7880
    port_range_start: 50000
    port_range_end: 60000
    tcp_port: 7881
    use_external_ip: true # bắt buộc cho hầu hết cloud environment
```

- `use_external_ip: true`: LiveKit dùng STUN tự phát hiện public IP — cần thiết
  khi host có public IP nhưng process không "thấy" nó (điển hình trên cloud).

### Ports cần mở (firewall)

| Port        | Protocol | Mục đích                                                    |
| ----------- | -------- | ----------------------------------------------------------- |
| 7880        | TCP      | Signal (WebSocket, cần TLS termination ở LB cho production) |
| 7881        | TCP      | RTC TCP fallback                                            |
| 50000–60000 | UDP      | ICE UDP candidates (media)                                  |
| 5349        | TCP      | TURN/TLS (hoặc 443 nếu không có LB)                         |
| 3478        | UDP      | TURN/UDP + STUN                                             |

### Local development

```bash
livekit-server --dev                # API key/secret: devkey/secret, bind 127.0.0.1
livekit-server --dev --bind 0.0.0.0 # cho phép máy khác trong LAN truy cập
```

- ngrok chỉ expose được signal (port 7880), **không** dùng cho media UDP.

### LiveKit Cloud — domain cần allow outbound

```
*.livekit.cloud        TCP 443     (signal WebSocket)
*.turn.livekit.cloud   TCP 443     (TURN/TLS relay)
*.host.livekit.cloud   UDP 3478    (TURN/UDP + STUN)
```

---

## 4. Custom Layout Recording (Egress)

### Các loại Egress

- **RoomComposite**: ghi cả room bằng cách render web app trong Chrome headless,
  gắn với vòng đời room.
- **Web**: ghi bất kỳ trang web nào, không gắn với room.
- **Track Composite**: export video + audio của một participant.
- **Track**: export track riêng lẻ, không transcode.

Output hỗ trợ: MP4 file, HLS segments, RTMP/SRT stream, thumbnails, upload
S3/Azure/GCP.

### Luồng hoạt động Custom Template

1. Backend gọi `StartRoomCompositeEgress()`.
2. Egress tạo URL từ `customBaseUrl` + query params `url`, `token`, `layout`.
3. Egress mở URL trong Chrome headless.
4. Template log `START_RECORDING` ra console → bắt đầu ghi.
5. Template log `END_RECORDING` → hoàn tất ghi.

### URL params LiveKit truyền vào template

```
https://my-template.com/?url=wss://livekit.example.com&token=<JWT>&layout=grid
```

| Param    | Ý nghĩa                                   |
| -------- | ----------------------------------------- |
| `url`    | WebSocket URL của LiveKit server          |
| `token`  | JWT cho recorder (hidden, subscribe-only) |
| `layout` | Tên layout truyền từ API call             |

### Template dùng `@livekit/egress-sdk`

```typescript
import EgressHelper from '@livekit/egress-sdk';
import { Room } from 'livekit-client';

const room = new Room();
await room.connect(EgressHelper.getLiveKitURL(), EgressHelper.getAccessToken());

EgressHelper.setRoom(room);
EgressHelper.startRecording();

EgressHelper.onLayoutChanged((newLayout) => {
    /* cập nhật layout */
});
```

SDK tự động kết thúc ghi khi room disconnect.

### API call (backend)

```typescript
const egressClient = new EgressClient('https://myproject.livekit.cloud');

await egressClient.startRoomCompositeEgress('my-room', outputs, {
    layout: 'grid', // grid | speaker | single-speaker | custom
    customBaseUrl: 'https://my-custom-template.com',
    encodingOptions: EncodingOptionsPreset.H264_1080P_30,
    audioOnly: false, // audio-only sẽ bỏ qua layout/customBaseUrl
});
```

### Token grants cho recording

Backend gọi Egress API:

```json
{ "video": { "room": "my-room", "roomRecord": true } }
```

Recorder (do Egress tự tạo, hidden subscribe-only):

```json
{
    "video": {
        "room": "my-room",
        "roomJoin": true,
        "canSubscribe": true,
        "canPublish": false
    }
}
```

> **Lưu ý quan trọng**: nếu có logic quản lý subscription server-side, phải
> filter out participant `kind = EGRESS`. Nếu không, `UpdateSubscriptions()` sẽ
> ghi đè subscription của recorder.

### Test template local

```bash
lk egress test-template \
    --base-url http://localhost:5173 \
    --room my-room \
    --layout grid \
    --publishers 4
```

Lệnh này tạo room, thêm 4 virtual publishers và mở browser với URL đầy đủ
params.

### Egress config (self-host)

```yaml
api_key: livekit-api-key
api_secret: livekit-api-secret
ws_url: ws://livekit-server:7880
redis:
    address: redis-server:6379
template_port: 7980
template_base: http://localhost:7980/
storage:
    s3:
        bucket: my-bucket
        region: us-east-1
        access_key: ${AWS_ACCESS_KEY_ID}
        secret: ${AWS_SECRET_ACCESS_KEY}
```

```bash
docker run --rm \
    -e EGRESS_CONFIG_FILE=/config/config.yaml \
    -v ~/egress-config:/config \
    livekit/egress
```

### Áp dụng cho smiski

`meeting-management` có thể host một custom recording template riêng để ghi
meeting theo layout tùy chỉnh (speaker view, grid, screen-share-only), lưu lên
S3 và phục vụ playback sau đó. Backend Java cấp token với `roomRecord` để
trigger egress; nhớ filter `kind=EGRESS` trong logic subscription động.

---

## Nguồn

- Tokens & grants — https://docs.livekit.io/frontends/reference/tokens-grants/
- Participant management —
  https://docs.livekit.io/intro/basics/rooms-participants-tracks/participants/
- Room service API — https://docs.livekit.io/reference/other/roomservice-api/
- Deploying LiveKit — https://docs.livekit.io/home/self-hosting/deployment/
- Ports & firewall — https://docs.livekit.io/home/self-hosting/ports-firewall/
- Running locally — https://docs.livekit.io/transport/self-hosting/local/
- Cloud firewall — https://docs.livekit.io/deploy/admin/firewall/
- Egress overview — https://docs.livekit.io/home/egress/overview/
- Room composite — https://docs.livekit.io/home/egress/room-composite/
- Custom template — https://docs.livekit.io/home/egress/custom-template/
- Egress examples — https://docs.livekit.io/home/egress/examples/
- Egress GitHub — https://github.com/livekit/egress
