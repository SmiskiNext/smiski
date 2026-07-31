# API Specification — Cancel Meeting, End Meeting, Screen-Share Field

> **Trạng thái:** Đặc tả này được viết **sau khi code đã xong**, tự suy ra từ
> code + convention có sẵn của `meet` — **không** dựa trên một spec/proposal
> đã được duyệt trước (repo chưa có `openspec/changes/.../cancel-meeting`,
> `.../end-meeting`, `.../screen-share`). Coi đây là tài liệu để review, không
> phải "sự thật đã chốt".

Service: `meet` · Base path: `/api/{version}` (hiện `version = 1`) · Định
nghĩa tại `services/meet/src/main/java/io/github/smiskinext/meet/presentation/MeetingController.java`

> **⚠️ Known gap — Authorization:** `PROJECT_CONTEXT.md` §4.5/§4.7/UC-04 quy
> định quyền `cancel`/`end` là permission Jira **`Edit Meeting`** (bất kỳ
> project member nào có quyền đó, không giới hạn Host). Cả 2 endpoint dưới
> đây hiện chỉ check **Host** (`hostId.equals(accountId)`), giống hệt pattern
> đã có sẵn ở `update`/`delete` — vì backend `meet` **hiện chưa có cơ chế
> check permission Jira nào cả** (xác nhận trong `app/AGENTS.md`, mục
> "Backend permission enforcement (not yet built)"). Quyết định (2026-07-31):
> giữ nguyên Host-only, không vá riêng lẻ cho 2 API này — sẽ sửa cùng lúc với
> `update`/`delete` khi backend permission enforcement được xây dựng.
> Đánh dấu `TODO(permission)` trong `CancelMeetingApplicationService`/
> `EndMeetingApplicationService`.

## Quy ước chung

- **Auth header** (bắt buộc mọi request): `X-Account-Id` — thiếu thì trả
  `400` `application/problem+json`, `code: VALIDATION_ERROR`. Tenant xác định
  qua `X-Tenant-ID` (đọc bởi `TenantContext`, không kiểm tra ở tầng
  controller — thiếu tenant có thể gây lỗi tầng dưới, không nằm trong 2 API
  này).
- **Không có JSend/envelope.** Response thành công là raw JSON object của
  DTO. Lỗi luôn là RFC 7807 `application/problem+json`:

  ```json
  {
    "type": "about:blank",
    "title": "<tiêu đề ngắn>",
    "status": <http-status>,
    "detail": "<mô tả người đọc được>",
    "code": "<MACHINE_READABLE_CODE>",
    "traceId": "<hex trace id>"
  }
  ```

- **Action endpoint** dùng suffix `:verb` trên resource, luôn là `POST`.

---

## 1. `POST /meetings/{id}:cancel`

Huỷ một meeting **chưa bắt đầu** (`SCHEDULED`), với tư cách host. Không cho
huỷ meeting đang `RUNNING`/`COMPLETED`/đã `CANCELED`.

### Request

| | |
|---|---|
| Method | `POST` |
| Path | `/api/1/meetings/{id}:cancel` |
| Path param | `id` — UUID của meeting |
| Headers | `X-Account-Id` (required), `X-Tenant-ID` |
| Body | **Không có** (giống `DELETE /meetings/{id}`) |

Không nhận `reason` từ client — reason luôn là `HOST_CANCELED` (giá trị
`NO_SHOW` trong domain được để dành cho một luồng tự động khác, chưa có ở
đây).

### Response `200 OK`

```json
{
  "meeting": {
    "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
    "hostId": "712020:abcd-...",
    "shortCode": "abc-defg-hij",
    "type": "SCHEDULED",
    "status": "CANCELED",
    "title": "Sprint planning",
    "description": "Plan the next sprint",
    "issueLink": {
      "issueId": "10001",
      "issueKey": "PROJ-1",
      "projectKey": "PROJ"
    },
    "settings": {
      "admissionPolicy": "MANUAL_APPROVAL",
      "maxParticipants": 50,
      "allowScreenShare": true,
      "chatEnabled": true,
      "allowMicrophone": true,
      "allowVideo": true
    },
    "startTime": "2025-02-01T14:00:00Z",
    "endTime": "2025-02-01T15:00:00Z",
    "zoneId": "UTC",
    "organizerEmail": "host@example.com",
    "organizerDisplayName": "Host User",
    "calendarUid": "6d3e5f1a-...",
    "calendarSequence": 0,
    "createdAt": "2025-01-15T10:30:00Z",
    "cancelReason": "HOST_CANCELED",
    "canceledAt": "2025-02-01T13:00:00Z"
  }
}
```

| Field | Type | Ghi chú |
|---|---|---|
| `startTime`, `endTime` | `string \| null` | `null` nếu là INSTANT meeting chưa từng có time range |
| `status` | `string` | luôn là `"CANCELED"` khi trả 200 |
| `cancelReason` | `string` | luôn là `"HOST_CANCELED"` qua endpoint này |
| `canceledAt` | `string` (ISO instant) | thời điểm server xử lý huỷ |

### Lỗi

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | thiếu header `X-Account-Id` |
| 403 | `NOT_OWNER` | account gọi không phải host của meeting ⚠️ *(chưa phải check permission `Edit Meeting` như spec yêu cầu — xem cảnh báo đầu file)* |
| 404 | `MEETING_NOT_FOUND` | meeting không tồn tại / đã bị xoá mềm |
| 409 | `INVALID_STATUS_TRANSITION` | meeting không ở trạng thái `SCHEDULED` (đang `RUNNING`, đã `COMPLETED`, hoặc đã `CANCELED`) |

### Side effect

- Publish domain event `meeting.canceled` (Kafka topic `meet.meeting.canceled`,
  proto `io.github.smiskinext.event.meet.v1.MeetingCanceled`, CloudEvents type
  `io.github.smiskinext.meet.meeting.canceled.v1`) — mang theo snapshot
  invitee để service khác (dự kiến: notification) gửi email báo huỷ. Hiện
  **chưa có consumer nào** cho topic này trong repo.

---

## 2. `POST /meetings/{id}:end`

Kết thúc một meeting **đang chạy** (`RUNNING`) ngay lập tức, với tư cách
host. Khác với việc chờ LiveKit tự báo `room_finished`, endpoint này trả về
`COMPLETED` **đồng bộ** trong chính response.

### Request

| | |
|---|---|
| Method | `POST` |
| Path | `/api/1/meetings/{id}:end` |
| Path param | `id` — UUID của meeting |
| Headers | `X-Account-Id` (required), `X-Tenant-ID` |
| Body | **Không có** |

### Response `200 OK`

```json
{
  "meeting": {
    "id": "0195e0c2-8f3a-7c21-b9d4-2f1a6e7c8d90",
    "hostId": "712020:abcd-...",
    "shortCode": "abc-defg-hij",
    "type": "INSTANT",
    "status": "COMPLETED",
    "title": "Sprint planning",
    "description": "Plan the next sprint",
    "issueLink": {
      "issueId": "10001",
      "issueKey": "PROJ-1",
      "projectKey": "PROJ"
    },
    "settings": {
      "admissionPolicy": "MANUAL_APPROVAL",
      "maxParticipants": 50,
      "allowScreenShare": true,
      "chatEnabled": true,
      "allowMicrophone": true,
      "allowVideo": true
    },
    "startTime": null,
    "endTime": "2025-02-01T14:32:10Z",
    "zoneId": "UTC",
    "organizerEmail": "host@example.com",
    "organizerDisplayName": "Host User",
    "calendarUid": "6d3e5f1a-...",
    "calendarSequence": 0,
    "createdAt": "2025-02-01T14:00:00Z"
  }
}
```

| Field | Type | Ghi chú |
|---|---|---|
| `startTime` | `string \| null` | `null` cho INSTANT meeting |
| `endTime` | `string` | **luôn có giá trị** (không nullable) — khác `cancel`, vì `end` chỉ trả 200 khi đã thật sự COMPLETED |
| `status` | `string` | luôn là `"COMPLETED"` khi trả 200 |

Không có field `cancelReason`/`canceledAt` (không áp dụng cho luồng end).

### Lỗi

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | thiếu header `X-Account-Id` |
| 403 | `NOT_OWNER` | account gọi không phải host ⚠️ *(chưa phải check permission `Edit Meeting` như spec yêu cầu — xem cảnh báo đầu file)* |
| 404 | `MEETING_NOT_FOUND` | meeting không tồn tại / đã bị xoá mềm |
| 409 | `MEETING_NOT_RUNNING` | meeting chưa `RUNNING` (còn `SCHEDULED`) hoặc đã kết thúc/huỷ trước đó |

### Side effect

- Đóng mọi `ParticipationLog` đang active (set `leftAt`), publish
  `meeting.completed` (Kafka topic `meet.meeting.completed`, đã tồn tại từ
  trước — endpoint này chỉ thêm một cách khác để trigger event tương tự,
  không phải event mới).
- **Bất đồng bộ, best-effort:** gọi LiveKit `DeleteRoom` để đóng room + kick
  người còn lại. Nếu lỗi (LiveKit down...) chỉ log, **không** ảnh hưởng response
  200 đã trả — response phản ánh trạng thái Postgres, không phụ thuộc kết quả
  gọi LiveKit.

---

## 3. `GET /meetings/{id}` — thêm field `screenSharing`

Endpoint đã tồn tại, **không đổi path/method/lỗi**. Chỉ thêm một field mới
trong mỗi phần tử của mảng `participants[]`:

```json
{
  "meeting": { "...": "không đổi" },
  "invitees": [ "...": "không đổi" ],
  "participants": [
    {
      "accountId": "712020:abcd-...",
      "role": "PARTICIPANT",
      "joinedAt": "2025-02-01T14:01:00Z",
      "leftAt": null,
      "screenSharing": true
    }
  ]
}
```

| Field | Type | Ghi chú |
|---|---|---|
| `screenSharing` | `boolean` | **mới.** `true` nếu account này đang share màn hình **tại thời điểm gọi API**. Luôn `false` nếu `leftAt != null` (đã rời phòng thì không thể đang share). Đọc trực tiếp từ Redis (`ScreenShareStateRepository`), không phải cột DB. |

Đây là thay đổi **additive** — client cũ không đọc field này vẫn hoạt động
bình thường, không có field/response nào bị xoá hay đổi kiểu.

### Cách state `screenSharing` được cập nhật real-time (không qua polling `GET`)

Không có API để client tự "báo" đang share — trạng thái này hoàn toàn do
LiveKit webhook điều khiển phía server:

1. Client bật share màn hình → LiveKit publish track nguồn `SCREEN_SHARE`.
2. LiveKit gọi webhook nội bộ `track_published`/`track_unpublished` (không
   phải API public, không có trong tài liệu này).
3. `meet` publish 2 event mới lên Kafka để phía khác (nếu có consumer) nhận
   real-time:

| Kafka topic | Proto message | CloudEvents type |
|---|---|---|
| `meet.screen_share.started` | `io.github.smiskinext.event.meet.v1.ScreenShareStarted` | `io.github.smiskinext.meet.screen_share.started.v1` |
| `meet.screen_share.stopped` | `io.github.smiskinext.event.meet.v1.ScreenShareStopped` | `io.github.smiskinext.meet.screen_share.stopped.v1` |

Cả 2 message có cùng shape:

```protobuf
message ScreenShareStarted { // và ScreenShareStopped tương tự
  string meeting_id = 1;
  string tenant_id = 2;
  string account_id = 3;
  string occurred_at = 4;
  string identity = 5; // "<accountId>:<deviceId>"
}
```

**Chưa có consumer nào** cho 2 topic này trong repo — nếu frontend cần cập
nhật real-time (không chỉ đọc lại qua `GET`), cần một kênh đẩy xuống client
(SSE) đọc từ 2 topic này, hiện chưa được implement trong PR này.

---

## Tổng hợp mã lỗi mới dùng trong tài liệu này

Không có `MeetingErrorCode` nào mới được tạo cho `:cancel` — dùng lại
`NOT_OWNER`, `MEETING_NOT_FOUND`, `INVALID_STATUS_TRANSITION` đã có sẵn
trong `domain/MeetingErrorCode.java`. Riêng `:end` dùng lại
`MEETING_NOT_RUNNING` (vốn đã tồn tại cho tính năng mute participant trước
đó, tái sử dụng vì đúng ngữ nghĩa "meeting phải đang RUNNING").
