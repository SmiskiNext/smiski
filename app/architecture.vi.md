# Smiski — Kiến Trúc Hệ Thống

> Module họp trực tuyến được nhúng trong Jira thông qua Forge, vận hành bởi LiveKit.
>
> **Trạng thái:** Bản nháp · **Đối tượng đọc:** Kỹ sư, người review · **Phạm vi:**
> Các backend service (`tenant`, `meet`, `record`, `notification`) và hạ tầng hỗ trợ.
>
> Sơ đồ được viết bằng [D2](https://d2lang.com). Mỗi khối code `d2` là một
> sơ đồ độc lập — hãy trích xuất nó trước khi render (D2 không parse Markdown).
> Xem [Render sơ đồ](#render-sơ-đồ).

## Mục lục

- [1. Tổng quan](#1-tổng-quan)
- [2. Nguyên tắc kiến trúc](#2-nguyên-tắc-kiến-trúc)
- [3. Bối cảnh hệ thống](#3-bối-cảnh-hệ-thống)
- [4. Trách nhiệm dịch vụ và kho dữ liệu](#4-trách-nhiệm-dịch-vụ-và-kho-dữ-liệu)
- [5. Trục sự kiện — Kafka / CloudEvents](#5-trục-sự-kiện--kafka--cloudevents)
- [6. Yêu cầu tham gia và trung tâm SSE thời gian thực](#6-yêu-cầu-tham-gia-và-trung-tâm-sse-thời-gian-thực)
- [7. Email mời họp (event-carried)](#7-email-mời-họp-event-carried)
- [8. CQRS và bản sao đọc (meet)](#8-cqrs-và-bản-sao-đọc-meet)
- [9. Phân vùng đa tenant](#9-phân-vùng-đa-tenant)
- [10. Vòng đời ghi hình và lưu giữ](#10-vòng-đời-ghi-hình-và-lưu-giữ)
- [11. Bản đồ sử dụng Valkey](#11-bản-đồ-sử-dụng-valkey)
- [12. Tác vụ theo lịch](#12-tác-vụ-theo-lịch)
- [13. Quyết định thiết kế](#13-quyết-định-thiết-kế)
- [14. Sai khác đã biết và việc cần làm tiếp](#14-sai-khác-đã-biết-và-việc-cần-làm-tiếp)
- [Render sơ đồ](#render-sơ-đồ)

---

## 1. Tổng quan

Smiski được cấu thành từ **bốn microservice** xây dựng theo phân lớp hexagonal
(domain → application → infrastructure → presentation). Mỗi service sở hữu
cơ sở dữ liệu riêng (database-per-service) và giao tiếp bất đồng bộ qua
**Kafka theo định dạng CloudEvents**. Danh tính được lấy trực tiếp từ Jira —
không có hệ thống đăng nhập riêng — và toàn bộ dữ liệu được phân vùng theo
`tenant_id`, chính là `cloudId` của Jira.

| Service        | Trách nhiệm                                                      | Kho lưu trữ                                             |
| -------------- | ---------------------------------------------------------------- | ------------------------------------------------------- |
| `tenant`       | Nguồn dữ liệu gốc cho tenancy: cài đặt / nâng cấp / gỡ Forge app | Postgres · Kafka                                        |
| `meet`         | Lõi cuộc họp: CRUD, yêu cầu tham gia, cấp token LiveKit          | Postgres (primary + replica) · Valkey · Kafka · LiveKit |
| `record`       | Vòng đời ghi hình độc lập, dẫn dắt bởi LiveKit Egress            | Postgres · Kafka · LiveKit Egress · RustFS              |
| `notification` | Trung tâm SSE thời gian thực và email mời họp                    | Valkey · Kafka (không có database)                      |

---

## 2. Nguyên tắc kiến trúc

- **Control plane không trạng thái (stateless).** Cả bốn service đều không
  giữ trạng thái cục bộ; trạng thái được lưu trong Postgres, Valkey và Kafka,
  nhờ đó service có thể mở rộng theo chiều ngang.
- **Media plane không đi qua backend.** LiveKit (một SFU) chuyển tiếp media
  trực tiếp; backend chỉ cấp access token và xử lý webhook. Tải media không
  bao giờ chạm tới control plane.
- **CQRS (cấp độ 2).** Lệnh ghi (command) ghi vào cơ sở dữ liệu primary; truy
  vấn (query) đọc từ replica và từ các read model trong Valkey.
- **Transactional outbox.** Sự kiện domain được ghi trong cùng transaction
  với aggregate, sau đó được publish lên Kafka bởi một poller dùng
  `FOR UPDATE SKIP LOCKED`.
- **Event-carried state transfer.** Sự kiện mang theo dữ liệu mà consumer cần
  (ví dụ: email và tên hiển thị của người được mời), loại bỏ việc tra cứu
  đồng bộ giữa các service.

---

## 3. Bối cảnh hệ thống

Góc nhìn cấp container (C4 system-context) thể hiện luồng request, luồng sự
kiện, và các phụ thuộc dữ liệu/media. Bố cục đọc từ trái sang phải như một
pipeline: Jira gửi request qua Kong tới các service, các service publish và
consume từ Kafka, và mỗi service chỉ truy cập kho dữ liệu mà nó sở hữu.

```d2
direction: right

jira: "Jira Cloud" {
  shape: cloud
  forge: "Forge App (UI Kit)"
}

kong: "Kong Gateway\nXác thực JWT · gắn tenant/account" {
  shape: hexagon
}

services: "Control Plane — Spring Boot không trạng thái" {
  tenant: "tenant"
  meet: "meet"
  record: "record"
  notification: "notification"
}

kafka: "Kafka — CloudEvents (key = tenant_id)" {
  shape: queue
}

data: "Tầng Dữ liệu & Media" {
  tenantdb: "tenant_db" { shape: cylinder }
  meetdb: "meet_db\nprimary + replica" { shape: cylinder }
  recdb: "record_db" { shape: cylinder }
  valkey: "Valkey" { shape: cylinder }
  livekit: "LiveKit SFU + Egress"
  rustfs: "RustFS (S3)" { shape: cylinder }
}

jira.forge -> kong: "Forge Remote (JWT / JWKS)"
kong -> services.tenant
kong -> services.meet
kong -> services.record
kong -> services.notification: "SSE"

services.tenant -> data.tenantdb
services.meet -> data.meetdb
services.meet -> data.valkey
services.meet -> data.livekit: "token / webhook"
services.record -> data.recdb
services.record -> data.livekit: "egress"
services.record -> data.rustfs
services.notification -> data.valkey: "pub/sub"
data.livekit -> data.rustfs: "tải lên"

services.tenant -> kafka: "tenant.*"
services.meet -> kafka: "meet.*"
services.record -> kafka: "record.*"
kafka -> services.notification: "tiêu thụ"
kafka -> services.meet: "chiếu (projection) tenant.*"
kafka -> services.record: "chiếu (projection) tenant.*"
```

---

## 4. Trách nhiệm dịch vụ và kho dữ liệu

```d2
direction: right

tenant: "tenant" {
  shape: rectangle
  owns: "Sở hữu:\n- bảng tenants (nguồn dữ liệu gốc)\n- cài đặt / nâng cấp / gỡ cài đặt\n- chính sách purge A/B"
  emits: "Phát ra:\ntenant.app.installed\ntenant.app.uninstalled"
}

meet: "meet" {
  shape: rectangle
  cmd: "Phía Command:\n- Meeting aggregate\n- JoinRequest (Valkey)\n- cấp token LiveKit\n- outbox -> Kafka"
  qry: "Phía Query:\n- projection -> replica\n- người tham gia live -> Valkey"
}

record: "record" {
  shape: rectangle
  owns: "Sở hữu:\n- recordings (metadata)\n- vòng đời dẫn dắt bởi egress\n- lưu giữ 15 ngày / 30 ngày"
}

notification: "notification" {
  shape: rectangle
  hub: "Trung tâm thời gian thực:\n- SSE + Valkey pub/sub\n- email (event-carried)\n- không tra cứu gRPC"
}

tenant -> meet: "chiếu tenant (Kafka)"
tenant -> record: "chiếu tenant (Kafka)"
meet -> record: "meeting.started (tự động ghi hình)"
meet -> notification: "meet.join-request.* · meet.meeting.invitations.sent"
record -> notification: "record.recording.completed"
```

**Đặc điểm tải I/O:**

- `meet` **nặng về đọc** (liệt kê cuộc họp theo issue, lịch sử) với **các đợt
  ghi bùng nổ** (webhook tham gia/rời khỏi). Đây là service hưởng lợi nhiều
  nhất từ read replica.
- `record` **không nặng tải trên Postgres** (3–4 lượt ghi mỗi lần ghi hình,
  đọc không thường xuyên). Điểm nóng của nó là **egress transcoding (CPU) và
  upload lên RustFS (network)** chứ không phải database, vì vậy read replica
  bị hoãn lại để ưu tiên tách riêng node egress.
- `tenant` dùng một bảng nhỏ và không cần replica.
- `notification` không có database.

---

## 5. Trục sự kiện — Kafka / CloudEvents

Quy ước đặt tên topic: `<service>.<aggregate>.<action>`. Khóa phân vùng
(partition key) là `tenant_id`, giúp đảm bảo thứ tự sự kiện trong phạm vi một
tenant.

```d2
direction: right

producers: "Producer (outbox -> poller)" {
  tenant: "tenant"
  meet: "meet"
  record: "record"
}

kafka: "Kafka topics" {
  shape: queue
  t1: "tenant.app.installed"
  t2: "tenant.app.uninstalled"
  t3: "meet.join-request.created"
  t4: "meet.join-request.approved\n(payload: liveKitToken + roomName)"
  t5: "meet.meeting.invitations.sent\n(payload: email + displayName)"
  t6: "meet.participant.joined"
  t7: "record.recording.completed"
}

consumers: "Consumer" {
  meetproj: "meet / record\n(chiếu tenant)"
  notif: "notification\n(SSE + email)"
}

producers.tenant -> kafka.t1
producers.tenant -> kafka.t2
producers.meet -> kafka.t3
producers.meet -> kafka.t4
producers.meet -> kafka.t5
producers.meet -> kafka.t6
producers.record -> kafka.t7

kafka.t1 -> consumers.meetproj
kafka.t2 -> consumers.meetproj
kafka.t3 -> consumers.notif
kafka.t4 -> consumers.notif
kafka.t5 -> consumers.notif
kafka.t6 -> consumers.notif
kafka.t7 -> consumers.notif
```

---

## 6. Yêu cầu tham gia và trung tâm SSE thời gian thực

`notification` là trung tâm SSE; `meet` chỉ publish sự kiện. Token LiveKit
được tạo trong `meet` và được truyền đi bên trong payload của sự kiện
(event-carried). Vì `notification` chạy nhiều instance và một SSE emitter
chỉ gắn với một instance duy nhất, việc phân phối đòi hỏi **fan-out qua
Valkey Pub/Sub**.

```d2
shape: sequence_diagram

req: "Người yêu cầu\n(Forge UI)"
host: "Chủ trì\n(Forge UI)"
meet: "meet"
valkey: "Valkey"
kafka: "Kafka"
notif_a: "notification-A"
notif_b: "notification-B"

req -> meet: "POST /meetings/{id}/join-requests"
meet -> valkey: "SET join_req:{id} (TTL 10m)"
meet -> kafka: "meet.join-request.created"
kafka -> notif_a: "tiêu thụ"
notif_a -> host: "SSE: có người đang yêu cầu tham gia"

host -> meet: "POST .../join-requests/{id}:approve"
meet -> meet: "JoinRequest.approve()\n+ tạo token LiveKit"
meet -> kafka: "meet.join-request.approved\n(liveKitToken + roomName)"
kafka -> notif_a: "tiêu thụ"
notif_a -> valkey: "PUBLISH sse:channel:{requesterAcct}"
valkey -> notif_b: "SSE của người yêu cầu đang chạy trên B"
notif_b -> req: "SSE: đã duyệt + token"
req -> req: "kết nối tới LiveKit bằng token"
```

**Race điều kiện đăng ký muộn (late-subscribe).** Nếu host duyệt trước khi
requester mở xong luồng SSE, kết quả được lưu dưới `join_req_result:{id}`
(Valkey, TTL bằng thời gian timeout của SSE) và được phát lại khi client
đăng ký (subscribe).

---

## 7. Email mời họp (event-carried)

`notification` không thực hiện tra cứu identity qua gRPC — phụ thuộc cũ vào
`user-management` đã được loại bỏ. Email và tên hiển thị của người được mời
được nhúng sẵn vào payload sự kiện khi `meet` publish nó.

```d2
direction: down

meet: "meet" {
  uc: "SendInvitations use case\n- phân giải người được mời (accountId -> email/name)\n- nhúng vào sự kiện"
}

kafka: "Kafka\nmeet.meeting.invitations.sent\n(invitees[]: email, displayName)" {
  shape: queue
}

notif: "notification" {
  consumer: "MeetingInvitationsSentConsumer"
  idem: "SETNX idem:consumer:{eventId} EX 1h"
  render: "MeetingInvitationEmailRenderer"
  send: "ResendEmailSender"
}

resend: "Resend (email API)" { shape: cloud }

meet.uc -> kafka
kafka -> notif.consumer
notif.consumer -> notif.idem: "khử trùng lặp"
notif.idem -> notif.render
notif.render -> notif.send
notif.send -> resend
```

---

## 8. CQRS và bản sao đọc (meet)

```d2
direction: down

controller: "Controller"

cmd: "Phía COMMAND" {
  uc: "CommandUseCase"
  agg: "Meeting aggregate\n(SELECT FOR UPDATE)"
  outbox: "outbox_event (cùng transaction)"
}

qry: "Phía QUERY" {
  qs: "QueryService\n(SQL projection,\nkhông rehydrate aggregate)"
}

routing: "RoutingDataSource\n@Transactional(readOnly) -> REPLICA" { shape: diamond }

primary: "Postgres PRIMARY\n(rw, có phân vùng)" { shape: cylinder }
replica: "Postgres REPLICA\n(ro, lịch sử / UC07)" { shape: cylinder }
valkey: "Valkey\nread model thời gian thực (UC04)\ndanh sách người tham gia" { shape: cylinder }

controller -> cmd.uc: "ghi"
controller -> qry.qs: "đọc"
cmd.uc -> cmd.agg
cmd.agg -> cmd.outbox
cmd.agg -> primary: "ghi"
qry.qs -> routing
routing -> replica: "chấp nhận eventual\n(lịch sử, danh sách)"
routing -> primary: "read-your-own-write\n(vừa tạo -> đọc ngay)"
qry.qs -> valkey: "thời gian thực\n(người tham gia live)"
primary -> replica: "streaming replication (async WAL)"
```

**Định tuyến theo yêu cầu độ mới (freshness):**

| Nhóm                  | Ví dụ                                       | Định tuyến |
| --------------------- | ------------------------------------------- | ---------- |
| Read-your-own-write   | vừa tạo cuộc họp, xem chi tiết              | PRIMARY    |
| Eventually consistent | lịch sử, truy vết UC07, danh sách theo host | REPLICA    |
| Thời gian thực        | danh sách người tham gia trực tiếp (UC04)   | Valkey     |

---

## 9. Phân vùng đa tenant

Mọi bảng nghiệp vụ đều `PARTITION BY HASH (tenant_id)` với 16 phân vùng.
`tenant_id` (chính là `cloudId` của Jira) là cột dẫn đầu (leading column)
của mọi primary key, foreign key và unique constraint, cho phép partition
pruning và giữ các phép join cục bộ trong một phân vùng duy nhất.

```d2
direction: down

tenant_id: "tenant_id = Jira cloudId\n(cột dẫn đầu của mọi PK/index)" { shape: oval }

meetings: "meetings\nPARTITION BY HASH(tenant_id)" {
  p0: "meetings_p00\n(MODULUS 16, REMAINDER 0)"
  p1: "meetings_p01"
  pdot: "..."
  p15: "meetings_p15\n(REMAINDER 15)"
}

related: "Bảng con (cùng cơ chế phân vùng)" {
  pl: "participation_logs"
  mi: "meeting_invitees"
  it: "invite_tokens"
}

tenant_id -> meetings: "hash -> chọn phân vùng"
meetings -> related: "FK (tenant_id, meeting_id)\njoin cục bộ trong một phân vùng"
```

Bảng `tenants` (một projection) **không** được phân vùng — nó chỉ chứa một
dòng nhỏ cho mỗi site và được đồng bộ từ service `tenant` qua Kafka. Các
bảng nghiệp vụ tham chiếu tới nó để đảm bảo tenant tồn tại trước khi thực
hiện bất kỳ thao tác ghi nào.

---

## 10. Vòng đời ghi hình và lưu giữ

```d2
direction: right

pending: "PENDING\n(StartRecording)"
recording: "RECORDING\n(egress_started)"
completed: "COMPLETED\n(egress_ended + file)"
failed: "FAILED\n(lỗi / timeout)"
soft: "soft-deleted\nis_deleted = TRUE"
hard: "HARD delete\nxóa RustFS + row"

pending -> recording: "webhook egress_started"
recording -> completed: "webhook egress_ended"
pending -> failed: "timeout (EgressTimeoutJob)"
recording -> failed: "lỗi egress"
completed -> soft: "RetentionJob\nended_at < now - 15 ngày"
soft -> hard: "RetentionJob\nedited_at < now - 15 ngày\n(= tổng 30 ngày)"
hard -> "record.recording.purged": "phát sự kiện\n(meet gỡ liên kết khỏi issue)"
```

Thời gian lưu giữ là cố định và **không thể cấu hình theo tenant**:
**soft-delete sau 15 ngày**, **hard-delete sau 30 ngày**.

---

## 11. Bản đồ sử dụng Valkey

```d2
direction: right

valkey: "Valkey" {
  token: "livekit:token:{room}:{identity}\nTTL 5-10 phút — JWT được cache"
  jr: "join_req:{id}\nTTL 10 phút — trạng thái JoinRequest"
  jrr: "join_req_result:{id}\nTTL = timeout của SSE — phát lại"
  parts: "meeting:{id}:participants\nread model thời gian thực (UC04)"
  sse: "sse:channel:{accountId}\nFan-out Pub/Sub giữa các instance"
  idemw: "idem:webhook:{eventId}\nTTL 1h — khử trùng lặp webhook"
  idemc: "idem:consumer:{eventId}\nTTL 1h — khử trùng lặp Kafka"
}

meet: "meet"
notif: "notification"

meet -> valkey.token
meet -> valkey.jr
meet -> valkey.parts
meet -> valkey.idemw
notif -> valkey.jrr
notif -> valkey.sse
notif -> valkey.idemc
```

---

## 12. Tác vụ theo lịch

| Service         | Job                     | Chu kỳ    | Mục đích                                            |
| --------------- | ----------------------- | --------- | --------------------------------------------------- |
| tất cả Postgres | OutboxPoller            | 500 ms    | Publish outbox lên Kafka (`FOR UPDATE SKIP LOCKED`) |
| meet            | JoinRequestExpiryJob    | 1 phút    | Phát sự kiện EXPIRED cho SSE                        |
| meet            | ArchiveEndedMeetingsJob | hằng ngày | **Chỉ bật khi số dòng/phân vùng vượt ngưỡng**       |
| record          | RecordingRetentionJob   | hằng ngày | Soft-delete sau 15 ngày, hard-delete sau 30 ngày    |
| record          | EgressTimeoutJob        | 5 phút    | Đánh dấu các recording PENDING quá hạn thành FAILED |
| tenant          | PurgeTenantJob          | hằng ngày | Purge sau thời gian ân hạn (Option B)               |

---

## 13. Quyết định thiết kế

| #           | Chủ đề                             | Quyết định                                                                                         |
| ----------- | ---------------------------------- | -------------------------------------------------------------------------------------------------- |
| Q1          | Yêu cầu tham gia + SSE             | `notification` là trung tâm thời gian thực; `meet` chỉ publish sự kiện; fan-out qua Valkey Pub/Sub |
| Q2          | Phân giải danh tính người được mời | Hoàn toàn event-carried — đã loại bỏ tra cứu gRPC                                                  |
| Q3          | Quyền sở hữu LiveKit               | Chỉ nằm trong `meet` (token) và `record` (egress); `tenant` không liên quan                        |
| #1          | Gỡ cài đặt                         | Hai lựa chọn: A purge ngay lập tức / B thời gian ân hạn (mặc định)                                 |
| #2          | Lưu giữ recording                  | Cố định 15 ngày soft / 30 ngày hard                                                                |
| #3          | Lưu trữ lịch sử cuộc họp           | Giữ nguyên tại chỗ; chỉ archive sang bảng lạnh khi đạt ngưỡng                                      |
| #4          | Publish outbox                     | Poller `@Scheduled` (`SKIP LOCKED`), không dùng CDC                                                |
| CQRS        | Cấp độ                             | Cấp độ 2 hybrid: command tới primary, query tới replica + Valkey                                   |
| Replica     | Phạm vi                            | `meet` trước tiên; `record` hoãn lại (không nặng DB); tenant/notification không cần                |
| Idempotency | Nơi lưu                            | Valkey (`idem:*`, TTL 1h)                                                                          |

---

## 14. Sai khác đã biết và việc cần làm tiếp

Đây là các khoảng cách giữa thiết kế này và codebase hiện tại.

- **D1** — Chuẩn hóa tên topic theo `<service>.<aggregate>.<action>`;
  consumer của notification vẫn đang dùng `meeting-management.*`.
- **D2** — Loại bỏ phụ thuộc gRPC `user-management` khỏi notification
  (`UserLookupPort`, `UserServiceGrpcLookupAdapter`, `GrpcClientConfig`).
- **D3** — Xây dựng tầng application và presentation cùng outbox poller cho
  `tenant`, `meet`, và `record` (hiện đang thiếu).
- **D4** — Thêm Valkey Pub/Sub cho fan-out SSE (yêu cầu bởi Q1).
- **D5 / D6** — Thêm job archival (đang hoãn) và job retention.
- **D7** — Cân nhắc lại `AUTO_OFFSET_RESET=latest`; dùng `earliest` cho các
  consumer quan trọng với email/SSE.

---

## Render sơ đồ

D2 không parse Markdown, vì vậy đưa file này trực tiếp vào `d2` sẽ lỗi. Hãy
trích xuất từng khối `d2` ra file riêng trước, ví dụ:

````sh
awk '
/^```d2$/ {n++; f=sprintf("diagram-%02d.d2", n); inb=1; next}
/^```$/   {inb=0; next}
inb       {print > f}
' docs/architecture.md

for f in diagram-*.d2; do d2 "$f" "${f%.d2}.svg"; done
````

Ngoài ra, có thể dùng trình xem Markdown hoặc plugin IDE có hỗ trợ D2 sẵn.
