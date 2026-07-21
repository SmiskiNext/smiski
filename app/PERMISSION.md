# Context: Permission Design — Online Meeting Module (Jira Forge + LiveKit)

> This file restates the existing permission design of the Meeting module (extracted from the
> graduation report, sections 1.4, 3.2, 3.3, 3.6, and the "Preconditions / Special requirements"
> parts of the UC-01 → UC-10 specifications).
> Purpose: give the AI full context to **correctly understand the permission model** before writing
> any configuration code (Jira custom permissions, backend authorization, frontend action visibility).
> This file does not prescribe a specific implementation process — it is reference material only.

---

## 1. System context

- The module is integrated into **Jira Cloud** via **Atlassian Forge** (Issue Panel + Project Page).
- Real-time media is handled by **LiveKit**.
- The backend consists of Spring Boot microservices (Meet Service, Record Service, Tenant Service,
  Notification Service), communicating through Kong Gateway.
- Authorization is based on **Jira custom permissions** (not hard-coded business-role names like
  Developer/Tester/BA). Permissions are configured through the Jira Permission Scheme and assigned
  to Project Roles / Users / Groups depending on the organization.

## 2. Core permission model

All Meeting business logic relies on only **2 custom permissions**:

| Permission | Meaning |
|---|---|
| **View Meeting** | View meeting info, view history, view detail, join a Running meeting, view recording if available. |
| **Edit Meeting** | Everything in View Meeting **plus** create meetings (instant/scheduled), start meetings, edit, cancel, end meetings, start/stop recording. |

**Containment relationship:** `Edit Meeting ⊇ View Meeting`. A user with Edit Meeting automatically
has every capability of View Meeting — the two permissions should not be treated as independent
parallel flags.

### Business actors / roles (descriptive only — NOT the authorization mechanism)

| Actor | Role |
|---|---|
| Jira Admin / Project Admin | Configures the app, declares custom permissions, sets up the permission scheme, assigns users to project roles. |
| Meeting Manager / Host | Person who manages meetings → maps to **Edit Meeting**. |
| Participant (Developer, Tester/QA, BA, Viewer...) | Person who attends/views meetings → maps to **View Meeting**. |

Important note: **"Host" is a per-meeting concept** (the creator of that specific meeting, stored in
`host_id` / the `HOST` role of the participation log), **distinct from permission**. A user with
Edit Meeting is allowed to End Meeting / manage a meeting **even if they are not the Host** of that
particular meeting — the report explicitly states: "the End Meeting right is controlled by the Edit
Meeting permission, the user is not required to be the Host." Therefore authorization logic must
never substitute a "current user == host_id" check for the actual Edit Meeting permission check.

## 3. Permission matrix by function

| Function | View Meeting | Edit Meeting |
|---|---|---|
| View meeting list | ✅ | ✅ |
| View meeting detail | ✅ | ✅ |
| View meeting history | ✅ | ✅ |
| Join a Running meeting | ✅ | ✅ |
| View recording | ✅ | ✅ |
| Search and filter meetings | ✅ | ✅ |
| Start Instant Meeting | ❌ | ✅ |
| Schedule Meeting | ❌ | ✅ |
| Manage Meeting (Edit/Cancel/End) | ❌ | ✅ |
| Start/Stop Recording | ❌ | ✅ |

## 4. Permission matrix by Use Case

| Use Case | Required permission |
|---|---|
| UC-01 Start Instant Meeting | Edit Meeting |
| UC-02 Schedule Meeting | Edit Meeting |
| UC-03 Start Scheduled Meeting | Edit Meeting |
| UC-04 Manage Meeting (Edit/Cancel/End) | Edit Meeting |
| UC-05 Join Meeting | View **or** Edit Meeting |
| UC-06 View Meeting Detail | View **or** Edit Meeting |
| UC-07 View Meeting History | View **or** Edit Meeting |
| UC-08 Record Meeting (Start/Stop) | Edit Meeting |
| UC-09 View Recording | View **or** Edit Meeting |
| UC-10 Search and Filter Meeting | View **or** Edit Meeting |

## 5. Action-visibility rules by Meeting state (permission × state)

A meeting has 4 states: `Scheduled → Running → Completed`, or `Scheduled → Canceled`.

| Meeting State | With View Meeting only | With Edit Meeting |
|---|---|---|
| **Scheduled** | View only (View Detail) | View Detail, Edit, Start, Cancel |
| **Running** | Join, View Detail | Join, View Detail, End, Start/Stop Recording |
| **Completed** | View Detail, View Recording (if any) | View Detail, View Recording (if any) |
| **Canceled** | View Detail | View Detail |

This is a **two-dimensional matrix**: which actions are visible/allowed = f(permission, current
meeting state). Example: a user with Edit Meeting must NOT see Edit/Cancel/End on a meeting that is
already Completed — the action is invalid for that state, regardless of having sufficient permission.

## 6. Additional business constraints tied to permission (from the UC specs)

These are **not permissions** themselves, but preconditions that must be checked alongside the
View/Edit Meeting check on the backend when executing an action:

- **UC-01 (Start Instant Meeting):** A user with Edit Meeting who is currently the Host of another
  Running meeting is NOT allowed to start a new instant meeting. An existing Scheduled meeting on
  the Issue does not block creating an Instant Meeting.
- **UC-02 (Schedule Meeting):** The scheduled time must not overlap with another meeting already
  scheduled **by that same user**. An Issue can have multiple Scheduled meetings at once; a Running
  meeting does not block scheduling a new one (since it's a future plan).
- **UC-03 (Start Scheduled Meeting):** Can only start when the meeting is in the Scheduled state,
  and the current user is not already the Host of another Running meeting.
- **UC-04 (Manage Meeting):** If End Meeting is triggered while Recording is active, the system must
  automatically Stop Recording first, then transition the meeting to Completed. Meetings in
  Completed/Canceled state cannot be Edited/Canceled/Ended even with Edit Meeting.
- **UC-05 (Join Meeting):** Joining is only allowed when the meeting is Running; other states
  (Scheduled, Completed, Canceled) do not allow Join regardless of permission.
- **UC-07 (View Meeting History):** Data scope depends on **where the user accesses it from**, not
  on permission: opened from the Issue Panel → history of the current Issue only; opened from the
  Project Page Dashboard → history of the entire Project. Only Completed/Canceled meetings appear in
  History.
- **UC-08 (Record Meeting):** Recording can only start while the meeting is Running; at any given
  time a meeting can have at most 1 active recording session.
- **UC-10 (Search and Filter):** In the Issue Panel, search is by Meeting Title only, scoped to the
  current Issue. In the Project Page Dashboard, filters extend to title/issue/creator/state, scoped
  to the entire Project, and filters are combined with AND logic.

## 7. Authorization principle (frontend vs. backend)

Per the original design (section 3.6.4):

> "Actions that don't match the user's permission or the meeting's current state must not be shown
> in the UI. In addition, **the backend must still re-check permission before executing the business
> logic**, to prevent unauthorized operations."

In other words:
- **Frontend (Issue Panel / Project Page — React inside Forge Custom UI):** only responsible for
  hiding/showing actions based on permission + state, for UX purposes — must NOT be treated as a
  security layer.
- **Backend (Meet Service, Record Service):** is where authorization is actually enforced. Every
  use case / API endpoint must re-validate: (a) the user has the correct custom permission
  (View/Edit Meeting) within that tenant's Jira site, (b) the meeting is currently in a state valid
  for that action, and (c) any additional business constraints from section 6 (if applicable) —
  before executing the operation.

## 8. Suggested mapping when configuring (reference only, not mandatory)

- In Jira, "View Meeting" and "Edit Meeting" should be declared as **2 separate custom permissions**
  in the Jira Permission Scheme (via Forge `permissions` / the project permission API), so that a
  Project Admin can assign them to appropriate Project Roles (e.g., `Meeting Manager` role → Edit
  Meeting, `Team Member` role → View Meeting).
- On the multi-tenant backend (each Jira site = 1 tenant, data partitioned by `tenant_id`),
  permission checks should verify against the correct `tenant_id` + caller `accountId` (not just a
  static JWT claim), since the permission scheme can change over time within a given site.
- Since Edit ⊇ View, the permission-check function should be designed as something like
  `hasAtLeast(user, MeetingPermission.VIEW)` and `hasAtLeast(user, MeetingPermission.EDIT)` rather
  than checking two independent boolean flags, to avoid the mistake of a user with Edit being
  incorrectly evaluated as not having View.