# Tham khảo — Hệ thống Phân quyền & Vai trò trong Jira

> Tài liệu tổng hợp mô hình phân quyền (permission) và vai trò (role) của Jira,
> trọng tâm **Jira Cloud** kèm ghi chú khác biệt **Data Center / Server**. Dùng
> làm nền tham chiếu khi thiết kế phân quyền cho Module Hội họp tích hợp Jira
> (xem `requirements/BA.md`, `requirements/ROADMAP.md`).

---

## 1. Mô hình phân quyền ba lớp

Jira kiểm soát truy cập theo ba lớp độc lập, từ toàn cục đến từng công việc:

```
Global Permissions (toàn site / instance)
    ↓
Project / Space Permissions (cấp project)
    ↓
Issue-level Security (cấp issue cụ thể)
```

**Nguyên tắc phụ thuộc:** nếu người dùng không có quyền _Browse_ một project thì
không thể xem bất kỳ issue nào bên trong, kể cả khi họ nằm trong security level
của issue đó.

| Lớp     | Phạm vi     | Công cụ cấu hình                 | Cấp cho ai                   |
| ------- | ----------- | -------------------------------- | ---------------------------- |
| Global  | Toàn site   | Global Permissions               | Chỉ groups hoặc "Anyone"     |
| Project | Một project | Permission Scheme / Project Role | Groups, users, roles, fields |
| Issue   | Một issue   | Issue Security Scheme            | Security level → holders     |

---

## 2. Global Permissions (Phân quyền toàn cục)

- Kiểm soát toàn bộ Jira tại cấp site/instance, áp dụng cho mọi project.
- **Chỉ gán cho groups** (hoặc "Anyone"), không gán user lẻ.
- Quản lý bởi Jira Admin.

Các quyền toàn cục chính:

| Quyền                             | Ý nghĩa                                                     |
| --------------------------------- | ----------------------------------------------------------- |
| Administer Jira                   | Tạo project, chỉnh workflow/scheme/field; đăng nhập mọi lúc |
| Browse users and groups           | Xem người dùng, @mention, chia sẻ filter/dashboard          |
| Make bulk changes                 | Sửa/xóa/di chuyển nhiều issue cùng lúc                      |
| Manage group filter subscriptions | Tạo/xóa group filter subscription                           |
| Create team-managed projects      | Tạo project do team quản lý                                 |

> **Data Center / Server:** phân tách rõ **Jira System Administrators** và
> **Jira Administrators**; Cloud không có "System Administrator" riêng (quản trị
> ở cấp Organization).

---

## 3. Project Roles (Vai trò project)

**Project role** là "placeholder" (vị trí) được định nghĩa ở cấp Jira admin
trong permission scheme, nhưng được điền cụ thể bởi project admin trong từng
project. Cùng một scheme, mỗi project có thể gán người khác nhau vào cùng role.

**Vai trò mặc định:**

| Role                | Vai trò                                              |
| ------------------- | ---------------------------------------------------- |
| Administrators      | Quản lý cấu hình project (settings, workflow, field) |
| Members             | Đội ngũ cốt lõi; tạo và chỉnh sửa issue              |
| Viewers / Reporters | Xem project, có thể tạo issue                        |

**Nguồn điền vào role:** users, groups, trường Assignee, trường Reporter, hoặc
bất kỳ custom field kiểu user/group select; ngoài ra apps/integration cũng dùng
role riêng (ví dụ `atlassian-addons-project-access`).

**Vì sao ưu tiên Role hơn Group ở cấp project:**

| Tiêu chí           | Groups                             | Project Roles                                |
| ------------------ | ---------------------------------- | -------------------------------------------- |
| Phạm vi            | Toàn site                          | Từng project (nhưng scheme tái sử dụng được) |
| Người quản lý      | Cần Jira admin                     | Project admin tự cấp/thu hồi                 |
| Tái sử dụng scheme | Khó — cần scheme riêng mỗi project | Dễ — cùng scheme, người khác nhau            |
| Kết quả            | Kiểm soát truy cập toàn cục        | Khả năng mở rộng + ủy quyền (delegation)     |

---

## 4. Permission Schemes

Một permission scheme là bộ quy tắc ánh xạ:

```
Permission → Holders (Roles / Groups / Users / Fields)
```

Ví dụ:

```
Create Issues      → Members role
Edit Issues        → Members role + Assignee field
Administer Project → Administrators role
Browse Projects    → Anyone / Members role
```

- Chỉ áp dụng cho **company-managed projects**.
- **Chia sẻ được giữa nhiều project**; sửa scheme sẽ ảnh hưởng _tất cả_ project
  đang dùng nó.

**Các quyền project phổ biến:**

| Nhóm                | Quyền tiêu biểu                                                                                 |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| Truy cập & cấu hình | Browse Projects, Administer Projects, Manage sprints, Manage versions, Edit workflows           |
| Công việc (issue)   | Create / Edit / Delete / Assign / Transition / Resolve / Move / Link Issues, Set Issue Security |
| Comment             | Add / Edit all / Delete all comments                                                            |
| Attachment          | Create / Delete all attachments                                                                 |

---

## 5. Issue Security Schemes (Phân quyền cấp issue)

- Cho phép **ẩn issue cụ thể** khỏi những người vẫn browse được project.
- Định nghĩa các **security level** (ví dụ Public / Internal / Confidential),
  mỗi level gán holders riêng.
- **Độc lập** với permission scheme: issue đã gán level chỉ hiển thị cho holders
  của level đó — kể cả project admin cũng không thấy nếu không nằm trong
  holders.

Luồng dùng: tạo scheme → định nghĩa level + holders → gắn scheme vào project →
gán level cho từng issue.

---

## 6. Groups vs Roles vs Users

| Yếu tố           | Users             | Groups              | Project Roles                          |
| ---------------- | ----------------- | ------------------- | -------------------------------------- |
| Mục đích         | Ngoại lệ / ad-hoc | Bộ người ổn định    | Phân quyền theo chức năng              |
| Phạm vi          | 1 user            | Toàn site           | 1 project (scheme tái sử dụng)         |
| Người quản lý    | Jira admin        | Org admin (AD/LDAP) | Jira admin gán role, project admin cấp |
| Khả năng mở rộng | Kém               | Tốt                 | Xuất sắc                               |
| Ví dụ            | Contractor cụ thể | `engineering-team`  | "Sprint Lead", "Viewer"                |

**Khuyến nghị:** groups cho global access; roles cho project scope; users chỉ
cho ngoại lệ. Tránh gán user trực tiếp vào scheme (khó audit, khó mở rộng).

---

## 7. Team-managed vs Company-managed Projects

| Tính năng               | Team-managed                   | Company-managed                     |
| ----------------------- | ------------------------------ | ----------------------------------- |
| Mô hình quyền           | Access levels + roles đơn giản | Permission schemes + custom roles   |
| Chuẩn hóa workflow      | Riêng từng project             | Chia sẻ giữa các project            |
| Issue security scheme   | Không                          | Có                                  |
| Custom field per scheme | Hạn chế                        | Đầy đủ                              |
| Cần Jira admin          | Không (team tự quản)           | Có                                  |
| Phù hợp                 | Team nhỏ, tự chủ               | Doanh nghiệp, cần chuẩn hóa & audit |

---

## 8. Best Practices

- **Least privilege:** cấp đủ quyền để làm việc, không dư (đặc biệt là
  _Administer Jira_).
- Global permission dùng **groups**; project permission dùng **roles**.
- Tổ chức group theo team/chức năng (`qa-team`) chứ không theo project.
- Không gán "Anyone" cho _Browse_ nếu project nhạy cảm.
- **Audit định kỳ:** rà soát group membership, role assignment, security scheme
  và Audit Logs (Site admin → Security → Audit Logs).
- Quy mô lớn: quản lý group tập trung qua LDAP/AD, dùng scheme chuẩn tái sử
  dụng, ủy quyền role cho project admin.

Anti-pattern cần tránh:

```
# Sai — gán user trực tiếp
Create Issues → user "john@example.com"

# Đúng — gán qua role
Create Issues → "Members" role   (project admin thêm john vào Members)
```

---

## 9. REST API (Jira Cloud v3)

**Permission schemes:**

```
GET    /rest/api/3/permissionscheme
GET    /rest/api/3/permissionscheme/{id}
POST   /rest/api/3/permissionscheme
PUT    /rest/api/3/permissionscheme/{id}
DELETE /rest/api/3/permissionscheme/{id}
```

**Permission grants (trong scheme):**

```
GET    /rest/api/3/permissionscheme/{id}/permission
POST   /rest/api/3/permissionscheme/{id}/permission
DELETE /rest/api/3/permissionscheme/{id}/permission/{permissionId}
```

**Project roles & actors:**

```
GET    /rest/api/3/project/{projectIdOrKey}/roles
GET    /rest/api/3/project/{projectIdOrKey}/roles/{id}
POST   /rest/api/3/project/{projectIdOrKey}/role/{id}      # thêm user/group
DELETE /rest/api/3/project/{projectIdOrKey}/role/{id}      # xóa actor
```

**Kiểm tra quyền:**

```
GET /rest/api/3/permissions              # liệt kê tất cả quyền
POST /rest/api/3/permissions/check       # kiểm tra quyền của user cho issue
GET /rest/api/3/user/permissions/search  # tìm user theo quyền
```

Lưu ý: xác thực bằng OAuth 2.0 Bearer token; rate limit ~300 request/phút mỗi
site; ưu tiên API v3 hơn v2.

---

## 10. Bảng permission key tích hợp sẵn (Built-in)

Đây là các **key** (định danh chuỗi) dùng trong permission scheme, REST API
(`GET /rest/api/3/permissions`), workflow condition và khi kiểm tra quyền. Giữ
nguyên key + display name tiếng Anh đúng như Atlassian định nghĩa.

### 10.1 Global permissions

| Key                                      | Display name                      | Kiểm soát                                         |
| ---------------------------------------- | --------------------------------- | ------------------------------------------------- |
| `ADMINISTER`                             | Jira System Administrators        | Toàn bộ chức năng quản trị hệ thống (Data Center) |
| `ADMINISTER_JIRA` / `ADMINISTER` (Cloud) | Jira Administrators               | Hầu hết chức năng quản trị Jira                   |
| `BULK_CHANGE`                            | Bulk Change                       | Sửa/di chuyển/xóa/transition nhiều issue cùng lúc |
| `CREATE_SHARED_OBJECTS`                  | Create shared objects             | Chia sẻ filter/dashboard công khai; tạo board     |
| `MANAGE_GROUP_FILTER_SUBSCRIPTIONS`      | Manage group filter subscriptions | Tạo/xóa subscription filter cho nhóm              |
| `USER_PICKER` / `BROWSE_USERS`           | Browse users and groups           | Xem danh sách user/group trong picker, @mention   |
| `SYSTEM_ADMIN`                           | Jira System Administrators        | Cấu hình cấp hệ thống (Data Center)               |

### 10.2 Project permissions — Administration

| Key                             | Display name              | Kiểm soát                                          |
| ------------------------------- | ------------------------- | -------------------------------------------------- |
| `ADMINISTER_PROJECTS`           | Administer Projects       | Quản lý role, component, version, chi tiết project |
| `BROWSE_PROJECTS`               | Browse Projects           | Xem project, dùng Issue Navigator, xem issue       |
| `EDIT_WORKFLOW`                 | Edit workflow             | Chỉnh sửa workflow của project                     |
| `EDIT_ISSUE_LAYOUT`             | Edit issue layout         | Chỉnh sửa bố cục issue                             |
| `VIEW_READONLY_WORKFLOW`        | View (read-only) workflow | Xem workflow chế độ chỉ đọc                        |
| `VIEW_DEV_TOOLS`                | View development tools    | Xem Development panel (Jira Software)              |
| `VIEW_PROJECTS` / `VIEW_ISSUES` | View Issues               | Xem issue trong project                            |
| `SERVICEDESK_AGENT`             | Service Desk Agent        | Truy cập/quản lý Service Desk project              |

### 10.3 Issue permissions

| Key                  | Display name       | Kiểm soát                                        |
| -------------------- | ------------------ | ------------------------------------------------ |
| `CREATE_ISSUES`      | Create Issues      | Tạo issue / sub-task                             |
| `EDIT_ISSUES`        | Edit Issues        | Chỉnh sửa issue                                  |
| `DELETE_ISSUES`      | Delete Issues      | Xóa issue (kèm comment/attachment)               |
| `ASSIGN_ISSUES`      | Assign Issues      | Gán issue cho người khác                         |
| `ASSIGNABLE_USER`    | Assignable User    | Được phép làm assignee (không tự gán người khác) |
| `RESOLVE_ISSUES`     | Resolve Issues     | Resolve/reopen issue, đặt Fix version            |
| `CLOSE_ISSUES`       | Close Issues       | Đóng issue theo workflow                         |
| `TRANSITION_ISSUES`  | Transition Issues  | Thay đổi status của issue                        |
| `LINK_ISSUES`        | Link Issues        | Tạo liên kết giữa các issue                      |
| `MOVE_ISSUES`        | Move Issues        | Di chuyển issue giữa project/workflow            |
| `SCHEDULE_ISSUES`    | Schedule Issues    | Đặt/sửa Due Date                                 |
| `MODIFY_REPORTER`    | Modify Reporter    | Đổi Reporter của issue                           |
| `SET_ISSUE_SECURITY` | Set Issue Security | Đặt security level cho issue                     |

### 10.4 Voters & Watchers

| Key                        | Display name             | Kiểm soát                         |
| -------------------------- | ------------------------ | --------------------------------- |
| `MANAGE_WATCHERS`          | Manage Watchers          | Xem/thêm/xóa người theo dõi issue |
| `VIEW_VOTERS_AND_WATCHERS` | View Voters and Watchers | Xem danh sách voter và watcher    |

### 10.5 Comments

| Key                   | Display name        | Kiểm soát                  |
| --------------------- | ------------------- | -------------------------- |
| `ADD_COMMENTS`        | Add Comments        | Thêm comment               |
| `EDIT_OWN_COMMENTS`   | Edit Own Comments   | Sửa comment của chính mình |
| `EDIT_ALL_COMMENTS`   | Edit All Comments   | Sửa mọi comment            |
| `DELETE_OWN_COMMENTS` | Delete Own Comments | Xóa comment của chính mình |
| `DELETE_ALL_COMMENTS` | Delete All Comments | Xóa mọi comment            |

### 10.6 Attachments

| Key                      | Display name           | Kiểm soát              |
| ------------------------ | ---------------------- | ---------------------- |
| `CREATE_ATTACHMENTS`     | Create Attachments     | Đính kèm file          |
| `DELETE_OWN_ATTACHMENTS` | Delete Own Attachments | Xóa file mình đính kèm |
| `DELETE_ALL_ATTACHMENTS` | Delete All Attachments | Xóa mọi file đính kèm  |

### 10.7 Time Tracking (Worklog)

| Key                   | Display name        | Kiểm soát                        |
| --------------------- | ------------------- | -------------------------------- |
| `WORK_ON_ISSUES`      | Work On Issues      | Ghi nhận time tracking cho issue |
| `EDIT_OWN_WORKLOGS`   | Edit Own Worklogs   | Sửa worklog của chính mình       |
| `EDIT_ALL_WORKLOGS`   | Edit All Worklogs   | Sửa mọi worklog                  |
| `DELETE_OWN_WORKLOGS` | Delete Own Worklogs | Xóa worklog của chính mình       |
| `DELETE_ALL_WORKLOGS` | Delete All Worklogs | Xóa mọi worklog                  |

**Phụ thuộc thường gặp:** `RESOLVE_ISSUES` cần `TRANSITION_ISSUES`;
`WORK_ON_ISSUES` cần `BROWSE_PROJECTS`.

Ví dụ kiểm tra quyền qua API:

```json
POST /rest/api/3/permissions/check
{
  "accountId": "5b10a2844c20165700ede21g",
  "globalPermissions": ["ADMINISTER"],
  "projectPermissions": [
    { "permissions": ["EDIT_ISSUES"], "projects": [10001] }
  ]
}
```

---

## 11. Khác biệt Cloud vs Data Center / Server

| Khía cạnh             | Cloud                | Data Center / Server       |
| --------------------- | -------------------- | -------------------------- |
| Global permission     | Quản lý ở site admin | Tách System Admin vs Admin |
| Quản lý người dùng    | Atlassian Directory  | LDAP / AD / Crowd          |
| Permission scheme     | Chỉ company-managed  | Mọi project                |
| Issue security scheme | Có                   | Có (chi tiết hơn)          |
| Audit log             | Giới hạn             | Đầy đủ + webhook           |
| API                   | REST v3              | REST v2/v3 + add-on        |

> **Free Jira Cloud** không có: permission schemes, project roles, issue
> security schemes. Cần nâng cấp gói để phân quyền chi tiết.

---

## 12. Tham khảo

- What are permission schemes in Jira? —
  https://support.atlassian.com/jira-cloud-administration/docs/what-are-permission-schemes-in-jira/
- Types of permissions in Jira —
  https://support.atlassian.com/jira-cloud-administration/docs/types-of-permissions-in-jira/
- Global permissions —
  https://support.atlassian.com/jira-cloud-administration/docs/what-are-global-permissions-and-what-do-they-do/
- How to use project/space roles —
  https://support.atlassian.com/jira-cloud-administration/docs/how-to-use-space-roles/
- Permissions for company-managed projects —
  https://support.atlassian.com/jira-cloud-administration/docs/permissions-for-company-managed-projects/
- What are issue/work item security schemes? —
  https://support.atlassian.com/jira-cloud-administration/docs/what-are-work-item-security-schemes/
- Team-managed vs company-managed projects —
  https://support.atlassian.com/jira-software-cloud/docs/what-are-team-managed-and-company-managed-projects/
- Manage access to team-managed project —
  https://support.atlassian.com/jira-software-cloud/docs/manage-how-people-access-your-team-managed-project/
- Permission schemes (REST API v3) —
  https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-permission-schemes/
- Project roles (REST API v3) —
  https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-project-roles/
- Permissions (REST API v3) —
  https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-permissions/
- Get all permissions (REST API v3) —
  https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-permissions/#api-rest-api-3-permissions-get
- Managing project permissions (Data Center) —
  https://confluence.atlassian.com/adminjiraserver/managing-project-permissions-938847145.html
- Managing global permissions (Data Center) —
  https://confluence.atlassian.com/adminjiraserver/managing-global-permissions-938847142.html
- Permissions overview (Jira Software DC) —
  https://confluence.atlassian.com/jirasoftwareserver/permissions-overview-939938996.html
- Administering Jira Data Center vs Cloud —
  https://support.atlassian.com/migration/docs/differences-administering-jira-data-center-and-cloud/
- Permissions best practices —
  https://confluence.atlassian.com/spaces/SECURITY/pages/1409093142/Permissions+best+practices
- Auditing in Jira —
  https://confluence.atlassian.com/spaces/SECURITY/pages/1409092970/Auditing+in+Jira
