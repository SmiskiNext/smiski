# Hướng dẫn chạy bộ kiểm thử tải & QoS

Hướng dẫn thao tác để chạy TC-01–TC-05 và lấy số liệu đưa vào báo cáo. Đề bài và
ngưỡng đạt: `services/test/problem.md`. Quyết định thiết kế, ràng buộc kỹ thuật,
điểm lệch so với đề bài: `services/test/AGENTS.md`.

`smiski` trong tài liệu này là `pnpm --dir scripts smiski`. Mọi lệnh đều có
`--help`. Chạy tất cả lệnh từ **thư mục gốc repo**.

## 1. Ngưỡng đạt của từng ca

| Ca    | Ngưỡng đạt                                                                     |
| ----- | ------------------------------------------------------------------------------ |
| TC-01 | Kết nối qua kênh mã hóa · Call Setup Time < 3 giây · audio/video mượt          |
| TC-02 | Độ trễ một chiều < 200 ms · Mất gói < 2% · Jitter < 30 ms                      |
| TC-03 | SFU không sập room · không nghẽn băng thông · tỷ lệ lỗi/rớt = 0%               |
| TC-04 | Tỷ lệ cấp token 100% · Redis cập nhật đúng, không xung đột · phản hồi < 500 ms |
| TC-05 | CPU LiveKit < 80% · RAM Redis < 256 MB                                         |

## 2. Yêu cầu trước khi chạy

- Docker + Compose v2
- IP LAN của máy host (LiveKit quảng bá IP này trong ICE candidate; loopback
  không hoạt động dưới rootless Docker)
- 3 image Java đã build sẵn (bước 3 dưới đây)
- Node + pnpm

## 3. Cài đặt

```sh
cp services/test/.env.example services/test/.env
# → mở services/test/.env, đặt SMISKI_HOST_IP = IP LAN của máy bạn

pnpm --dir scripts smiski test keygen

./services/gradlew -p services/tenant bootBuildImage
./services/gradlew -p services/meet bootBuildImage
./services/gradlew -p services/notification bootBuildImage

COMPOSE_PROFILES=observability docker compose -f services/test/compose.yaml up -d

pnpm --dir scripts smiski test seed # in ra meeting id + tenant dùng cho các bước sau
```

Dừng stack:

```sh
docker compose -f services/test/compose.yaml down    # dừng, giữ volume
docker compose -f services/test/compose.yaml down -v # dừng và xóa sạch dữ liệu
```

Sau khi stack chạy, xác nhận mọi target báo `up` tại
<http://localhost:9090/targets> trước khi đo — tệp cấu hình hợp lệ không đồng
nghĩa target truy cập được.

Hai header bắt buộc trên mọi request tự tạo tay: `x-issue-id: 10001` và
`x-forge-oauth-system: loadtest-system-token`. Thân request cần cả `displayName`
và `deviceId`.

## 4. TC-01 — Vượt NAT (< 3 giây), hai chân

Điều kiện: stack đang chạy (có `browser` và `nat-gw`), đã `keygen` và `seed`.

```sh
# Lấy LiveKit token qua gateway
curl -s -X POST "http://localhost:30000/api/1/meetings/<MEETING_ID>:join" \
    -H "Authorization: Bearer $(pnpm -s --dir scripts smiski test token)" \
    -H 'Content-Type: application/json' -H 'x-issue-id: 10001' \
    -H 'x-forge-oauth-system: loadtest-system-token' \
    -d '{"displayName":"NatClient","deviceId":"d1"}'
```

1. Mở <http://localhost:3010> (noVNC) — **không** dùng trình duyệt trên host.
   Trong trình duyệt đó, vào `http://10.77.0.11`.
2. Dán token. Server URL `ws://10.77.0.10:7880`, STUN `stun:10.77.0.12:3478`,
   **relay-only TẮT**.
3. **Chân 1 — đi thẳng.** Bấm connect. Đọc thời gian thiết lập, xác nhận
   `Interpretation` = "Leg 1: NAT traversed directly". Bấm export CSV.
4. **Chân 2 — relay.** Chạy `smiski test impair blocked-udp --service nat-gw`,
   disconnect rồi connect lại. Xác nhận `Interpretation` = "Leg 2: relayed
   through TURN". Bấm export CSV. Khôi phục:
   `smiski test impair lan --service nat-gw`.

Đọc kết quả từ CSV, không suy từ nút bấm:

| Chân | `local_candidate_type` | Cột khác cần đúng           |
| ---- | ---------------------- | --------------------------- |
| 1    | `srflx` / `prflx`      | `nat traversal proven,true` |
| 2    | `relay`                | `relay proven,true`         |

Cả hai chân cần `call setup within 3000 ms budget,true`.

## 5. TC-02 — QoS 15 phút, hai người cùng sau NAT

Điều kiện: stack chạy (có cả `browser` và `browser-b`), đã `keygen`/`seed`, có
meeting id.

```sh
# Bóp mạng 4g — BẮT BUỘC, mạng local không bóp cho kết quả vô nghĩa
smiski test impair 4g --service livekit-server
```

1. Tạo hai token bằng lệnh `curl` ở mục 4, đổi `deviceId` thành `d1` và `d2`.
2. **Người A:** mở <http://localhost:3010> (noVNC) → `http://10.77.0.11`. Dán
   token A, server `ws://10.77.0.10:7880`, STUN `stun:10.77.0.12:3478`,
   relay-only TẮT, **publish media BẬT**, vào phòng chung.
3. **Người B:** mở <http://localhost:3011> (noVNC) → `http://10.77.0.11`, dán
   token B, cùng thiết lập, cùng phòng.
4. Để **cả hai** lấy mẫu đủ 15 phút. Không reload — mẫu nằm trong trang.
5. Bấm export CSV ở **cả hai** console.
6. Gỡ mạng: `smiski test impair 4g --service livekit-server --remove`.
7. `smiski test collect --cases tc02 --network-profile 4g`

Đọc mỗi CSV: `local_candidate_type` phải là `srflx`/`prflx`, cùng
`round_trip_ms` < 200, `packet_loss_percent` < 2, `jitter_ms` < 30. Cột inbound
trống → bên kia chưa publish hoặc không cùng phòng.

Cách nhẹ hơn (không cần chứng minh vượt NAT, chỉ lấy số QoS): một trình duyệt
**trên host** tại <http://localhost:8090> (server `ws://localhost:7880`) +
`smiski test loadtest room --room "$ROOM" --duration 16m` làm publisher còn lại.

## 6. TC-03 — Tải phòng, 30 người

```sh
smiski test loadtest room --room "$ROOM" --duration 3m \
    --video-publishers 10 --subscribers 19 --video-resolution high
```

Song song: vào cùng phòng từ trang harness (<http://localhost:8090>) và bấm nút
chia sẻ màn hình — `lk load-test` không publish được (hardcode camera). Mốc thời
gian bắt đầu/kết thúc đi vào header bản xuất CSV.

Đọc kết quả: `livekit_track_publish_counter` (Prometheus, mục 8) + bản xuất
harness. Bảng tóm tắt `lk` chỉ ghi lại tham khảo, không dùng phán xét ngưỡng
(không báo jitter).

## 7. TC-04 — Sinh token, 500 request trong 1 giây

```sh
# TC-04a: throughput cấp token — cả 4 lượt (cold/warm × single/sharded)
smiski test loadtest tokens --admission-policy ALLOW_ALL

# TC-04b: trạng thái phê duyệt lưu trong cache
smiski test loadtest tokens --admission-policy MANUAL_APPROVAL \
    --variants single --cache-states warm
```

Ngưỡng 500 ms chỉ áp cho lượt warm-cache + sharded (đọc từ CLI summary).

TC-04b: response mang `token: null` dưới `MANUAL_APPROVAL`, đọc trạng thái từ
Redis **ngay sau khi chạy** (key có TTL):

```sh
docker exec smiski-test-valkey-1 valkey-cli --scan --count 500 | rg join_request
```

## 8. TC-05 — Tài nguyên container

Chạy kèm TC-03 và TC-04; cần profile `observability`.

```sh
smiski test collect --cases tc05 --since 30
```

Đọc CSV: cột CPU của `livekit-server`, cột RAM của `valkey` (không phải
`livekit-redis` — hai job tách riêng).

## 9. Lấy kết quả

Kết quả rơi vào `services/test/results/` (gitignored).

- **CSV từ `smiski test collect`**: một tệp mỗi ca, truy vấn từ Prometheus. Số
  liệu vắng ghi `UNAVAILABLE`/`EMPTY`/`SUBSTITUTED` thay vì bỏ qua.
- **CSV từ trang harness**: bấm export trên trang, mang jitter, mất gói,
  `framesDropped`, bitrate up/down, `local_candidate_type`.
- **Grafana**: <http://localhost:3000> (đăng nhập `GRAFANA_ADMIN_USER` /
  `GRAFANA_ADMIN_PASSWORD`, mặc định `admin`/`admin`).
- **Prometheus**: <http://localhost:9090> — `/targets` và truy vấn thủ công
  `livekit_*`, `turn_*`, `redis_memory_used_bytes`,
  `container_cpu_usage_seconds_total`.

## 10. Bảng phân công tự động / thủ công

| Bước                                              | Script | Thủ công |
| ------------------------------------------------- | :----: | :------: |
| Sinh khóa, ký token, seed dữ liệu                 |   ✅   |          |
| Áp / gỡ / xem impairment mạng                     |   ✅   |          |
| Tải phòng TC-03, tải token TC-04                  |   ✅   |          |
| Thu metrics ra CSV                                |   ✅   |          |
| Vào phòng từ trình duyệt, đọc thời gian thiết lập |        |    ✅    |
| Điều khiển trình duyệt NAT qua noVNC (TC-01/02)   |        |    ✅    |
| Bắt đầu / dừng chia sẻ màn hình                   |        |    ✅    |
| Giữ TC-02 đủ 15 phút                              |        |    ✅    |
| Phán xét kết quả so với ngưỡng                    |        |    ✅    |

## 11. Xử lý sự cố nhanh

| Triệu chứng                                  | Nguyên nhân thường gặp                                   | Cách xử lý                                                                                 |
| -------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `spring-services` báo `down` trên `/targets` | Image Java cũ, chưa có `/actuator/prometheus`            | Build lại 3 image (mục 3)                                                                  |
| Stack không start, `Address already in use`  | Stack dev đang chạy, trùng cổng                          | `docker compose -f services/docker/compose.yaml down` trước                                |
| `uplink_kbps` = 0 (harness trên host)        | Mở harness bằng địa chỉ LAN                              | Mở bằng `http://localhost:8090`                                                            |
| `uplink_kbps` = 0 ở trình duyệt NAT          | Cờ fake-media/secure-context không áp lên Chromium       | Kiểm tra `browser`/`browser-b` khởi động với đủ `CHROME_CLI`; ghim `BROWSER_IMAGE` cố định |
| jitter/loss/downlink xuất trống              | Phòng chưa có ai publish, hoặc `adaptiveStream` bật      | Chạy `loadtest room` trước; giữ `adaptiveStream: false`                                    |
| TC-04 độ trễ cao bất thường (~2 giây)        | Gateway gọi Atlassian thật thay vì mock                  | Kiểm tra `JIRA_API_BASE` trong `compose.yaml` (không phải `.env`)                          |
| TC-01 Chân 1 ra `host` chứ không `srflx`     | Đang dùng trình duyệt host, không phải browser NAT       | Vào qua noVNC `http://localhost:3010` → `http://10.77.0.11`                                |
| TC-01 Chân 2 không rớt xuống relay           | Chưa chặn UDP, hoặc secret Coturn lệch                   | `impair blocked-udp --service nat-gw`; kiểm tra secret khớp `rtc.turn_servers.secret`      |
| Trình duyệt NAT không tải được trang/kết nối | `nat-gw` chưa sẵn sàng, hoặc route media chưa được thêm  | Xem log `nat-gw` ("nat-gw ready"); kiểm tra `browser` chạy `custom-init.sh`                |
| `404 MEETING_NOT_FOUND` khi join             | `--cloud-id` lệch `meetings.tenant_id`                   | Dùng tenant mà `smiski test seed` in ra                                                    |
| Target LiveKit/Coturn vắng khỏi `/targets`   | Override observability chưa mount, hoặc chưa bật profile | Kiểm tra mount `prometheus.yml`; start với `COMPOSE_PROFILES=observability`                |

## 12. Giới hạn đã biết

- TC-02 cần giữ đủ 15 phút bằng tay — chưa có script chạy trọn tự động.
- CSV phía trình duyệt phải tự bấm export; trình duyệt NAT lưu vào `results/`
  (mount `/config/Downloads`).
- Ghim `BROWSER_IMAGE` vào một tag Chromium cụ thể để phép đo tái lập được.
- Cổng 30000 có thể bị chiếm; nếu vậy thêm `ports: !override` cục bộ và truyền
  `--gateway-origin` tương ứng cho `smiski test loadtest tokens`.
- Bộ đếm của Coturn tích lũy theo tiến trình, reset khi container khởi động lại
  — đọc chênh lệch trong một phiên, đừng đọc giá trị tuyệt đối xuyên phiên.
