# ROADMAP — Từ Zero Meeting System (ZMS) đến Module Hội họp tích hợp Jira

> Tài liệu này phân tích khoảng cách giữa **dự án hiện tại (ZMS — nền tảng họp
> đa năng, độc lập)** và **đích đến trong `BA.md` (Module Hội họp Trực tuyến
> nhúng trong Jira qua Forge, gắn cuộc họp với Issue)**, rồi trình bày lộ trình
> theo **phase/milestone kỹ thuật**.

---

## 1. Bối cảnh

|                  | Hiện tại (ZMS)                                  | Đích đến (BA.md)                                         |
| ---------------- | ----------------------------------------------- | -------------------------------------------------------- |
| Hình thái        | App họp độc lập (web + Android)                 | Module nhúng trong Jira qua Forge App                    |
| Danh tính        | Đăng nhập riêng (JWT + Firebase), Kong validate | Lấy context người dùng từ Jira (không tự login)          |
| Đơn vị nghiệp vụ | Meeting có `hostId`, `shortCode`                | Meeting **gắn với Jira Issue** (UC01, 02, 07)            |
| Phạm vi          | chat, recording, notification email, Android    | Chỉ họp + liên kết Issue; phần còn lại **ngoài phạm vi** |
| Hạ tầng media    | LiveKit + RustFS                                | LiveKit (giữ), RustFS/recording bỏ                       |

**Quyết định nền (đã chốt với chủ đề tài):**

1. **Tái sử dụng `meeting-management`** làm bộ não nghiệp vụ; bổ sung khái niệm
   `issueId` và xây Forge App làm lớp giao diện.
2. **Gỡ hẳn khỏi monorepo**: `chat-management`, `notification`, tính năng
   recording (RustFS), và `frontends/android-app` — đều ngoài phạm vi BA.
3. Trình bày theo **phase kỹ thuật** (không bám tuần lịch).
4. **Định hướng `user-management` để mở** — ghi 2 phương án, quyết sau khi spike
   auth (xem Phase 2).

---

## 2. Phân tích khoảng cách (Gap Analysis)

### 2.1 Liên kết Issue — hạng mục cốt lõi, hoàn toàn mới

- `Meeting` aggregate hiện **không có** trường tham chiếu Issue
  (`services/meeting-management/.../domain/model/Meeting.java`).
- Cần thêm value object `JiraIssueRef` (issueKey, issueId, projectKey, siteUrl)
  và gắn vào `Meeting`, kèm migration Flyway + cập nhật repository/query.
- UC07 (truy vết theo Issue) cần endpoint "list meetings by issue" và index DB.

### 2.2 Forge App — tầng UI mới

- Chưa tồn tại. Cần một Forge app (Custom UI) hiển thị panel trong Issue: nút
  "Tạo cuộc họp", danh sách cuộc họp của Issue, nút "Tham gia".
- Forge app gọi Meeting Service qua HTTP (Forge `fetch` + remote backend).

### 2.3 Danh tính / Auth — cầu nối thay cho login riêng

- `HeaderAuthFilter` hiện tin header `X-User-ID` do **Kong** tiêm sau khi
  validate JWT (`infrastructure/security/HeaderAuthFilter.java`).
- `UserGrpcServicePort` gọi gRPC sang `user-management` để **resolve display
  name / avatar / email** của participant và invitee — đây là phụ thuộc **ngoài
  login**, nên không thể bỏ user-management một cách ngây thơ.
- BA loại trừ "Atlassian OAuth Migration" ⇒ chỉ làm cầu nối danh tính, không làm
  OAuth đầy đủ.

### 2.4 Phạm vi cần gỡ

- `chat-management` (MongoDB), `notification` (Resend), recording (RustFS),
  `android-app`. Gỡ kéo theo: settings.gradle, Kafka consumer, k8s manifests,
  openapi pipeline, docker-compose, CI.

### 2.5 Mapping Use Case → trạng thái hiện tại

| UC   | Mô tả               | Hiện trạng                              | Việc cần làm                     |
| ---- | ------------------- | --------------------------------------- | -------------------------------- |
| UC01 | Tạo họp từ Issue    | Có `CreateInstantMeetingUseCase`        | Thêm `issueId` vào lệnh tạo      |
| UC02 | DS họp theo Issue   | Chưa có query theo issue                | Thêm query + index               |
| UC03 | Tham gia từ Jira    | LiveKit token đã có                     | Gọi từ Forge UI                  |
| UC04 | DS người tham gia   | `ParticipationLog` đã có                | Hiển thị trong Forge             |
| UC05 | Kết thúc họp        | `Meeting.end()` đã có                   | Nối nút trong Forge              |
| UC06 | Lịch sử họp         | `add-web-meeting-history` đã xong (web) | Port sang Forge + lọc theo issue |
| UC07 | Liên kết & truy vết | **Chưa có**                             | Hạng mục trung tâm Phase 1       |

---

## 3. Lộ trình theo Phase

### Phase 0 — Cắt gọn phạm vi (Scope Reduction)

**Mục tiêu:** monorepo chỉ còn `meeting-management` + `user-management` +
`shared` + `proto`, build xanh.

- Gỡ `chat-management`, `notification` khỏi `settings.gradle.kts` và xóa module.
- Gỡ tính năng recording (RustFS) khỏi `meeting-management`.
- Gỡ `frontends/android-app`.
- Dọn Kafka topic/consumer, k8s manifest, docker-compose, CI workflow, openapi
  pipeline tham chiếu các service đã xóa.
- **Tiêu chí hoàn thành:** `./services/gradlew build` xanh; openapi unified
  build lại không lỗi.

### Phase 1 — Mô hình hóa liên kết Issue (Core Domain)

**Mục tiêu:** một cuộc họp có thể gắn và truy vết theo một Jira Issue (UC01,
UC02, UC07).

- Thêm value object `JiraIssueRef` và trường tham chiếu vào `Meeting`
  aggregate + factory methods.
- Migration Flyway thêm cột + index `(issue_key)`.
- Mở rộng `CreateInstantMeetingUseCase` / `ScheduleMeetingUseCase` nhận
  `issueRef`.
- Thêm query "list meetings by issue" (repository + use case + endpoint).
- Cập nhật OpenAPI + SDK.
- **Tiêu chí:** tạo họp kèm issueKey, truy vấn danh sách họp theo issueKey, test
  unit + integration cho domain và query.

### Phase 2 — Cầu nối danh tính (Auth Bridge) — _spike trước, chốt sau_

**Mục tiêu:** người dùng đã đăng nhập Jira không phải đăng nhập lại; backend
biết "ai" đang gọi.

Hai phương án để mở (quyết sau spike):

- **Phương án A — Giữ `user-management`, đổi vai (đề xuất):**
    - Bỏ luồng login UI; thêm endpoint provision/map theo `accountId` Jira
      (just-in-time provisioning).
    - Giữ nguyên gRPC resolve display name/avatar.
    - Kong/Spring Security Resource Server validate token Forge thay JWT tự ký.
    - _Ưu:_ ít vỡ nhất, giữ được resolve participant. _Nhược:_ vẫn nuôi 1
      service.
- **Phương án B — Gỡ hẳn `user-management`, snapshot danh tính từ Jira:**
    - Nhúng displayName/avatar vào token/payload từ Forge.
    - Bỏ `UserGrpcServicePort`, lưu snapshot tên/avatar trực tiếp trên
      `ParticipationLog` / invitee.
    - _Ưu:_ monorepo gọn đúng BA. _Nhược:_ refactor gRPC + mọi chỗ resolve user.

- **Spike (chốt phương án):** dựng Forge app tối thiểu, xác minh cách lấy
  `accountId` + token và cách backend verify. Sau spike, cập nhật phase này
  thành phương án đã chọn.
- **Tiêu chí:** request từ Forge tới Meeting Service được xác thực; principal là
  người dùng Jira; participant hiển thị đúng tên.

### Phase 3 — Forge App (Tầng tích hợp Jira)

**Mục tiêu:** giao diện họp nằm trong Issue panel của Jira (UC01–UC06).

- Khởi tạo Forge app (Custom UI) + manifest (issue panel module).
- Panel: nút tạo họp (gắn issueKey hiện tại), danh sách họp của Issue, trạng
  thái, nút tham gia/kết thúc, danh sách participant, lịch sử (UC06).
- Forge `fetch` → Meeting Service (kèm token từ Phase 2).
- **Tiêu chí:** trong Jira dev site, tạo/tham gia/kết thúc/xem lịch sử họp ngay
  trên Issue.

### Phase 4 — Tích hợp LiveKit trong luồng Forge (UC03)

**Mục tiêu:** Audio/Video/Screen share chạy được khi join từ Jira.

- Tái dùng `LiveKitAdapter` / `LiveKitPort` cấp token theo room name từ
  meetingId.
- Client LiveKit nhúng trong Forge Custom UI (hoặc mở tab web client tối giản
  nếu Forge sandbox chặn WebRTC — xác minh trong Phase 3 spike).
- **Tiêu chí:** 2 người join cùng phòng từ Jira, thấy/nghe nhau, share màn hình.

### Phase 5 — Triển khai & Đánh giá (Docker)

**Mục tiêu:** đóng gói Docker, demo end-to-end, đáp ứng mục 8–9 BA.

- docker-compose tinh gọn: Meeting Service + Postgres + LiveKit + Redis (+
  user-management nếu chọn Phương án A).
- Hướng dẫn deploy + script seed dữ liệu demo.
- Kiểm thử tích hợp end-to-end theo UC01–UC07.
- **Tiêu chí:** `docker compose up` chạy toàn hệ; demo full luồng từ Jira Issue.

---

## 4. Phụ thuộc giữa các phase

```text
Phase 0 (cắt gọn)
   │
   ▼
Phase 1 (Issue link) ──┐
   │                   │
   ▼                   ▼
Phase 2 (Auth spike) ─► Phase 3 (Forge UI) ─► Phase 4 (LiveKit) ─► Phase 5 (Docker/Demo)
```

- Phase 1 và Phase 2 có thể chạy song song một phần (domain vs auth spike).
- Phase 3 cần kết quả chốt của Phase 2.

---

## 5. Rủi ro & điểm cần xác minh sớm

- **WebRTC trong Forge sandbox:** Forge Custom UI có giới hạn iframe/CSP — cần
  spike sớm (Phase 3) xem LiveKit chạy trực tiếp được không, hay phải mở web
  client phụ.
- **Quyết định auth (Phase 2)** ảnh hưởng dây chuyền tới resolve participant và
  số service phải nuôi — ưu tiên spike trước khi code sâu.
- **Gỡ service (Phase 0)** có thể làm vỡ Kafka/k8s/CI — làm trước, kiểm tra
  build kỹ trước khi sang Phase 1.
- **Ngoài phạm vi (giữ nguyên theo BA):** AI summary, recording, chat sync,
  mobile, Jira notification, marketplace, OAuth migration, calendar, email
  invite, multi-tenant, LiveKit cluster.
