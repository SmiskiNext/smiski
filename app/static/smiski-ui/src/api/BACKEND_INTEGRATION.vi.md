# Hướng Dẫn Gắn Backend API Cho Smiski UI

Tài liệu này mô tả cách nối `static/smiski-ui` vào backend thật sau khi các
service hoàn thiện. Nội dung bám theo backend hiện có trong `services/tenant`,
`services/meet`, `services/record` và shared web/tenant/identity config.

## Bức Tranh Hiện Tại

Frontend đã có lớp adapter trong `src/api`:

- `config.ts`: bật/tắt mock/backend bằng `VITE_SMISKI_DATA_SOURCE`.
- `client.ts`: gọi API qua direct `fetch` hoặc Forge resolver.
- `endpoints.ts`: một nơi khai báo route backend.
- `mappers.ts`: đổi backend DTO sang domain UI và build request body từ form.
- `meetings.ts`, `participants.ts`, `recordings.ts`, `permissions.ts`: hàm API
  theo từng resource.

Mặc định UI vẫn chạy mock. Khi backend sẵn sàng, bật:

```bash
VITE_SMISKI_DATA_SOURCE=backend
```

Lưu ý: nhiều hook UI hiện vẫn gọi mock trực tiếp. Sau khi backend sẵn sàng, cần
wire các hook sang `src/api` bằng `shouldUseBackendApi()` rồi giữ mock làm
fallback.

## Context Backend Đã Đọc

Backend dùng Spring Boot service riêng theo domain:

- `tenant`: lifecycle cài/gỡ app Forge, route chính là `/api/1/tenants`.
- `meet`: meeting lifecycle, route chính là `/api/1/meetings...`.
- `record`: hiện `openapi.yaml` chưa expose HTTP path, mới có schema lỗi chung.
- `shared`: tự thêm prefix `/api/{version}` cho controller, nên controller Java
  khai báo `/meetings` nhưng URL thật là `/api/1/meetings`.

Các request nghiệp vụ cần header:

- `X-Tenant-ID`: Jira `cloudId`, lấy từ Forge invocation context.
- `X-Account-Id`: Jira `accountId`, lấy từ Forge invocation context.

Lưu ý hạ tầng: gateway local hiện là Caddy ở `services/docker/caddy/Caddyfile`.
Caddyfile hiện inject `X-User-ID` và `X-User-Email`, nhưng `meet` service mới
đọc `X-Account-Id`. Khi nối backend mới, cần đổi Caddy `header_up` sang
`X-Account-Id` hoặc để Forge resolver tự forward header đúng tên.

Backend trả lỗi theo RFC 9457 `application/problem+json`, ví dụ có `status`,
`code`, `detail`, `traceId`, `errors`. Frontend đã có `ApiError` để đọc các
trường này.

## Chốt Gateway Trước Khi Gắn

Spec service hiện dùng `/api/{version}` với version integer, ví dụ `/api/1`.
Trong khi Caddyfile hiện tại ở `services/docker/caddy/Caddyfile` đang match
`/api/v1/...`.

Trước khi bật frontend backend mode, cần chọn một chuẩn:

- Khuyến nghị theo service hiện tại: `/api/1/...`.
- Nếu muốn giữ `/api/v1/...`, backend shared `ApiPathPrefixAutoConfiguration` và
  OpenAPI phải đổi tương ứng.

Frontend hiện mặc định sinh `/api/1` qua `VITE_SMISKI_API_VERSION=1`, nên nếu
gateway giữ `/api/v1` thì request sẽ không match route.

## Luồng Đúng Trong Forge

Custom UI không nên tự gọi backend production trực tiếp với tenant/account tự
điền từ browser. Luồng an toàn là:

1. React gọi `invoke('backendRequest', payload)`.
2. Forge resolver đọc `req.context.cloudId` và `req.context.accountId`.
3. Resolver forward request tới Caddy/backend, gắn header: `X-Tenant-ID`,
   `X-Account-Id`, `Content-Type`, `Accept`.
4. Resolver trả nguyên status/body về `apiRequest()`.
5. UI dùng mapper trong `src/api/mappers.ts`.

Biến môi trường cần có ở Forge resolver:

```bash
SMISKI_API_BASE_URL=https://<caddy-gateway>
```

Trong `app/manifest.yml`, thay placeholder egress:

```yaml
permissions:
    external:
        fetch:
            backend:
                - address: 'https://<caddy-gateway>'
            client:
                - address: 'wss://<livekit-host>'
```

Sau khi đổi egress hoặc scope phải `forge deploy` rồi `forge install --upgrade`.

## Tenant API Không Gọi Từ React

Backend `tenant` có:

| Việc             | Method   | Path             | Ghi chú                                 |
| ---------------- | -------- | ---------------- | --------------------------------------- |
| Register tenant  | `POST`   | `/api/1/tenants` | Lần đầu trả `201`, reinstall trả `200`. |
| Uninstall tenant | `DELETE` | `/api/1/tenants` | Idempotent, trả tenant `UNINSTALLED`.   |

Hai API này nên gọi từ Forge lifecycle/background handler, không gọi từ màn hình
React.

Request register cần header `X-Tenant-ID=<cloudId>` và body dạng:

```json
{
    "id": "installation-id",
    "installerAccountId": "account-id",
    "app": {
        "id": "forge-app-id",
        "version": "1.0.0",
        "name": "Smiski",
        "ownerAccountId": "owner-account-id"
    },
    "environment": {
        "id": "forge-environment-id"
    },
    "siteUrl": "https://example.atlassian.net"
}
```

Request uninstall cần header `X-Tenant-ID=<cloudId>`. Body hiện optional ở
controller, có thể gửi `{ "id": "installation-id" }` nếu Forge event có dữ liệu.

Checklist tenant:

- Thêm Forge lifecycle trigger/function cho installed/upgraded/uninstalled nếu
  manifest chưa có.
- Trong handler lifecycle, map Forge payload sang `RegisterTenantRequest` hoặc
  `UninstallTenantRequest`.
- Không expose tenant API xuống browser.
- Log `traceId` khi backend trả problem detail.

## Meeting API Đã Có Trong Backend

Các path dưới đây đã có trong `services/meet/openapi.yaml`.

| UI cần làm                  | Method   | Path                            | Request/response chính                                               |
| --------------------------- | -------- | ------------------------------- | -------------------------------------------------------------------- |
| List issue/project meetings | `POST`   | `/api/1/meetings`               | Body `ListMeetingsRequest`, response `{ data, meta }`.               |
| Create instant meeting      | `POST`   | `/api/1/meetings:instant`       | Body `CreateInstantMeetingRequest`, response `{ meeting, livekit }`. |
| Schedule meeting            | `POST`   | `/api/1/meetings:schedule`      | Body `ScheduleMeetingRequest`, response `{ meeting }`.               |
| Update meeting              | `PUT`    | `/api/1/meetings/{id}`          | Body `UpdateMeetingRequest`, response `{ meeting }`.                 |
| Replace invitees            | `PUT`    | `/api/1/meetings/{id}/invitees` | Body `{ invitees }`, response `{ invitees }`.                        |
| Delete meeting              | `DELETE` | `/api/1/meetings/{id}`          | Soft delete, response `{ meeting }`.                                 |
| Batch delete meetings       | `POST`   | `/api/1/meetings:batchDelete`   | Body `{ meetingIds }`, response `{ meetings }`.                      |

Quan trọng: list meetings dùng `POST` với body JSON, không dùng query string.
Adapter hiện cần đổi từ:

```ts
apiRequest(meetingEndpoints.list, { query: { issueKey } });
```

sang:

```ts
apiRequest(meetingEndpoints.list, {
    method: 'POST',
    body: { issueKey, pageSize: 50, sort: 'CREATED_AT' },
});
```

`ListMeetingsRequest` backend hỗ trợ:

- `creatorId`
- `search`
- `statuses`: `SCHEDULED`, `RUNNING`, `COMPLETED`, `CANCELED`
- `issueKey`
- `sort`: `CREATED_AT` hoặc `START_TIME`
- `pageSize`: tối đa `50`
- `pageToken`

## Payload Meeting Cần Map

Create instant cần body:

```json
{
    "title": "Daily standup",
    "description": "Quick sync",
    "issueLink": {
        "issueId": "10001",
        "issueKey": "PROJ-1",
        "projectKey": "PROJ"
    },
    "settings": {
        "admissionPolicy": "OPEN",
        "maxParticipants": 50,
        "allowScreenShare": true,
        "chatEnabled": true,
        "allowMicrophone": true,
        "allowVideo": true
    },
    "host": {
        "displayName": "Alice Nguyen",
        "deviceId": "web-device-id",
        "avatarUrl": null
    },
    "organizerEmail": "alice@example.com",
    "organizerDisplayName": "Alice Nguyen",
    "zoneId": "Asia/Ho_Chi_Minh",
    "invitees": [
        {
            "email": "bob@example.com",
            "accountId": "account-bob",
            "displayName": "Bob Tran"
        }
    ]
}
```

Schedule giống instant nhưng thay `host` bằng `timeRange`:

```json
{
    "timeRange": {
        "startTime": "2026-08-01T03:00:00.000Z",
        "endTime": "2026-08-01T04:00:00.000Z"
    }
}
```

Update meeting là full update, không phải patch. Frontend phải gửi đủ `title`,
`description`, `issueLink`, `settings`, `zoneId`; `timeRange` có thể null nhưng
scheduled meeting thường cần giữ lại từ `baseMeeting`.

## API Chưa Có Hoặc Cần Chốt Thêm

Các route này đang được frontend dự đoán trong `endpoints.ts`, nhưng backend
OpenAPI hiện chưa có:

- `GET /api/1/meetings/{id}`: lấy detail một meeting.
- `POST /api/1/meetings/{id}:start`: start scheduled meeting.
- `POST /api/1/meetings/{id}:end`: end running meeting.
- `POST /api/1/meetings/{id}:cancel`: cancel scheduled meeting.
- `GET /api/1/meetings/{id}/participants`: roster participant.
- `POST /api/1/meetings/{id}/room-token`: cấp token LiveKit cho người join.
- `GET /api/1/meetings/host-conflict`: kiểm tra host đang có running meeting.
- `GET /api/1/projects/{projectKey}/meeting-permissions`: quyền View/Edit.
- Recording APIs: `record/openapi.yaml` hiện chưa có path HTTP.

Khi backend thêm các route này, chỉ sửa `endpoints.ts` và `mappers.ts` trước,
sau đó mới wire hook/màn hình. Nếu backend quyết định không làm route nào,
frontend phải bỏ/đổi flow tương ứng thay vì giữ route giả.

## LiveKit Token

`POST /api/1/meetings:instant` đã trả:

```json
{
    "livekit": {
        "token": "...",
        "roomName": "meeting-..."
    }
}
```

Response này chưa có `url`. Frontend hiện cần `token` và `url` để join phòng. Có
hai cách:

- Backend thêm `url` hoặc `livekitUrl` vào response token.
- Frontend dùng `VITE_SMISKI_LIVEKIT_URL`/Forge env làm fallback URL.

Resolver `getRoomToken` trong `app/src/index.ts` hiện chỉ là shim prototype và
không kiểm tra quyền. Khi backend có room-token endpoint thật, bỏ shim này khỏi
flow production.

## Quyền View/Edit Meeting

Backend `meet` hiện kiểm tra một số hành động theo host/account, nhưng chưa có
endpoint permission dành cho UI. Theo `app/PERMISSION.md`, UI cần biết:

- `View Meeting`: xem/list/join/view recording.
- `Edit Meeting`: create/schedule/start/edit/cancel/end/record.

Có hai hướng:

- Forge resolver gọi Jira permission API bằng `api.asUser()` rồi trả
  `{ hasViewMeeting, hasEditMeeting }` cho UI.
- Backend expose endpoint permission theo tenant/project/account, resolver chỉ
  forward.

Dù UI ẩn nút theo permission, backend vẫn phải kiểm tra lại ở action endpoint.

## Direct Dev Mode

Chạy Vite gọi thẳng backend/gateway:

```bash
VITE_SMISKI_DATA_SOURCE=backend \
    VITE_SMISKI_API_TRANSPORT=direct \
    VITE_SMISKI_API_BASE_URL=http://localhost:30000 \
    VITE_SMISKI_API_VERSION=1 \
    pnpm run dev
```

Lệnh trên giả định Caddy đã được chỉnh để route `/api/1/...` tới backend mới.
Nếu Caddyfile vẫn giữ `/api/v1/...`, request từ adapter sẽ không match route.

Direct mode không có Forge context. Để test được cần một trong các cách:

- Caddy dev tự inject tenant/account cố định.
- Backend local cho phép header dev.
- Tạm thêm header dev trong `apiRequest()` bằng env local, nhưng không commit
  secret hoặc account thật.

Nếu gọi qua browser, CORS của Caddy phải allow thêm `X-Tenant-ID` và
`X-Account-Id`; Caddyfile hiện chỉ allow `Authorization`, `Content-Type`,
`X-Requested-With`, `X-User-ID`, `X-User-Email`.

## Thứ Tự Gắn API Đề Xuất

1. Chạy/regenerate OpenAPI sau khi backend final:

    ```bash
    pnpm run openapi
    ```

2. Chốt gateway path là `/api/1` hay `/api/v1`, rồi chỉnh
   `VITE_SMISKI_API_VERSION` hoặc backend/gateway cho khớp.

3. Implement Forge resolver chung `backendRequest` trong `app/src/index.ts`.
   Resolver chỉ forward HTTP, không chứa business logic.

4. Implement tenant lifecycle handler và gọi `tenant` API khi app install hoặc
   uninstall.

5. Sửa `src/api/endpoints.ts` theo OpenAPI thật. Xóa hoặc đánh dấu route chưa
   có.

6. Sửa `src/api/meetings.ts`:
    - list dùng `POST /meetings` với body.
    - delete dùng `DELETE /meetings/{id}` hoặc map cancel sang endpoint backend
      thật nếu backend tách cancel/delete.
    - batch delete dùng `/meetings:batchDelete` nếu UI có bulk action.

7. Sửa `src/api/mappers.ts` theo DTO final. Sau khi contract ổn định, giảm bớt
   mapper tolerant để lỗi contract lộ sớm hơn.

8. Wire hook sang backend mode:
    - `useIssueMeetings`
    - `useProjectMeetings`
    - `useMeeting`
    - `useMeetingMutations`
    - `useMeetingParticipants`
    - `useHostConflict`
    - `useMeetingPermission`
    - `useMeetingRecording`
    - `useRoomToken`

9. Test theo từng lớp:

    ```bash
    pnpm run test
    pnpm run lint
    pnpm run build
    ```

    Từ `app/`:

    ```bash
    pnpm build
    pnpm lint
    ```

10. Test trong Forge tunnel/development deployment với resolver transport trước
    khi bật direct client fetch ở production.

## Checklist Trước Khi Bật Backend Mode

- `app/manifest.yml` có egress gateway/LiveKit thật.
- Forge env có `SMISKI_API_BASE_URL` trỏ tới public Caddy API gateway.
- Gateway route khớp `/api/1` hoặc `/api/v1`.
- Resolver gắn đúng `X-Tenant-ID` và `X-Account-Id`.
- Tenant lifecycle đã register site trước khi UI gọi `meet`.
- `src/api/meetings.ts` list đã đổi sang `POST` body.
- Route chưa có backend đã được implement hoặc UI tạm disable.
- LiveKit token response có `url` hoặc UI có fallback `VITE_SMISKI_LIVEKIT_URL`.
- `ApiError` được hiển thị/log đủ `code`, `detail`, `traceId`.
- Build Vite và app đều pass trước deploy.
