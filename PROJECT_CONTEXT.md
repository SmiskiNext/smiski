# PROJECT CONTEXT — Module Hội họp Trực tuyến tích hợp Jira (LiveKit)

> File này tổng hợp lại toàn bộ nội dung phân tích & thiết kế từ báo cáo thực tập
> **"Thiết kế và xây dựng Module Hội họp Trực tuyến tích hợp vào hệ thống quản lý dự án sử dụng LiveKit"**
> Mục đích: dùng làm **context nền** để Claude Code hiểu domain, kiến trúc, dữ liệu và
> đặc biệt là **cơ chế phân quyền**, phục vụ phát triển tính năng tiếp theo.

---

## 1. Tổng quan đề tài

Xây dựng một **module hội họp trực tuyến (video meeting)** có khả năng **tích hợp vào hệ
thống quản lý công việc/dự án** (điển hình là **Jira**, thông qua **Atlassian Forge**),
cho phép người dùng **tạo, lên lịch, tham gia, quản lý và xem lại lịch sử cuộc họp**
ngay trong ngữ cảnh của một **Issue** hoặc **Project**, thay vì phải dùng công cụ họp
rời rạc (Google Meet/Zoom/Teams).

- Nền tảng media thời gian thực: **LiveKit** (SFU – Selective Forwarding Unit, dựa trên WebRTC).
- Nền tảng tích hợp mẫu: **Jira Cloud** qua **Atlassian Forge** (Issue Panel + Project Page).
- Kiến trúc backend: **Microservices + Event-Driven Architecture**, mỗi service theo
  **Hexagonal Architecture (Ports & Adapters) + CQRS**.

### Phạm vi (in-scope)
- Thiết kế module hội họp có khả năng tích hợp (không chỉ Jira, hướng mở rộng).
- Tích hợp thực nghiệm vào Jira qua Forge: **Issue Panel** + **Project Page**.
- Trạng thái meeting: `Scheduled`, `Running`, `Completed`, `Canceled`.
- Cơ chế phân quyền qua **custom permission** của Jira: `View Meeting`, `Edit Meeting`.
- Meeting Room: audio, video, screen sharing, recording (ở mức thiết kế/demo).
- Lưu vết lịch sử cuộc họp + bản ghi (recording).
- Chính sách retention dữ liệu: **soft delete sau 15 ngày**, **hard delete sau 30 ngày** (ở tầng backend).
- Kiểm thử bằng mock data + giao diện demo.

### Ngoài phạm vi (out-of-scope)
- Phát hành chính thức lên Atlassian Marketplace.
- Tích hợp đầy đủ với Trello/Asana/ClickUp/GitHub Projects.
- Ứng dụng mobile hoàn chỉnh.
- Tự động sinh biên bản họp bằng AI, speech-to-text, phân tích nội dung cuộc họp.
- Đồng bộ nâng cao với Google Calendar/Outlook Calendar.
- Hệ thống thông báo đa kênh qua Slack/Teams/SMS (hiện chỉ có email).

---

## 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Backend | Java & Spring Boot (REST API, DI, Spring Data, Spring Security) |
| Frontend | ReactJS / NextJS (Forge Custom UI) |
| Media/RTC | LiveKit (SFU trên nền WebRTC), Coturn (TURN/STUN) |
| CSDL quan hệ | PostgreSQL |
| Cache/Realtime state | Valkey (tương thích Redis) |
| Event streaming | Apache Kafka + CloudEvents |
| API Gateway | Kong Gateway (reverse proxy, auth, rate limiting) |
| Object storage | RustFS (S3-compatible, lưu file ghi hình) |
| Containerization | Docker |
| Nền tảng tích hợp | Atlassian Forge (UI Kit / Custom UI), Jira REST API, `@forge/bridge` |

---

## 3. Actor / Đối tượng sử dụng hệ thống

| Actor | Vai trò |
|---|---|
| **Jira Admin / Site Admin** | Cấu hình ứng dụng, khai báo custom permission, quản lý permission scheme |
| **Project Administrator** | Quản lý thành viên trong project, gán user vào Project Role |
| **Meeting Manager / Host** | Người có quyền quản lý meeting: tạo, lên lịch, bắt đầu, chỉnh sửa, hủy, kết thúc, ghi hình |
| **Participant** | Người tham gia cuộc họp, xem thông tin, tham gia khi đang Running |
| **Developer / Tester-QA / Business Analyst** | Các vai trò nghiệp vụ tham gia họp theo ngữ cảnh công việc (map vào Participant hoặc Host tùy permission) |
| **Viewer** | Chỉ có quyền xem thông tin/lịch sử/bản ghi nếu được cấp quyền |

**Actor chính dùng trong Use Case (thu gọn còn 3 loại)**:
1. Jira Admin / Project Admin — cấu hình & phân quyền.
2. Meeting Manager / Host — quản lý meeting.
3. Participant — tham gia/xem meeting.

> ⚠️ Điểm quan trọng: **vai trò nghiệp vụ (Host/Participant) không đồng nhất với permission**.
> Một user là "Host" của 1 meeting (người tạo) nhưng quyền **thao tác thực tế được quyết định
> hoàn toàn bởi custom permission `Edit Meeting` / `View Meeting`** được cấu hình ở Jira
> permission scheme — xem Mục 4.

---

## 4. PHÂN QUYỀN (Authorization) — chi tiết quan trọng nhất

### 4.1. Mô hình phân quyền

Hệ thống **không tự định nghĩa role riêng** mà tận dụng **custom permission của Jira**
(khai báo qua `manifest.yml` của Forge app, gán vào Jira **Permission Scheme** của từng Project).
Quyền thao tác của user với module Meeting phụ thuộc vào **permission được cấp trong phạm vi Project
đó**, không phải vai trò họp (Host/Participant) đơn thuần.

### 4.2. Danh sách permission (chỉ có 2 quyền, phân cấp bao hàm)

| Permission | Ý nghĩa | Bao gồm |
|---|---|---|
| **View Meeting** | Xem thông tin cuộc họp, xem lịch sử meeting, xem chi tiết meeting, **tham gia (join)** meeting đang Running, xem recording nếu có | — |
| **Edit Meeting** | Tạo, lên lịch, bắt đầu, chỉnh sửa, hủy, kết thúc meeting, start/stop recording | **Bao gồm toàn bộ quyền của View Meeting** (superset) |

> Rule cứng: `Edit Meeting ⊇ View Meeting`. Không có role thứ 3, không có phân quyền
> theo mức field/attribute — chỉ 2 cấp: xem (View) và quản lý toàn phần (Edit).

### 4.3. Ma trận quyền theo chức năng

| Chức năng | View Meeting | Edit Meeting |
|---|---|---|
| Xem danh sách Meeting | ✅ | ✅ |
| Xem chi tiết Meeting | ✅ | ✅ |
| Xem lịch sử Meeting | ✅ | ✅ |
| Tham gia (Join) Meeting đang Running | ✅ | ✅ |
| Start Instant Meeting | ❌ | ✅ |
| Schedule Meeting | ❌ | ✅ |
| Manage Meeting (Edit/Cancel/End/Start Scheduled) | ❌ | ✅ |
| Start/Stop Recording | ❌ | ✅ |

### 4.4. Ma trận quyền theo Use Case

| Use Case | Permission yêu cầu |
|---|---|
| UC-01 Start Instant Meeting | `Edit Meeting` |
| UC-02 Schedule Meeting | `Edit Meeting` |
| UC-03 Start Scheduled Meeting | `Edit Meeting` |
| UC-04 Manage Meeting (Edit/Cancel/End) | `Edit Meeting` |
| UC-05 Join Meeting | `View Meeting` **hoặc** `Edit Meeting` |
| UC-06 View Meeting Detail | `View Meeting` **hoặc** `Edit Meeting` |
| UC-07 View Meeting History | `View Meeting` **hoặc** `Edit Meeting` |

### 4.5. Quy tắc hiển thị action theo TRẠNG THÁI meeting × PERMISSION

Đây là **rule quan trọng nhất cho UI/UX và cho backend validation**: action nào được phép
thực hiện phụ thuộc **đồng thời** vào (a) permission của user và (b) trạng thái hiện tại
của meeting.

| Meeting State | User có `View Meeting` | User có `Edit Meeting` |
|---|---|---|
| **Scheduled** | View Detail | View Detail, **Edit**, **Start**, **Cancel** |
| **Running** | **Join**, View Detail | **Join**, View Detail, **End** |
| **Completed** | View Detail | View Detail |
| **Canceled** | View Detail | View Detail |

Quy tắc suy ra:
- `Scheduled` → Edit Meeting: có thể **Edit** hoặc **Cancel** (không thể End vì chưa chạy).
- `Running` → Edit Meeting: chỉ có thể **End** (không Edit/Cancel một meeting đang chạy).
- `Completed` / `Canceled` → **immutable**: chỉ xem, không ai được sửa/xóa/đổi trạng thái nữa
  kể cả user có Edit Meeting. Không có thao tác xóa thủ công trên UI — dữ liệu được xử lý theo
  **chính sách retention** (soft delete 15 ngày → hard delete 30 ngày ở backend).
- `Edit Meeting` **không bắt buộc phải là Host** của meeting đó — bất kỳ user nào có
  permission `Edit Meeting` trong project đều có thể **End Meeting**, **Cancel**, **Edit**
  của bất kỳ meeting nào trong project (không giới hạn theo "chỉ Host mới sửa được").

### 4.6. Business rule đặc biệt liên quan phân quyền + state

1. **Một user chỉ được là Host của tối đa 1 meeting đang `Running` tại một thời điểm.**
   - Nếu user đang là Host của 1 meeting Running → không được `Start Instant Meeting` mới,
     và không được `Start Scheduled Meeting` khác cho tới khi kết thúc meeting hiện tại.
   - Thông báo lỗi mẫu: *"Vui lòng kết thúc cuộc họp đang diễn ra để tạo cuộc họp mới!"*
2. **Meeting Scheduled không chặn việc tạo Instant Meeting** — 1 Issue có thể có song song
   nhiều Scheduled + 1 Instant/Running.
3. **1 Issue có thể có nhiều meeting Scheduled** cùng lúc trong vòng đời xử lý.
4. **Không được lên lịch trùng thời gian bắt đầu với 1 meeting khác do chính user đó đã lên lịch trước đó**
   (kiểm tra theo phạm vi người tạo lịch, không phải toàn hệ thống).
   - Thông báo lỗi mẫu: *"Vui lòng không chọn thời gian bắt đầu cuộc họp trùng với thời gian
     bắt đầu cuộc họp đã lên lịch trước đó!"*
5. **Action không phù hợp permission hoặc trạng thái → ẩn hoàn toàn trên UI**, đồng thời
   **backend bắt buộc phải re-check permission & state trước khi xử lý nghiệp vụ**
   (không tin tưởng tuyệt đối vào việc ẩn UI — phòng chống thao tác trái quyền qua gọi API trực tiếp).
6. **Người dùng không tự xóa meeting thủ công** trên giao diện — vòng đời dữ liệu theo
   chính sách retention của hệ thống (xem Mục 6.2 & schema `deleted_at`/`purge_after`).

### 4.7. Gợi ý implement cho Claude Code khi phát triển thêm tính năng

- Mọi endpoint quản lý meeting (Create/Schedule/Start/Edit/Cancel/End/Start-Stop-Recording)
  phải guard bằng permission `Edit Meeting` ở tầng Application/Command layer, **không chỉ ở
  Frontend/Forge UI**.
- Mọi endpoint đọc (List/Detail/History) guard bằng `View Meeting OR Edit Meeting`.
- Endpoint `Join Meeting` guard bằng `View Meeting OR Edit Meeting` **+** kiểm tra
  `meeting.status == RUNNING`.
- Nên có 1 lớp **PermissionGuard/PolicyService** trung tâm nhận `(userId, projectId, requiredPermission)`
  trả về boolean, gọi Jira API (`context.permission` / Forge `permission API`) hoặc cache lại quyền
  theo project để tránh gọi API Jira liên tục.
- Nên có 1 lớp **MeetingStateGuard** kiểm tra `(currentStatus, requestedAction)` hợp lệ theo bảng
  ở Mục 4.5 trước khi cho phép transition trạng thái — tách biệt rõ 2 lớp kiểm tra
  (permission vs. state machine) để dễ mở rộng thêm action mới sau này.
- Khi thêm role/permission mới trong tương lai (vd. "Recorder-only", "Moderator" tách biệt
  khỏi Edit Meeting), cần thiết kế lại ma trận ở Mục 4.3–4.5 chứ không patch rời rạc.

---

## 5. Use Case chi tiết

| ID | Tên | Actor | Permission | Mô tả ngắn |
|---|---|---|---|---|
| UC-01 | Start Instant Meeting | Meeting Manager/Host | Edit Meeting | Tạo & bắt đầu họp ngay từ Issue Panel, meeting → `Running` ngay |
| UC-02 | Schedule Meeting | Meeting Manager/Host | Edit Meeting | Lên lịch họp tương lai, meeting → `Scheduled` |
| UC-03 | Start Scheduled Meeting | Meeting Manager/Host | Edit Meeting | Chuyển 1 meeting `Scheduled` → `Running` |
| UC-04 | Manage Meeting | Meeting Manager/Host | Edit Meeting | Edit / Cancel (Scheduled) hoặc End (Running) |
| UC-05 | Join Meeting | Host, Participant | View or Edit Meeting | Tham gia meeting đang `Running`, mở Meeting Room |
| UC-06 | View Meeting Detail | Host, Participant | View or Edit Meeting | Xem chi tiết meeting (mọi trạng thái), mở trong Dashboard stack |
| UC-07 | View Meeting History | Host, Participant | View or Edit Meeting | Xem lịch sử các meeting `Completed`/`Canceled` theo Issue hoặc Project |

### Điểm nghiệp vụ đáng chú ý từng UC

**UC-01 Start Instant Meeting**
- Precondition: đã login, có `Edit Meeting`, **không đang là Host của meeting Running khác**.
- Postcondition: meeting mới `Running`, người tạo = Host, các Scheduled cũ của Issue giữ nguyên,
  Issue Panel ưu tiên hiển thị meeting Running, điều hướng sang **Project Page – Meeting tab** để vào Meeting Room.

**UC-02 Schedule Meeting**
- Precondition: có `Edit Meeting`, thời gian lên lịch hợp lệ & không trùng với lịch khác của
  chính user đó.
- Cho phép tạo Scheduled dù Issue đang có Running (không xung đột vì là việc tương lai).
- Hiển thị ở Issue Panel (Upcoming) + Project Page Dashboard.

**UC-03 Start Scheduled Meeting**
- Chỉ áp dụng cho meeting đang `Scheduled`; nếu đã `Canceled` thì không cho start.
- Không được start nếu user hiện đang Host của 1 meeting `Running` khác.
- Ghi nhận thời gian bắt đầu thực tế (`actual start time`), chuyển `Scheduled → Running`.

**UC-04 Manage Meeting**
- `Edit` / `Cancel` chỉ áp dụng khi `Scheduled`.
- `End` chỉ áp dụng khi `Running`.
- `Completed`/`Canceled` → chỉ xem, không sửa.
- `Cancel`: `Scheduled → Canceled`, đưa vào History.
- `End`: `Running → Completed`, ghi nhận `endTime`, đưa vào History.
- Quyền `End Meeting` theo permission `Edit Meeting`, **không bắt buộc là Host**.

**UC-05 Join Meeting**
- Chỉ join được khi `Running`.
- `View Meeting` chỉ được join, **không** được quản lý (Edit/Cancel/End/Recording).

**UC-06 View Meeting Detail**
- Xem được ở mọi trạng thái (Scheduled/Running/Completed/Canceled).
- Hiển thị trong **Dashboard stack** (không mở trong Meeting Room tab).
- Meeting Room tab chỉ dùng khi thực sự **Join/Start** meeting (giao diện họp trực tuyến).

**UC-07 View Meeting History**
- Chỉ gồm meeting `Completed`/`Canceled`. `Scheduled`/`Running` không thuộc History.
- Phạm vi: theo Issue (mở từ Issue Panel) hoặc theo Project (mở từ Project Page Dashboard).
- Nếu meeting đã bị xóa theo retention policy → không còn hiển thị trong History.

---

## 6. Kiến trúc hệ thống

### 6.1. Tổng quan 4 lớp

```
Client Layer        : ReactJS/NextJS trên Forge (Issue Panel, Project Page)
        │
Gateway Layer        : Kong Gateway — entrypoint duy nhất, routing, auth, rate limit, logging
        │
Microservices Layer   : Tenant Service | Meet Service | Record Service | Notification Service
        │
Infrastructure Layer  : PostgreSQL | Valkey | Apache Kafka | RustFS | LiveKit (SFU)
```

- Kiến trúc tổng: **Microservices + Event-Driven Architecture**.
- Mỗi service nội bộ theo **Hexagonal Architecture (Ports & Adapters) + CQRS**
  (tách Domain Layer / Application Layer (Command/Query Use Cases) / Infrastructure Layer).
- Giao tiếp đồng bộ: REST API qua Gateway.
- Giao tiếp bất đồng bộ: Kafka + **Outbox pattern** (mỗi service có bảng `outbox_event` riêng
  để đảm bảo publish sự kiện tin cậy — transactional outbox).
- Client kết nối **trực tiếp tới LiveKit** để truyền media (không qua Gateway) theo WebRTC.

### 6.2. Tenant Service
**Mục đích**: quản lý vòng đời cài đặt/gỡ cài đặt app trên Jira (multi-tenant theo `cloudId`).

- Aggregate Root: `Tenant`
- Value Objects: `TenantId`, `AppId`, `InstallationId`
- Command Use Cases: `RegisterTenantUseCase`, `UninstallTenantUseCase`
- Infra: PostgreSQL Repository, Kafka Event Publisher

**Bảng `tenants`**: `tenant_id` (PK, = Jira cloudId), `installation_id` (unique), `app_id`,
`environment_type` (DEVELOPMENT/STAGING/PRODUCTION), `environment_id`, `site_url`,
`installer_account_id`, `app_version`, `status` (ACTIVE/SUSPENDED/UNINSTALLED),
`installed_at`, `updated_at`, `uninstalled_at`, `purge_after`.

**Bảng `outbox_event`** (mẫu dùng chung ở mọi service): `tenant_id`, `id` (uuidv7, PK cùng tenant_id),
`aggregate_id`, `aggregate_type`, `event_type`, `topic`, `payload`, `created_at`, `published_at`,
`retry_count`, `last_error`.

### 6.3. Meet Service (service trung tâm)
**Mục đích**: quản lý toàn bộ vòng đời meeting — tạo, lên lịch, participant, join request,
invite, đồng bộ trạng thái real-time; điều phối Notification/Record/LiveKit.

**Chức năng chính**:
- Quản lý cuộc họp: tạo tức thì, lên lịch, cập nhật, hủy, kết thúc, tìm kiếm/tra cứu.
- Quản lý người tham gia: join/leave, danh sách, loại bỏ participant, quản lý role Host/Participant.
- Quản lý join request: gửi/duyệt/từ chối yêu cầu tham gia, theo dõi realtime.
- Quản lý lời mời (invite): gửi lời mời, danh sách người được mời, xác thực link, theo dõi trạng thái.
- Thông báo realtime: **Server-Sent Events (SSE)**, đồng bộ trạng thái meeting, thông báo duyệt join request.

**Domain Layer**:
- Aggregate Roots: `Meeting`, `ParticipationLog`, `MeetingInvitee`, `InviteToken`, `JoinRequest`
- Value Objects: `MeetingSettings`, `ShortCode`, `LiveKitIdentity`, `LiveKitRoomName`, `MeetingTimeRange`

**Application Layer (CQRS)**:
- Commands: `ScheduleMeetingUseCase`, `CreateInstantMeetingUseCase`, `CancelMeetingUseCase`,
  `EndMeetingUseCase`, `RequestJoinUseCase`, `ApproveJoinRequestUseCase`, `AddInviteeUseCase`
- Queries: `GetMeetingUseCase`, `GetHostMeetingsUseCase`, `GetParticipantsUseCase`,
  `GetJoinRequestsUseCase`, `GetMeetingRecordingsUseCase`

**Infrastructure Layer**: PostgreSQL Adapter, Redis(Valkey) Adapter, Kafka Event Publisher,
LiveKit Adapter, User Service gRPC Client, SSE Manager, Security Components, Scheduled Jobs.

#### Bảng dữ liệu Meet Service

**`tenants`** (bản sao rút gọn dùng nội bộ Meet Service): `tenant_id` (PK), `cloud_id`,
`status`, `updated_at`, `uninstalled_at`, `purge_after`.

**`meetings`**:
| Cột | Kiểu | Ghi chú |
|---|---|---|
| tenant_id | VARCHAR(255) NOT NULL | khóa phân mảnh, Jira cloudId |
| id | UUID (uuidv7) | PK cùng tenant_id |
| host_id | VARCHAR(128) NOT NULL | Jira accountId của Host |
| short_code | VARCHAR(15) NOT NULL | mã ngắn tham gia phòng |
| issue_id / issue_key / project_key | VARCHAR | ngữ cảnh Jira gắn với meeting |
| title, description | | |
| start_time, end_time | TIMESTAMPTZ | dự kiến |
| type | INSTANT / SCHEDULED | |
| status | SCHEDULED / LIVE / ENDED / CANCELLED | default SCHEDULED |
| settings | JSONB | cấu hình cuộc họp |
| deleted_at, deleted_by, purge_after | | soft/hard delete |
| created_at | | |

> Lưu ý: trạng thái lưu trong DB dùng `SCHEDULED/LIVE/ENDED/CANCELLED`, trong khi phần
> đặc tả nghiệp vụ (Mục 4, 5) dùng thuật ngữ `Scheduled/Running/Completed/Canceled` — 
> **map 1-1**: `LIVE ≈ Running`, `ENDED ≈ Completed`. Khi code cần thống nhất 1 enum/naming,
> nên chọn 1 bộ và document rõ mapping.

Ràng buộc: `pk_meetings (tenant_id, id)`, `fk_meetings_tenant → tenants(tenant_id)`,
`chk_meetings_type IN ('INSTANT','SCHEDULED')`.

**`participation_logs`**: `tenant_id`, `id` (uuidv7 PK cùng tenant_id), `meeting_id` (FK meetings),
`account_id` (Jira accountId, bắt buộc — không hỗ trợ guest), `display_name`,
`display_name_cached_at`, `role` (HOST/PARTICIPANT), `livekit_identity` (JWT sub claim),
`livekit_participant_sid` (gán khi webhook `participant_joined`), `joined_at`, `left_at`,
`close_reason` (LEFT/SUPERSEDED).
Ràng buộc đáng chú ý: unique partial `(tenant_id, meeting_id, livekit_identity) WHERE left_at IS NULL`
và `(tenant_id, livekit_participant_sid) WHERE left_at IS NULL AND sid IS NOT NULL` — đảm bảo
**1 identity chỉ active 1 phiên tham gia tại 1 thời điểm** trong 1 meeting.

**`meeting_invitees`**: `tenant_id`, `id`, `meeting_id` (FK), `inviter_id`, `account_id` (nullable
— chưa resolve), `email`, `display_name`, `status` (PENDING/ACCEPTED/DECLINED), `invite_token_id` (FK),
`invited_at`, `responded_at`. Unique `(tenant_id, meeting_id, email)`.

**`invite_tokens`**: `tenant_id`, `id`, `meeting_id` (FK), `invitee_id` (FK), `token_hash`,
`status` (PENDING/USED/REVOKED/EXPIRED), `expires_at`, `created_at`, `updated_at`.
Unique `(tenant_id, token_hash)`.

**`outbox_event`**: giống mẫu chung (Mục 6.2).

### 6.4. Record Service
**Mục đích**: quản lý vòng đời bản ghi (recording) — tạo, sửa metadata, xóa, lấy danh sách.
Nhận sự kiện từ **LiveKit Egress**.

**Chức năng**: tạo bản ghi (nhận event từ Meet Service, publish event), sửa metadata
(title, notes), xóa 1/nhiều bản ghi, lấy danh sách bản ghi theo meeting.

**Domain**: Aggregate `Record`; VOs `RecordId`, `RecordTitle`.
**Application (CQRS)**: Commands `StartRecordUseCase`, `EndRecordUseCase`,
`UpdateRecordMetaDataUseCase`, `DeleteRecordUseCase`; Query `GetRecordUseCase`.
**Infra**: PostgreSQL Adapter, **RustFS Adapter** (object storage S3-compatible), Kafka Publisher,
LiveKit Adapter.

#### Bảng dữ liệu Record Service
**`tenants`**: giống mẫu chung.

**`recordings`**:
| Cột | Ghi chú |
|---|---|
| tenant_id, id (uuidv7 PK cùng tenant_id) | |
| meeting_id | UUID thuần, **không FK** (loose coupling với Meet Service) |
| livekit_egress_id | unique theo tenant |
| livekit_room_name | |
| file_url, thumbnail_url, storage_path | vị trí lưu trên RustFS |
| status | PENDING / RECORDING / COMPLETED / FAILED |
| title, notes | user-editable |
| deleted_at, deleted_by, purge_after | soft/hard delete |
| edited_by, edited_at | |
| started_at, ended_at, duration_seconds, file_size_bytes | |
| error_message | khi FAILED |
| created_at | |

Ràng buộc đáng chú ý: `uq_recordings_active_per_meeting` — unique partial
`(tenant_id, meeting_id) WHERE status IN ('PENDING','RECORDING')` → **1 meeting chỉ có tối đa
1 recording đang active tại 1 thời điểm**.

**`outbox_event`**: giống mẫu chung.

### 6.5. Notification Service
**Mục đích**: gửi email cho các sự kiện nghiệp vụ (event-driven, **không lưu trữ dữ liệu**,
chỉ consume Kafka rồi gửi mail).

**Chức năng**: gửi email mời tham gia meeting, gửi email hủy meeting, gửi email cập nhật
link tham gia (khi link đổi), thông báo trạng thái duyệt/từ chối join request.

**Domain**: chỉ có `EmailSender` Port.
**Application**: `SendMeetingInvitationEmailUseCase`, `SendMeetingCancelledEmailUseCase`,
`SendInviteUpdatedEmailUseCase`, `SendParticipantAcceptedUseCase`, `SendParticipantDeniedUseCase`.
**Infra**: Kafka Consumers, Email Sender, Email Template Renderers, Link Generator,
Notification Configuration.

### 6.6. Luồng xử lý tổng quát
1. Client (Web/Forge) gửi request.
2. Kong Gateway tiếp nhận, xác thực, định tuyến tới microservice tương ứng.
3. Microservice xử lý nghiệp vụ, ghi PostgreSQL/Valkey.
4. Nếu phát sinh sự kiện cần giao tiếp bất đồng bộ → ghi vào `outbox_event` → publish lên Kafka.
5. Service khác consume Kafka event tương ứng và xử lý (vd. Notification Service gửi mail
   khi Meet Service phát sự kiện mời họp).
6. Kết quả trả về client qua Gateway.
7. Riêng phần media: client **kết nối trực tiếp LiveKit** (không qua Gateway) để truyền
   audio/video/screen-share theo WebRTC.

---

## 7. Cơ sở lý thuyết liên quan (tóm tắt kỹ thuật nền)

- **WebRTC**: `getUserMedia`, `RTCPeerConnection`, `RTCDataChannel`; cần signaling riêng
  (không chuẩn hóa sẵn), dùng SDP.
- **NAT traversal**: ICE điều phối, dùng candidate loại Host / Server-reflexive (qua STUN) /
  Relayed (qua TURN). TURN chỉ dùng khi không thể kết nối trực tiếp (tăng độ trễ & chi phí).
- **Kiến trúc truyền media nhiều người**: so sánh Mesh (đơn giản, không scale) vs MCU
  (server tải cao, trộn media) vs **SFU** (server forward track chọn lọc, scale tốt, độ trễ thấp
  — **đây là kiến trúc LiveKit sử dụng**).
- **LiveKit model**: `Room` (không gian họp), `Participant` (user/device/service), `Track`
  (luồng audio/video/screen-share/data). Có SDK đa nền tảng, Server API, Webhook, **Egress**
  (dùng để ghi hình/export — chính là nguồn sự kiện cho Record Service).
- **Atlassian Forge module**: khai báo trong `manifest.yml`; mỗi module chạy sandbox riêng,
  giao tiếp Jira qua `@forge/bridge` / Jira REST API / **Forge Remote** (khi cần gọi backend
  tự triển khai ngoài Forge — đây chính là cách Forge App gọi vào Meet/Record/Notification Service).

---

## 8. Những điểm cần làm rõ / rủi ro khi phát triển tiếp (gaps trong báo cáo gốc)

- Báo cáo dùng 2 bộ thuật ngữ trạng thái khác nhau (nghiệp vụ: Scheduled/Running/Completed/Canceled;
  schema DB: SCHEDULED/LIVE/ENDED/CANCELLED) — cần chốt 1 enum chuẩn trước khi code.
- Ma trận quyền chỉ có 2 permission (`View`/`Edit`) — nếu sau này cần vai trò trung gian
  (vd. Moderator có thể mute người khác nhưng không được Cancel/Delete meeting), cần thiết kế
  thêm permission mới và cập nhật lại toàn bộ ma trận ở Mục 4.
- Chương 4 (triển khai/kiểm thử) và Chương 5 (kết quả/tổng kết) trong báo cáo gốc mới chỉ có
  **đề mục khung**, chưa có nội dung/số liệu thực tế — không dùng làm căn cứ kỹ thuật, chỉ
  dùng làm checklist các đầu việc test cần làm (NAT/TURN, chất lượng media, tải phòng họp,
  sinh Access Token, giám sát tài nguyên Docker).
- Thiết kế giao diện (Mục 3.8) và use case diagram tổng quan (Hình 3.1) chỉ là placeholder
  hình ảnh trong docx gốc, không có mô tả text kèm theo — cần bổ sung riêng nếu cần.
- `recordings.meeting_id` **không có FK** sang bảng `meetings` (khác service, khác DB) —
  tính toàn vẹn tham chiếu phải được đảm bảo ở tầng application/event, không phải DB constraint.

---

## 9. Tóm tắt nhanh cho Claude Code (TL;DR)

- Domain: **video-meeting module tích hợp Jira**, backend Java/Spring Boot, 4 microservice
  (Tenant, Meet, Record, Notification), Hexagonal + CQRS + Event-Driven (Kafka + Outbox),
  media qua LiveKit (SFU), multi-tenant theo Jira `cloudId`.
- **Chỉ 2 permission**: `View Meeting` (xem + join) và `Edit Meeting` (toàn quyền quản lý,
  superset của View). Không có permission thứ 3.
- **Quyền hiển thị/thực hiện action = f(permission, meeting.status)** — luôn tra bảng ở Mục 4.5
  trước khi thêm action mới.
- Meeting states: `Scheduled → Running → Completed`, hoặc `Scheduled → Canceled`.
  `Completed`/`Canceled` là **terminal & immutable** (chỉ xem).
- 1 user chỉ được làm Host của **tối đa 1 meeting Running** cùng lúc.
- Mọi kiểm tra quyền/state phải **enforce lại ở backend**, không tin vào việc ẩn nút trên UI.
