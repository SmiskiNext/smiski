# Hướng dẫn bộ kiểm thử tải & QoS (services/test)

Tài liệu này hướng dẫn vận hành bộ kiểm thử đo hiệu năng và chất lượng luồng
media của hệ thống họp WebRTC. Nó dành cho người chạy bài thực hành và lấy số
liệu đưa vào báo cáo.

> Bản tham chiếu kỹ thuật đầy đủ (tiếng Anh, chi tiết từng quyết định) nằm ở
> `services/test/AGENTS.md`. Tài liệu này là bản hướng dẫn thao tác.

---

## 1. Mục đích

Bộ kiểm thử này tạo ra bằng chứng đo được cho 5 ca kiểm thử của bài thực hành:

| Ca        | Nội dung                                                             | Ngưỡng đạt                                                                          |
| --------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| **TC-01** | Vượt NAT thật: 2 chân — đi thẳng qua NAT, và fallback qua TURN relay | Kết nối thành công qua kênh mã hóa · Call Setup Time < 3 giây · audio/video mượt    |
| **TC-02** | Đo chất lượng luồng media (QoS) cuộc gọi 2 người, 15 phút            | Độ trễ một chiều < 200 ms · Mất gói < 2% · Jitter < 30 ms                           |
| **TC-03** | Tải phòng họp: 30 người, 10 camera 720p, 1 chia sẻ màn hình          | SFU phân phối đúng, không sập room · không nghẽn băng thông · tỷ lệ rớt = 0%        |
| **TC-04** | Sinh Token & đồng bộ trạng thái: 500 request đồng thời trong 1 giây  | Tỷ lệ cấp token 100% · Redis cập nhật đúng · thời gian phản hồi trung bình < 500 ms |
| **TC-05** | Giám sát tài nguyên hạ tầng dưới tải (chạy kèm TC-03/TC-04)          | CPU LiveKit < 80% · RAM Redis < 256 MB                                              |

Nguyên tắc: **phối hợp script và người**. Việc lặp lại được (dựng khóa, cấp
token, seed dữ liệu, bắn tải, thu số liệu) do lệnh `smiski test` lo. Việc cần
mắt người (mở trình duyệt, đọc thời gian thiết lập, chia sẻ màn hình, giữ phiên
15 phút, phán xét kết quả so với ngưỡng) làm thủ công. Tự động hóa toàn bộ chưa
bao giờ là mục tiêu.

---

## 2. Kiến trúc tổng quan

Bộ kiểm thử **không sao chép** stack dev. Nó là một lớp phủ (overlay): tệp
`services/test/compose.yaml` khai báo `name: smiski-test` và
`include: ../docker/compose.yaml`, nên nó tự động bám theo stack dev thay vì tự
trôi khỏi một bản sao. Thư mục `services/docker/` là **chỉ đọc** với công việc
này.

```text
                    ┌─────────────── smiski test CLI (scripts/src) ───────────────┐
                    │  keygen → token → seed → loadtest → impair → collect         │
                    └──────────────────────────────────────────────────────────────┘
                              │                │                │
        ┌─────────────────────┘                │                └──────────────┐
        ▼                                      ▼                               ▼
   TC-04a/b                              TC-01/02/03                        TC-05
   k6 (docker)                     lk load-test + trang harness         Prometheus
   500 req → envoy:30000            30 người · người thật              → CSV
   FIT ký local, mock Jira         NAT thật + chia sẻ màn hình         (cAdvisor)

   TC-01: trình duyệt trong container, sau NAT thật
   browser (client-net) ──route 10.77.0.0/24──► nat-gw ─MASQUERADE─► livekit
                                                          │           coturn
                                          Chân 1: UDP mở → đi thẳng (srflx)
                                          Chân 2: chặn UDP → relay TURN/TCP

   Luồng xác thực TC-04:
   k6 → envoy:30000 ─jwt_authn(local_jwks)→ Lua ─ext_authz→ gateway → mock-jira
                                                               │
                                                           valkey (cache)
                                                               ▼
                                                           meet:8080
```

Các thành phần được thêm so với stack dev:

| Thành phần thêm          | Lý do tồn tại                                                        |
| ------------------------ | -------------------------------------------------------------------- |
| `coturn`                 | Máy chủ STUN + TURN cho TC-01, đo tài nguyên riêng được              |
| `nat-gw`                 | NAT thật giữa client và media server — xem mục 6                     |
| `browser`                | Chromium trong container, đứng sau NAT, điều khiển qua noVNC         |
| `mock-jira`              | Bỏ timeout 2 giây gọi Jira khỏi phép đo TC-04                        |
| `harness`                | Trang QoS chạy trên trình duyệt cho TC-01/TC-02, nằm ngoài app Forge |
| `livekit-redis-exporter` | Biến Redis thứ hai (của LiveKit) thành một target đo riêng           |
| `envoy/envoy.yaml`       | Dùng `local_jwks` thay cho `remote_jwks` của Atlassian               |
| `observability/*`        | Thêm job thu metrics cho LiveKit, Coturn, `livekit-redis`            |
| hai mạng cố định         | Đặt `nat-gw` giữa client và media để TC-01 vượt NAT thật — xem mục 6 |

Volume và network mang tiền tố riêng (`smiski-test_valkey-data` so với
`docker_valkey-data`), nên hai stack không bao giờ chia sẻ dữ liệu.

> ⚠️ **Cổng host bị kế thừa nguyên trạng** (30000, 9901, 8281-8284, 9094,
> 7880-7882, 3000, 9090, 3100, 12345), cộng **một cổng mới**: `BROWSER_VNC_PORT`
> (mặc định 3010) cho console noVNC của trình duyệt TC-01 — chọn ngoài dải kế
> thừa vì Grafana đã chiếm 3000. Hai stack **không thể chạy đồng thời** dù dữ
> liệu tách biệt hoàn toàn. Dừng stack dev trước khi chạy stack test, hoặc thêm
> `ports: !override` vào một tệp cục bộ không commit.

---

## 3. Yêu cầu trước khi chạy

- **Docker** kèm Compose v2
- **IP LAN của máy host** mà trình duyệt truy cập được — LiveKit quảng bá IP này
  trong ICE candidate; loopback không hoạt động dưới rootless Docker
- **Ba image Java** đã build sẵn (Compose chỉ pull, không build)
- **Node + pnpm** để chạy CLI `smiski`

Các image công cụ (`grafana/k6`, `livekit/livekit-cli`, `coturn/coturn`) được
pull tự động khi chạy lệnh tương ứng.

---

## 4. Cài đặt

Chạy tất cả lệnh từ **thư mục gốc repo**.

```sh
# 1. Biến môi trường. CẢ HAI tệp .env đều được nạp; services/test/.env thắng khi
#    trùng khóa, nên tệp này chỉ mang phần chênh lệch (delta). SMISKI_HOST_IP và
#    CURSOR_SECRET khai báo với `:?` ở stack gốc — phải tồn tại ở một trong hai.
cp services/test/.env.example services/test/.env
#    → Mở services/test/.env, đặt SMISKI_HOST_IP = IP LAN của máy bạn.

# 2. Sinh khóa. Envoy đọc JWKS lúc khởi động VÀ lúc --mode validate, nên bước này
#    phải chạy TRƯỚC. Thiếu tệp khóa là lỗi cứng.
pnpm --dir scripts smiski test keygen

# 3. Build 3 image Java (Compose chỉ pull — xem cảnh báo image cũ ở mục 5).
./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage

# 4. Khởi động. Profile observability BẮT BUỘC cho TC-05 và cho nửa phía máy chủ
#    của TC-02, TC-03.
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml up -d

# 5. Nạp dữ liệu mẫu. In ra các meeting id mà lệnh sau sẽ dùng.
pnpm --dir scripts smiski test seed
```

Dừng stack:

```sh
docker compose -f services/test/compose.yaml down    # dừng, giữ volume
docker compose -f services/test/compose.yaml down -v # dừng và xóa sạch dữ liệu
```

Ghi chú tiện dụng: `smiski` trong tài liệu này là `pnpm --dir scripts smiski`.
Mọi lệnh đều có `--help`.

---

## 5. Sáu cái bẫy khiến số liệu "mất" thay vì báo lỗi

Đây là các bẫy nguy hiểm nhất: stack trông vẫn khỏe mạnh trong khi con số bạn
cần lại vắng hoặc sai. Tất cả đều đã quan sát thực tế trên máy, không phải suy
đoán.

1. **Image Java cũ → `spring-services` báo down.** Compose chạy chúng với
   `-Dspring.aot.enabled=true`, AOT "đóng băng" `/actuator/prometheus` vào lúc
   build. Không biến môi trường nào thêm lại được. _Triệu chứng:_ stack phục vụ
   request bình thường nhưng target `spring-services` đọc `down` trên
   `/targets`; số liệu phía service của TC-04 rỗng, không có lỗi nào. → **Phải
   build lại 3 image.**

2. **Trang harness cần secure context.** Mở tại `http://localhost:8090`, **không
   bao giờ** dùng địa chỉ LAN. Trình duyệt chỉ mở `navigator.mediaDevices` trong
   secure context; `http://localhost` được coi là an toàn, `http://192.168.x.x`
   thì không. _Triệu chứng:_ trang tải, kết nối, lấy mẫu bình thường nhưng
   `uplink_kbps` = 0 suốt phiên và chia sẻ màn hình lỗi. Trang có tự phát hiện
   và báo ở dòng trạng thái — đó là cảnh báo duy nhất.

3. **Không bật `adaptiveStream`.** Khi bật, client chỉ subscribe track mà nó cho
   là đang hiển thị, nên tab nền để `isSubscribed` = false. _Triệu chứng:_ không
   có `inbound-rtp`, nên `jitter_ms`, `packet_loss_percent`, `downlink_kbps`
   xuất ra **trống** trên một kết nối trông hoàn toàn khỏe — TC-02 mất nửa phần
   downlink. `harness.js` đã đặt `adaptiveStream: false` và `dynacast: false` vì
   lý do này; để nguyên.

4. **Phải có ai đó đang publish thì downlink mới có số liệu.** Một client
   harness đơn độc trong phòng trống sẽ xuất jitter/loss trống dù cấu hình đúng
   — đơn giản vì không có media vào. Khởi động `loadtest room` hoặc một client
   thứ hai trước.

5. **`${VAR:-default}` vô hiệu với khóa đã có trong `.env` của stack dev.**
   `JIRA_API_BASE` đã được đặt ở đó, nên default `:-` không bao giờ áp dụng và
   gateway gọi Atlassian thật trong khi mock ngồi không. _Triệu chứng:_ độ trễ
   thuần trong đúng con số TC-04 đo (timeout 2 giây rồi trả quyền rỗng, không
   lỗi). Giá trị được ghi thẳng trong `compose.yaml`; muốn đổi hướng mock thì
   sửa dòng đó, không sửa `.env`.

6. **Prometheus không expand biến môi trường, nhưng `promtool check config` vẫn
   báo pass.** Cổng dạng template chỉ lỗi `too many colons in address` lúc
   scrape. Cổng trong `observability/prometheus.yml` để literal; đổi
   `LIVEKIT_PROMETHEUS_PORT` phải sửa **cả hai** tệp.

> 📌 Điểm chung: **kiểm tra tệp không phát hiện được bất kỳ bẫy nào ở trên.**
> Sau mỗi thay đổi observability, mở `/targets` trên <http://localhost:9090> và
> xác nhận mọi job báo `up`. Tệp cấu hình hợp lệ không đồng nghĩa target truy
> cập được.

---

## 6. TC-01 vượt NAT thật — hai chân

TC-01 yêu cầu vượt NAT. Cách trung thực để chứng minh là đặt **một NAT thật**
vào đường media rồi để ICE tự chọn, thay vì ép `iceTransportPolicy: 'relay'` rồi
gọi một relay bị ép là "vượt NAT". Nên overlay chạy một trình duyệt trong
container, đứng sau một NAT gateway, và đo hai chân:

- **Chân 1 — đi thẳng qua NAT.** UDP mở, Coturn trả lời STUN. ICE học địa chỉ
  ngoài-NAT và kết nối thẳng tới LiveKit. Bằng chứng: `local_candidate_type` là
  `srflx` (hoặc `prflx`) và `nat traversal proven,true` trong header bản xuất.
  **Không dùng relay.**
- **Chân 2 — fallback relay.** `impair blocked-udp --service nat-gw` chặn UDP
  trong gateway, nên STUN/UDP và media/UDP đều hỏng, ICE **tự** rớt xuống TURN
  qua TCP. Bằng chứng: `local_candidate_type` là `relay` và `relay proven,true`.

Vì sao chỉ 1 loại NAT, không phải 2. Đối chiếu RFC 8445: trong mô hình client
sau NAT ↔ SFU với tới được, **cả cone lẫn symmetric NAT đều đi thẳng** qua
candidate phản xạ mà client tạo ra khi kiểm tra kết nối tới LiveKit. Loại NAT
không đổi kết quả khi server với tới được; thứ ép relay là **mất UDP**. Nên
`nat-gw` mô phỏng một loại — **port-restricted cone** (chính là hành vi của
`MASQUERADE` + conntrack) — và Chân 2 gỡ UDP thay vì đổi loại NAT.

Kiến trúc mạng, tất cả trong `compose.yaml`:

| Phần                                  | Mục đích                                         |
| ------------------------------------- | ------------------------------------------------ |
| `networks.clients` `10.88.0.0/24`     | Một mạng mà media server **không** nằm trên đó   |
| `nat-gw` hai chân `.2` / `10.77.0.13` | Đường duy nhất băng qua, source-NAT trình duyệt  |
| `browser` + `custom-init.sh`          | Chỉ route `10.77.0.0/24` qua `nat-gw`, không hơn |
| Coturn bỏ `--no-udp`, thêm cổng UDP   | Phục vụ STUN/UDP (Chân 1) và TURN/TCP (Chân 2)   |
| `livekit rtc.node_ip: 10.77.0.10`     | Quảng bá đúng địa chỉ trình duyệt NAT route tới  |

> 📌 **Route có phạm vi, không phải default.** `custom-init.sh` chỉ route
> `10.77.0.0/24` qua `nat-gw`. Đổi **default route** sẽ đẩy cả traffic noVNC về
> gateway và làm treo phiên điều khiển. Chỉ traffic media băng qua NAT; truy cập
> cổng noVNC từ host vẫn hoạt động.

Loại client chạy trình duyệt quyết định một lần chạy có thể chứng minh kết nối
trực tiếp hay không:

> 📌 **Trình duyệt trên host vẫn không route được tới `10.77.0.10`.**
> TC-02/TC-03 chạy harness từ trình duyệt trên host, nên với chúng mọi kết nối
> vẫn qua relay — một lần chạy trên host **không** phải bằng chứng kết nối trực
> tiếp. Hãy đọc `local_candidate_type`, đừng suy từ nút bấm. Chỉ trình duyệt
> TC-01 trong container mới đứng sau NAT và cho ra `srflx`.

---

## 7. Chạy từng ca kiểm thử

Hai header bắt buộc trên mọi request tự tạo tay, thiếu là lỗi khó hiểu:
`x-issue-id: 10001` và `x-forge-oauth-system: loadtest-system-token` (thiếu cái
sau trả `500 configuration_error`). Thân request cần cả `displayName` và
`deviceId`.

### TC-01 — Vượt NAT (< 3 giây), hai chân

Điều kiện: stack đang chạy (có `browser` và `nat-gw`), đã `keygen` và `seed`, có
sẵn một meeting id. Client là Chromium trong container đứng sau NAT, điều khiển
qua noVNC tại `http://localhost:3010`, **không** phải trình duyệt trên host. Hai
chân dùng chung hai bước đầu:

```sh
# 1. Vào phòng qua gateway để lấy LiveKit token
curl -s -X POST "http://localhost:30000/api/1/meetings/<MEETING_ID>:join" \
    -H "Authorization: Bearer $(pnpm -s --dir scripts smiski test token)" \
    -H 'Content-Type: application/json' -H 'x-issue-id: 10001' \
    -H 'x-forge-oauth-system: loadtest-system-token' \
    -d '{"displayName":"NatClient","deviceId":"d1"}'
```

Sau đó, **thủ công:** mở <http://localhost:3010> (noVNC), trong trình duyệt đó
vào `http://10.77.0.11`. Dán token, để server URL `ws://10.77.0.10:7880`, để
STUN `stun:10.77.0.12:3478`, **để relay-only TẮT**. Rồi chạy lần lượt hai chân.

**Chân 1 — đi thẳng qua NAT.** Bấm connect. Đọc thời gian thiết lập; dòng
`Interpretation` phải là "Leg 1: NAT traversed directly", rồi bấm export CSV.
Bằng chứng: `nat traversal proven,true`, `local_candidate_type` =
`srflx`/`prflx`, `call setup within 3000 ms budget,true`, và **không** có
traffic relay ở Coturn.

**Chân 2 — fallback relay.** Chạy
`smiski test impair blocked-udp --service nat-gw` để chặn UDP trong gateway, rồi
disconnect và connect lại từ trang. Dòng `Interpretation` phải là "Leg 2:
relayed through TURN", rồi bấm export CSV. Khôi phục bằng
`smiski test impair lan --service nat-gw`. Bằng chứng: `relay proven,true`,
`local_candidate_type` = `relay`, `call setup within 3000 ms budget,true`, cộng
`turn_total_allocations` và `turn_total_traffic_sentb` từ Coturn. Kết nối thành
công **không** chứng minh chân nào — kiểu candidate mới là thứ phân biệt hai
chân.

### TC-02 — Chất lượng QoS trong 15 phút

Điều kiện: stack chạy, đã `keygen`/`seed`, thêm **một publisher thứ hai** và
profile `4g`. Khác TC-01, TC-02 dùng trình duyệt **trên host** tại
<http://localhost:8090> (server URL `ws://localhost:7880`); không cần client
NAT, và việc kết nối host luôn qua relay không ảnh hưởng số liệu QoS nó đo.

```sh
# 1. Bóp mạng 4g — BẮT BUỘC. Mạng local không bóp có độ trễ ~0.04 ms, kết quả
#    "< 200 ms" khi đó vô nghĩa và không được trình bày như bằng chứng.
smiski test impair 4g --service livekit-server

# 2. Khởi động publisher trong phòng
smiski test loadtest room --room "$ROOM" --duration 16m
```

1. **Thủ công:** vào phòng từ trang harness và để nó lấy mẫu đủ **15 phút**.
   Không reload — mẫu nằm trong trang, chốt chặn `beforeunload` là bảo vệ duy
   nhất.
2. Bấm export, rồi gỡ mạng:
   `smiski test impair 4g --service livekit-server --remove`.
3. Thu số liệu: `smiski test collect --cases tc02 --network-profile 4g`.

### TC-03 — Tải phòng, 30 người

```sh
smiski test loadtest room --room "$ROOM" --duration 3m \
    --video-publishers 10 --subscribers 19 --video-resolution high
```

**Phần thủ công:** `lk load-test` hardcode `TrackSource_CAMERA`, **không**
publish được chia sẻ màn hình. Vào cùng phòng từ trang harness và dùng nút chia
sẻ màn hình; mốc thời gian bắt đầu/kết thúc đi vào header bản xuất để căn với
`livekit_track_publish_counter`.

Bảng tóm tắt của `lk` được ghi lại nhưng **không** dùng để phán xét ngưỡng: cột
Latency của nó là thời gian gói đến phía tester và không báo jitter. Lấy jitter,
mất gói, RTT từ `/metrics` của LiveKit và từ bản xuất harness.

### TC-04 — Sinh token, 500 request trong 1 giây

```sh
# TC-04a: throughput cấp token — cả 4 lượt (cold/warm × single/sharded)
smiski test loadtest tokens --admission-policy ALLOW_ALL

# TC-04b: trạng thái phê duyệt lưu trong cache
smiski test loadtest tokens --admission-policy MANUAL_APPROVAL \
    --variants single --cache-states warm
```

Ngưỡng 500 ms **chỉ** áp cho lượt warm-cache + sharded. Đuôi single-room đo hàng
đợi pessimistic lock, không phải throughput (xem mục 9). Đo thực tế trên stack
này: trung vị **253.5 ms** single-room cold so với **3.9 ms** sharded warm, cùng
mức tải.

TC-04b có tiêu chí là trạng thái phê duyệt trong cache — mà response không thể
cho thấy (dưới `MANUAL_APPROVAL` mọi response mang `token: null`). Đọc trực tiếp
từ Redis **ngay sau khi chạy** vì key có TTL:

```sh
docker exec smiski-test-valkey-1 valkey-cli --scan --count 500 | rg join_request
```

Đo trên stack này: 500 request tạo 477 dòng `PENDING`, 487 key
`join_request_meta`, 487 key `join_request_device`, và 23 request bị đếm là lỗi
`http 5xx` thay vì bị trung bình hóa vào thời gian.

### TC-05 — Tài nguyên container

Chạy kèm TC-03 và TC-04; cần profile `observability`.

```sh
smiski test collect --cases tc05 --since 30
```

Hai instance cache là hai job riêng (`valkey`, `livekit-redis`), nên ngưỡng 256
MB áp đúng cái cần. Bộ sinh tải có series cAdvisor riêng (`smiski-test-k6`,
`smiski-test-lk`) nên trừ được khỏi số liệu host thay vì bị tính nhầm là tài
nguyên của hệ thống đang đo.

---

## 8. Lấy kết quả

Kết quả rơi vào `services/test/results/`, thư mục này **được gitignore** — mọi
tệp ở đó là artifact tái tạo được bằng cách chạy lại, không phải mã nguồn.

- **CSV từ `smiski test collect`**: một tệp mỗi ca (ví dụ `tc02-*.csv`,
  `tc05-*.csv`), truy vấn từ Prometheus range API. Cột được gắn nhãn rõ; khi số
  liệu vắng, tệp ghi `UNAVAILABLE` / `EMPTY` / `SUBSTITUTED` thay vì lặng lẽ bỏ
  qua.
- **CSV từ trang harness**: bấm nút export trên trang, mang jitter, mất gói,
  `framesDropped`, bitrate up/down, `local_candidate_type` và mốc thời gian chia
  sẻ màn hình. Đây là nguồn số liệu phía client cho TC-01/TC-02.
- **Bảng tóm tắt `lk load-test`**: in ra stdout khi chạy TC-03, ghi lại làm tham
  khảo (không dùng phán xét ngưỡng).
- **Dashboard Grafana**: <http://localhost:3000> (đăng nhập bằng
  `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` trong `.env`, mặc định
  `admin`/`admin`) để xem trực quan CPU/RAM/mạng theo thời gian trong lúc chạy.
- **Prometheus trực tiếp**: <http://localhost:9090> — kiểm tra `/targets` và
  truy vấn thủ công `livekit_*`, `turn_*`, `redis_memory_used_bytes`,
  `container_cpu_usage_seconds_total`.

Đối chiếu với ngưỡng ở bảng mục 1 khi viết báo cáo.

---

## 9. Bảng phân công tự động / thủ công

| Bước                                              | Script | Thủ công |
| ------------------------------------------------- | :----: | :------: |
| Sinh khóa, ký token, seed dữ liệu                 |   ✅   |          |
| Áp / gỡ / xem impairment mạng                     |   ✅   |          |
| Tải phòng TC-03, tải token TC-04                  |   ✅   |          |
| Thu metrics ra CSV                                |   ✅   |          |
| Vào phòng từ trình duyệt, đọc thời gian thiết lập |        |    ✅    |
| Điều khiển trình duyệt NAT qua noVNC (TC-01)      |        |    ✅    |
| Bắt đầu / dừng chia sẻ màn hình                   |        |    ✅    |
| Giữ TC-02 đủ 15 phút                              |        |    ✅    |
| Phán xét kết quả so với ngưỡng                    |        |    ✅    |

---

## 10. Các điểm lệch so với đề bài (và cách xử lý)

Đề bài mô tả một hệ thống khác với repo này. Mỗi điểm lệch được xử lý bằng một
quyết định nêu rõ để không ai diễn giải lại theo ý mình.

| #   | Đề bài nói                       | Thực tế trong repo                                                    | Cách xử lý                                                              |
| --- | -------------------------------- | --------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1   | Backend là NestJS                | Spring Boot 4 / Java 25, service `meet`                               | TC-04 nhắm vào service `meet` (Spring)                                  |
| 2   | Giám sát container Coturn        | Không có Coturn; không có `turn:` block                               | Thêm container Coturn standalone (STUN + TURN) vào stack này            |
| 3   | "container Redis"                | **Hai**: `valkey` (ứng dụng) và `livekit-redis` (nội bộ LiveKit)      | TC-05 đo cả hai, báo cáo thành hai job riêng                            |
| 4   | Metrics từ LiveKit dashboard     | LiveKit không expose: `prometheus_port` chưa bật, không có job scrape | Bật `prometheus.port`; thêm job scrape ở đây                            |
| 5   | Tỷ lệ đồng bộ về hệ QLDA         | `meet` không hề ghi sang Jira; chỉ lưu `issue_id`/`issue_key` local   | Định nghĩa lại thành độ trễ outbox drain + số dòng `participation_logs` |
| 6   | Kafka lag làm integration metric | `apache/kafka:4.1.0` không có JMX exporter, cố ý loại khỏi Prometheus | Đo drain qua bảng `outbox_events`                                       |

Năm điểm lệch phát sinh từ code (không từ đề bài):

1. **Hai tiêu chí của TC-04 không thể cùng đúng trong một lần chạy.**
   `ALLOW_ALL` trả token nhưng không chạm Redis; `MANUAL_APPROVAL` ghi Redis
   nhưng response `token: null`. Nên tách TC-04a / TC-04b. Thêm nữa,
   `RequestJoinApplicationService` giữ khóa `PESSIMISTIC_WRITE` suốt giao dịch,
   nên 500 join vào cùng một phòng đo hàng đợi khóa chứ không phải throughput —
   do đó có biến thể single / sharded.
2. **`lk load-test` không publish được chia sẻ màn hình** — hardcode
   `TrackSource_CAMERA`. Nhánh đó của TC-03 làm thủ công, đóng mốc thời gian vào
   bản xuất harness.
3. **Không thể tạo Forge Invocation Token thật ở local** — Atlassian ký nó.
   Envoy đổi `remote_jwks` sang `local_jwks` đọc tệp khóa sinh ra; phần còn lại
   của chuỗi (verify RS256, Lua lọc claim, ext_authz gRPC, cache Valkey của
   gateway) vẫn nằm trong đường đo. Không cần dịch vụ mock JWKS.
4. **Không dùng được app Forge làm client QoS.** App dựng `Room` mà không có
   cách chèn `rtcConfig`, nên trang harness độc lập là thứ cho phép cấu hình ICE
   và giữ app production không bị đụng.
5. **Loại NAT không tự ép relay trong mô hình client↔SFU.** Đối chiếu RFC 8445:
   khi SFU với tới được, client sau bất kỳ NAT nào — cone hay symmetric — đều đi
   thẳng qua candidate phản xạ; chỉ mất UDP (hoặc server không với tới được) mới
   ép TURN. Nên TC-01 không đổi loại NAT. Nó đặt một NAT thật (`nat-gw`,
   port-restricted cone) vào đường đi và đo hai chân: đi thẳng khi UDP mở, relay
   sau khi `blocked-udp` gỡ UDP. Thiết kế cũ ép `iceTransportPolicy: 'relay'`,
   chỉ chứng minh relay chạy được chứ không chứng minh vượt NAT — bản này thay
   thế nó.

---

## 11. Hình dạng claim của token

`smiski test token` lấy claim từ `services/gateway/internal/fit/parser.go`. Tất
cả `iss`, `aud`, `principal`, `context.cloudId`, `app.id`, `app.apiBaseUrl` và
`app.environment.id` đều bắt buộc; thiếu `app.id` hoặc `app.environment.id` sẽ
lỗi `403` nêu tên claim.

> ⚠️ **`--cloud-id` phải bằng `meetings.tenant_id` đã seed.** Lệch nhau không
> phải lỗi xác thực — nó qua được xác thực, rồi bộ lọc `@TenantId` của Hibernate
> trả `404 MEETING_NOT_FOUND`, trông như phòng không tồn tại chứ không như token
> sai. `smiski test seed` in ra tenant nó dùng chính vì lý do này.

---

## 12. Kiểm tra cấu hình

```sh
# Định nghĩa Compose, cả hai cách — service sau profile KHÔNG được kiểm tra khi
# profile chưa bật, nên chỉ chạy bản thường sẽ bỏ sót một nửa.
docker compose -f services/test/compose.yaml config
COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml config

# Envoy. Cần đã chạy keygen: local_jwks được đọc lúc validate, và tập khóa phải
# được mount đúng chỗ envoy.yaml khai báo. envoy.yaml mount dạng TỆP, không mount
# cả thư mục (mount thư mục read-only vào /etc/envoy chặn hai mount lồng bên dưới).
docker run --rm \
    -v "$(pwd)/services/test/envoy/envoy.yaml:/etc/envoy/envoy.yaml:ro" \
    -v "$(pwd)/services/docker/envoy/lua:/etc/envoy/lua:ro" \
    -v "$(pwd)/services/test/keys:/etc/envoy/keys:ro" \
    envoyproxy/envoy:v1.36-latest --mode validate -c /etc/envoy/envoy.yaml

# Cấu hình scrape Prometheus
docker run --rm -v "$(pwd)/services/test/observability:/w:ro" \
    --entrypoint promtool prom/prometheus:v3.13.2 check config /w/prometheus.yml

# CLI
pnpm --dir scripts lint && pnpm --dir scripts typecheck
```

Sau đó, trên stack **đang chạy**, xác nhận mọi target scrape báo `up`:

```sh
curl -s 'http://localhost:9090/api/v1/targets?state=active' \
    | python3 -c 'import json,sys; [print(t["labels"]["job"], t["health"]) for t in json.load(sys.stdin)["data"]["activeTargets"]]'
```

---

## 13. Xử lý sự cố nhanh

| Triệu chứng                                  | Nguyên nhân thường gặp                                   | Cách xử lý                                                                                 |
| -------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `spring-services` báo `down` trên `/targets` | Image Java cũ, chưa có `/actuator/prometheus`            | Build lại 3 image (mục 4 bước 3)                                                           |
| Stack không start, `Address already in use`  | Stack dev đang chạy, trùng cổng                          | `docker compose -f services/docker/compose.yaml down` trước                                |
| `uplink_kbps` = 0, chia sẻ màn hình lỗi      | Mở harness bằng địa chỉ LAN                              | Mở bằng `http://localhost:8090`                                                            |
| jitter/loss/downlink xuất trống              | Phòng chưa có ai publish, hoặc `adaptiveStream` bật      | Chạy `loadtest room` trước; giữ `adaptiveStream: false`                                    |
| TC-04 độ trễ cao bất thường (~2 giây)        | Gateway gọi Atlassian thật thay vì mock                  | Kiểm tra `JIRA_API_BASE` trong `compose.yaml` (không phải `.env`)                          |
| TC-01 Chân 1 ra `host` chứ không `srflx`     | Đang dùng trình duyệt host, không phải browser NAT       | Vào qua noVNC `http://localhost:3010` → `http://10.77.0.11`, server `ws://10.77.0.10:7880` |
| TC-01 Chân 2 không rớt xuống relay           | Chưa chặn UDP, hoặc secret Coturn lệch                   | `impair blocked-udp --service nat-gw`; kiểm tra secret khớp `rtc.turn_servers.secret`      |
| Trình duyệt NAT không tải được trang/kết nối | `nat-gw` chưa sẵn sàng, hoặc route media chưa được thêm  | Xem log `nat-gw` ("nat-gw ready"); kiểm tra `browser` chạy `custom-init.sh`                |
| `404 MEETING_NOT_FOUND` khi join             | `--cloud-id` lệch `meetings.tenant_id`                   | Dùng tenant mà `smiski test seed` in ra                                                    |
| Target LiveKit/Coturn vắng khỏi `/targets`   | Override observability chưa mount, hoặc chưa bật profile | Kiểm tra mount `prometheus.yml`; start với `COMPOSE_PROFILES=observability`                |

---

## 14. Giới hạn đã biết của môi trường

Các điểm này là giới hạn thao tác/bằng chứng, không phải lỗi code:

- **TC-02 cần giữ đủ 15 phút bằng tay** — trang harness lấy mẫu vô hạn theo
  thiết kế, nhưng phiên đủ độ dài chưa được chạy trọn từ đầu đến cuối tự động.
- **CSV phía trình duyệt phải tự bấm export** — chưa có artifact mẫu commit sẵn;
  toàn bộ đường đo phía client sinh ra khi bạn chạy thật. Trình duyệt NAT lưu
  CSV vào `results/` (mount `/config/Downloads`).
- **Ảnh trình duyệt TC-01 mặc định `:latest`** — ghim `BROWSER_IMAGE` vào một
  tag Chromium cụ thể để phép đo tái lập được, cùng lý do mọi ảnh công cụ khác
  được ghim.
- **Cổng 30000** có thể bị tiến trình khác trên máy chiếm; khi đó thêm
  `ports: !override` cục bộ (không commit) và truyền `--gateway-origin` tương
  ứng cho lệnh `smiski test loadtest tokens`.
- **Bộ đếm của Coturn tích lũy theo tiến trình** và reset khi container khởi
  động lại — đọc chênh lệch trong một phiên, đừng đọc giá trị tuyệt đối xuyên
  phiên.
