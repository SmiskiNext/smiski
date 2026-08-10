# Đề bài kiểm thử

## Bài thực hành 1 — Kết nối, vượt NAT và chất lượng luồng Media

**NAT Traversal & QoS Testing**

Mô tả: Giả lập các môi trường mạng thực tế khác nhau (mạng nội bộ, mạng 4G di
động, mạng công ty có tường lửa chặn cổng UDP) để kiểm tra tính ổn định của giao
thức ICE và máy chủ STUN/TURN, đồng thời đo lường chất lượng truyền tải đa
phương tiện.

### TC-01 — Kết nối vượt NAT qua máy chủ TURN

Người dùng kết nối vào cuộc họp từ mạng LAN nghiêm ngặt (chặn toàn bộ cổng UDP
trực tiếp, bắt buộc relay qua TURN).

- Kết nối phòng họp thành công qua giao thức mã hóa.
- Thời gian thiết lập cuộc gọi (Call Setup Time) < 3 giây.
- Luồng audio và video hiển thị mượt mà.

### TC-02 — Chất lượng dịch vụ luồng Media (QoS)

Duy trì cuộc gọi video giữa 2 người dùng trong 15 phút và bật công cụ giám sát
luồng (WebRTC Internals).

- Độ trễ mạng một chiều (End-to-End Latency) < 200 ms.
- Tỷ lệ mất gói dữ liệu (Packet Loss Rate) < 2%.
- Chỉ số biến động độ trễ (Jitter) < 30 ms.

## Bài thực hành 2 — Tải phòng họp đồng thời và kiểm soát tài nguyên

**SFU Scalability & Stress Testing**

Mô tả: Sử dụng các công cụ giả lập (ví dụ: LiveKit Benchmarking tool hoặc các
script headless browser) để đẩy số lượng người dùng kết nối đồng thời vào hệ
thống nhằm đánh giá sức chịu tải của kiến trúc SFU và tầng lưu trữ Redis.

### TC-03 — Tải phòng họp tập trung (Room Capacity Test)

Giả lập 1 phòng họp lớn có 30 thành viên cùng tham gia, trong đó có 10 người bật
Camera (độ phân giải 720p) và 1 người chia sẻ màn hình.

- SFU LiveKit phân phối chính xác luồng dữ liệu mà không bị sập room.
- Băng thông phân phối trên máy chủ không bị nghẽn nghẹt (Bottleneck).
- Tỷ lệ lỗi kết nối hoặc rớt phòng họp = 0%.

### TC-04 — Hiệu năng sinh Token và đồng bộ trạng thái

Giả lập 500 yêu cầu kết nối và lấy Access Token cùng lúc (Concurrent Requests)
gửi tới Backend trong vòng 1 giây.

- Tỷ lệ phân phối Token thành công đạt 100%.
- Trạng thái phòng họp trên bộ nhớ đệm Redis được cập nhật chính xác, không bị
  xung đột dữ liệu.
- Thời gian phản hồi API sinh Token trung bình < 500 ms.

### TC-05 — Giám sát tài nguyên hạ tầng Docker dưới tải cao

Đo lường hiệu suất phần cứng của máy chủ chạy Docker trong suốt quá trình thực
hiện bài test tải TC-03 và TC-04.

- Mức chiếm dụng CPU của LiveKit Server Container < 80%.
- Mức chiếm dụng RAM của Redis Container < 256 MB (không bị rò rỉ bộ nhớ hoặc
  tràn cache).

## Dữ liệu thu thập

Trong quá trình thực nghiệm và vận hành hệ thống, cần trích xuất và phân tích
các nhóm số liệu sau để đưa vào báo cáo:

- **Thông số truyền tải mạng thời gian thực (WebRTC Metrics):** Băng thông tiêu
  thụ thực tế (Bitrate kbps cho cả chiều Up/Down), tần suất rớt khung hình
  (Frame Drop Rate), độ trễ âm thanh và hình ảnh — thu thập trực tiếp qua
  LiveKit Dashboard hoặc WebRTC statistics.
- **Chỉ số hiệu năng hạ tầng (Infrastructure Logs):** Biểu đồ đo lường CPU, RAM,
  Network I/O và Disk Read/Write của từng container độc lập (LiveKit Server,
  Coturn, Backend, Redis, PostgreSQL) — thu thập bằng Prometheus/Grafana hoặc
  Docker Stats.
- **Chỉ số nghiệp vụ tích hợp (Integration Metrics):** Tỷ lệ đồng bộ dữ liệu
  cuộc họp thành công về hệ thống quản lý dự án, thời gian lưu vết lịch sử cuộc
  họp, và log kiểm định bảo mật của luồng cấp phát mã Access Token.
