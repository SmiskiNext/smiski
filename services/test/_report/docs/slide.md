# HƯỚNG DẪN CHO DEEPSEEK — TẠO SLIDE BẢO VỆ ĐỒ ÁN BẰNG HTML

## 1. Mục tiêu

Hãy tạo một **slide deck bảo vệ đồ án thực tập tốt nghiệp bằng HTML/CSS/JavaScript**, có thể mở trực tiếp bằng trình duyệt.

Slide phải phục vụ **bảo vệ trước giảng viên phản biện**, trong đó giảng viên đánh giá rất chặt theo các chuẩn đầu ra CLO.

Sinh viên trong dự án giữ vai trò chính là **Business Analyst (BA)**, vì vậy trọng tâm của slide phải là:

> **Nghiên cứu bối cảnh → phân tích hiện trạng → xác định vấn đề → phân tích nguyên nhân → xác định nhu cầu nghiệp vụ → đề xuất giải pháp → phân tích yêu cầu → mô hình hóa → phối hợp triển khai → kiểm thử → đánh giá → thể hiện đóng góp cá nhân.**

Không được biến bài trình bày thành một bài giới thiệu công nghệ thuần túy.

---

# 2. CẤU TRÚC THƯ MỤC HIỆN TẠI

Dự án có cấu trúc tương tự:

```text
services/
├── notification/
├── proto/
├── record/
├── shared/
├── tenant/
└── test/
    └── report/
        ├── docs/
        │   ├── LeHaThanh_C40_... 
        │   └── slide.md
        │
        └── image/
            ├── hiện trạng.png
            ├── luồng kiểm thử ...
            ├── luồng xử lý của ...
            ├── môi liên hệ.png
            ├── phạm vi đề tài.png
            ├── sơ đồ hệ thống.png
            ├── sơ đồ tổng hợp use-case.png
            ├── sơ đồ tuần tự join...
            ├── TC-01 Thời gian t...
            ├── TC-02 Biểu đồ ...
            ├── TC-02 Chỉ số pack...
            ├── TC-02 Kết quả đo...
            ├── TC-02 Chỉ số ...
            ├── TC-03 Biểu đồ ...
            ├── TC-03 Biểu đồ ...
            ├── TC-04 Biểu đồ ...
            ├── TC-04 Biểu đồ ...
            ├── TC-05 Biểu đồ ...
            ├── TC-05 Biểu đồ ...
            ├── thiết kế hệ thống...
            ├── tương tác giữa us...
            └── ...
```

### Quy tắc quan trọng về thư mục

DeepSeek phải **tận dụng các hình ảnh đã có trong thư mục `image/`**, không tự vẽ lại nếu hình hiện tại đã phù hợp.

File HTML được khuyến nghị đặt tại:

```text
services/test/report/docs/index.html
```

hoặc nếu muốn:

```text
services/test/report/docs/slide.html
```

Khi HTML nằm trong `docs/`, đường dẫn tới hình ảnh phải sử dụng:

```html
../image/<ten-file>
```

Ví dụ:

```html
<img src="../image/phạm vi đề tài.png" alt="Phạm vi đề tài">
```

Nếu tên file có dấu hoặc khoảng trắng, phải giữ nguyên tên file thực tế và kiểm tra đường dẫn.

**Không được tự ý đổi tên file hình ảnh.**

---

# 3. NGUỒN DỮ LIỆU ĐỂ TẠO SLIDE

Ưu tiên sử dụng dữ liệu theo thứ tự:

### Nguồn 1 — `docs/slide.md`

Đây là nguồn nội dung chính nếu file có sẵn.

Phải đọc và sử dụng nội dung trong:

```text
services/test/report/docs/slide.md
```

### Nguồn 2 — Tài liệu báo cáo

Nếu có file báo cáo trong:

```text
services/test/report/docs/
```

hãy sử dụng để bổ sung nội dung khi cần.

### Nguồn 3 — Hình ảnh trong `image/`

Các hình ảnh là evidence trực quan cho:
- hiện trạng;
- phạm vi;
- kiến trúc;
- use case;
- sequence;
- testing;
- kết quả kiểm thử;
- UI;
- các flow nghiệp vụ.

### Nguyên tắc

Không tự bịa số liệu, kết quả, requirement, business rule hoặc chức năng không có trong tài liệu nguồn.

Nếu thiếu thông tin:

> Để placeholder rõ ràng `[CẦN BỔ SUNG]`

thay vì tự tạo dữ liệu.

---

# 4. CHUẨN ĐẦU RA CLO PHẢI ĐƯỢC PHẢN ÁNH

## CLO1 — Đạo đức và trách nhiệm nghề nghiệp

Phải thể hiện được:
- trách nhiệm với phần việc cá nhân;
- ownership;
- kế hoạch và quá trình thực hiện;
- cách xử lý khó khăn/sai sót;
- tính trung thực và minh bạch;
- tuân thủ quy định nghề nghiệp;
- bảo mật/thông tin người dùng nếu có liên quan.

### Minh chứng cần có

Một slide hoặc một phần rõ ràng về:

> **Vai trò và đóng góp cá nhân**

và:

> **Khó khăn — Cách xử lý — Kết quả**

---

# 5. CLO2 — PHÂN TÍCH BÀI TOÁN

Đây là CLO cực kỳ quan trọng với vai trò BA.

Slide phải chứng minh được:

```text
BỐI CẢNH
   ↓
HIỆN TRẠNG AS-IS
   ↓
PAIN POINT
   ↓
ROOT CAUSE
   ↓
BUSINESS NEED
   ↓
REQUIREMENT
   ↓
SOLUTION
```

Không được nhảy trực tiếp:

```text
Problem → LiveKit
```

Mà phải thể hiện:

```text
Business Problem
→ Business Need
→ Requirement
→ Solution
→ Technology
```

## PI2.1

Phải trả lời:
- Bài toán là gì?
- Ai gặp vấn đề?
- Vấn đề phát sinh ở đâu?
- Nguyên nhân là gì?
- Tác động là gì?
- Phạm vi bài toán là gì?

## PI2.2

Phải trả lời:
- Có những nhu cầu nào?
- Giải pháp nào được lựa chọn?
- Vì sao chọn giải pháp?
- Giải pháp đáp ứng requirement nào?
- Vì sao công nghệ được chọn phù hợp?

---

# 6. CLO3 — GIAO TIẾP VÀ LÀM VIỆC NHÓM

Phải thể hiện:
- vai trò BA;
- cách BA phối hợp với Developer;
- cách trao đổi requirement;
- cách review;
- cách thống nhất Business Rule;
- cách xử lý bất đồng;
- phần việc chủ động thực hiện.

Nếu có evidence trong repository, có thể sử dụng:
- task;
- commit;
- issue;
- document;
- review;
- test case.

Không được nhận công việc của thành viên khác là đóng góp cá nhân.

---

# 7. CLO4 — THIẾT KẾ VÀ MÔ HÌNH HÓA

Phải thể hiện:
- Use Case Diagram;
- Activity Diagram;
- Sequence Diagram;
- State Diagram;
- Architecture;
- các mô hình phù hợp khác.

Mỗi sơ đồ phải có mục đích rõ ràng.

Không chỉ đặt hình lên slide.

Ví dụ:

> **Use Case:** xác định Actor và phạm vi chức năng.

> **Activity:** mô tả workflow nghiệp vụ.

> **State:** mô tả lifecycle của Meeting.

> **Sequence:** mô tả tương tác giữa các thành phần.

---

# 8. CLO5 — PHÁT TRIỂN, TRIỂN KHAI VÀ VẬN HÀNH

Phải thể hiện:
- công đoạn phát triển đã tham gia;
- tích hợp hệ thống;
- triển khai;
- cấu hình;
- kiểm thử;
- vận hành;
- xử lý vấn đề phát sinh.

Không cần trình bày code dài.

Ưu tiên:

```text
Development
    ↓
Integration
    ↓
Deployment
    ↓
Testing
    ↓
Operation
```

---

# 9. CẤU TRÚC SLIDE BẮT BUỘC

Tạo khoảng **16–19 slide**, ưu tiên 17–18 slide nếu nội dung đủ.

## SLIDE 01 — TITLE

Tên đề tài:

**Thiết kế và xây dựng Module Hội họp Trực tuyến tích hợp vào hệ thống quản lý dự án sử dụng LiveKit**

Thông tin:
- Sinh viên;
- MSSV;
- Lớp;
- Giảng viên;
- Đơn vị thực tập;
- Vai trò: **Business Analyst**.

Thiết kế tối giản, chuyên nghiệp.

---

## SLIDE 02 — BỐI CẢNH

Tiêu đề:

> **01. Bối cảnh và nhu cầu thực tế**

Thể hiện:
- quản lý dự án phần mềm;
- Issue/Task/Work Item;
- nhu cầu trao đổi trực tuyến;
- vấn đề Meeting tồn tại tách khỏi context của công việc.

Ưu tiên sử dụng hình:

```text
../image/phạm vi đề tài.png
```

nếu phù hợp với nội dung thực tế.

---

## SLIDE 03 — HIỆN TRẠNG AS-IS

Tiêu đề:

> **02. Phân tích hiện trạng — AS-IS**

Thể hiện workflow hiện tại.

Ví dụ:

```text
Issue phát sinh
      ↓
Trao đổi trên Jira
      ↓
Cần họp
      ↓
Mở công cụ Meeting bên ngoài
      ↓
Tạo Meeting
      ↓
Gửi Meeting Link
      ↓
Tham gia
      ↓
Quay lại Jira
```

Ưu tiên sử dụng hình hiện trạng đã có trong:

```text
../image/hiện trạng.png
```

nếu hình này đúng với nội dung.

---

## SLIDE 04 — PAIN POINT + ROOT CAUSE

Tiêu đề:

> **03. Vấn đề và nguyên nhân gốc rễ**

Chia hai vùng:

### Pain Point
- Meeting tách khỏi Issue.
- Quy trình phân tán.
- Khó quản lý lifecycle.
- Khó truy xuất lịch sử.
- Khó kiểm soát quyền.

### Root Cause

Nhấn mạnh:

> **Meeting chưa được quản lý trong cùng ngữ cảnh với Issue.**

Đây là slide BA quan trọng nhất.

---

## SLIDE 05 — BUSINESS NEED

Tiêu đề:

> **04. Chuyển hóa vấn đề thành nhu cầu nghiệp vụ**

Bảng:

| Pain Point | Business Need |
|---|---|
| Mất ngữ cảnh | Gắn Meeting với Issue |
| Quy trình phân tán | Tạo/Join Meeting từ Jira |
| Khó quản lý | Quản lý lifecycle |
| Khó kiểm soát | Permission |
| Khó truy xuất | Meeting History |

Phải thể hiện được tư duy:

> Problem → Need

---

## SLIDE 06 — OBJECTIVE + SCOPE

Tiêu đề:

> **05. Mục tiêu và phạm vi**

Chia:
- Objectives;
- In Scope;
- Out of Scope.

Không đưa quá nhiều text.

Nếu có hình phạm vi phù hợp, dùng:

```text
../image/phạm vi đề tài.png
```

---

## SLIDE 07 — STAKEHOLDER / ACTOR

Tiêu đề:

> **06. Stakeholder và vai trò người dùng**

Thể hiện:
- Host;
- Participant;
- các stakeholder phù hợp.

Nếu có sơ đồ Actor/Use Case trong:

```text
../image/sơ đồ tổng hợp use-case.png
```

hoặc hình tương ứng, sử dụng lại.

---

## SLIDE 08 — REQUIREMENT + BUSINESS RULE

Tiêu đề:

> **07. Phân tích yêu cầu và quy tắc nghiệp vụ**

### Functional Requirement

Chọn các chức năng chính:
- Create;
- Schedule;
- Start;
- Join;
- Edit;
- Cancel;
- End;
- View History.

### Business Rule quan trọng

Ví dụ:
- Một Issue chỉ có tối đa một Active Meeting.
- Creator là Host.
- Chỉ Host được End Meeting.
- Permission phải được kiểm tra trước các thao tác tương ứng.

Chỉ sử dụng rule thực tế có trong tài liệu nguồn.

---

## SLIDE 09 — AS-IS → TO-BE

Tiêu đề:

> **08. Giải pháp đề xuất — TO-BE**

Bên trái:

```text
AS-IS
Jira → External Meeting → Jira
```

Bên phải:

```text
TO-BE
Jira Issue
   ↓
Issue Panel
   ↓
Meeting
   ↓
Meeting Room
   ↓
LiveKit
```

Thông điệp:

> **Đưa Meeting vào cùng context với Issue.**

---

## SLIDE 10 — LÝ DO LỰA CHỌN GIẢI PHÁP

Tiêu đề:

> **09. Đánh giá và lựa chọn giải pháp**

Có thể dùng bảng:

| Tiêu chí | Công cụ độc lập | Module tích hợp |
|---|---|---|
| Gắn với Issue | Hạn chế | Có |
| Lifecycle | Hạn chế | Có |
| Permission theo context | Hạn chế | Có |
| Join từ Jira | Không | Có |
| Meeting History theo Issue | Hạn chế | Có |
| RTC | Có | LiveKit |

Không được tuyên bố tuyệt đối rằng giải pháp mới “tốt hơn mọi công cụ khác”.

Cách diễn đạt:

> **Giải pháp phù hợp hơn với bài toán quản lý Meeting trong ngữ cảnh Jira Issue.**

---

## SLIDE 11 — KIẾN TRÚC HỆ THỐNG

Tiêu đề:

> **10. Kiến trúc giải pháp**

Sử dụng hình kiến trúc hiện có nếu có:

```text
../image/sơ đồ hệ thống.png
../image/thiết kế hệ thống...
```

Chỉ dùng hình đúng tên/nội dung thực tế.

Kiến trúc hiện tại cần phản ánh đúng tài liệu dự án, ví dụ:

```text
Jira Cloud
    ↓
Forge App
    ↓
Forge Remote
    ↓
Envoy Gateway
    ↓
Internal Services
    ├── Meet Service
    └── Notification Service
         ↓
      Database

Meet Service
    ↓
LiveKit
    ↓
Realtime Media
```

Không tự thêm service không tồn tại trong kiến trúc hiện tại.

---

## SLIDE 12 — MÔ HÌNH HÓA

Tiêu đề:

> **11. Mô hình hóa hệ thống**

Chọn tối đa 3 hình quan trọng:
- Use Case;
- Activity;
- State.

Có thể sử dụng hình đã có trong `../image/`.

Mỗi hình có caption ngắn:

**Use Case:** phạm vi chức năng.

**Activity:** workflow nghiệp vụ.

**State:** lifecycle Meeting.

---

## SLIDE 13 — SEQUENCE / NGHIỆP VỤ TIÊU BIỂU

Tiêu đề:

> **12. Luồng xử lý nghiệp vụ tiêu biểu**

Ưu tiên nghiệp vụ Join Meeting nếu có đầy đủ evidence.

Ví dụ:

```text
User
 ↓
Issue Panel
 ↓
Forge
 ↓
Meet Service
 ↓
Authorization
 ↓
LiveKit Token
 ↓
LiveKit
 ↓
Meeting Room
```

Nếu có hình sequence trong `../image/`, sử dụng hình đó thay vì tự vẽ.

---

## SLIDE 14 — TRIỂN KHAI / VẬN HÀNH

Tiêu đề:

> **13. Triển khai và vận hành**

Thể hiện:

```text
Build
 ↓
Configuration
 ↓
Deployment
 ↓
Service
 ↓
Database
 ↓
LiveKit
 ↓
Forge/Jira
 ↓
Verification
```

Nếu tài liệu nguồn có hình triển khai, ưu tiên dùng hình đó.

---

## SLIDE 15 — KIỂM THỬ

Tiêu đề:

> **14. Kiểm thử và đánh giá**

Chọn 5–7 test case quan trọng.

Ưu tiên:
- Happy path;
- Authorization;
- Business Rule;
- Negative test;
- Lifecycle.

Ví dụ:

| Test | Expected |
|---|---|
| Host tạo Meeting | Success |
| Participant Join | Success |
| Unauthorized Join | Denied |
| Participant End Meeting | Denied |
| Host End Meeting | Success |
| Duplicate Active Meeting | Denied |
| Cancel Meeting | Success |

Nếu thư mục `image/` có các ảnh:

```text
TC-01...
TC-02...
TC-03...
TC-04...
TC-05...
```

hãy lựa chọn các hình có giá trị chứng minh cao nhất.

Không nhét tất cả ảnh test vào một slide.

---

## SLIDE 16 — KẾT QUẢ

Tiêu đề:

> **15. Kết quả đạt được**

Chia:

### Business
- Meeting gắn với Issue.
- Workflow tập trung.
- Lifecycle.
- Permission.
- History.

### Technical
- Jira Forge.
- LiveKit.
- Backend.
- Database.
- Gateway.

### Product
- Issue Panel.
- Meeting Room.
- Meeting History.

Chỉ sử dụng kết quả thực tế được chứng minh bởi tài liệu/hình ảnh.

---

## SLIDE 17 — ĐÓNG GÓP CÁ NHÂN — BA

Tiêu đề:

> **16. Vai trò và đóng góp cá nhân**

Đây là slide **rất quan trọng để bảo vệ CLO1 và CLO3**.

Thiết kế dạng timeline:

```text
RESEARCH
   ↓
ANALYSIS
   ↓
REQUIREMENT
   ↓
MODELING
   ↓
VALIDATION
```

### Research
- Nghiên cứu bối cảnh.
- Nghiên cứu giải pháp.
- Thu thập thông tin.

### Analysis
- AS-IS.
- Pain Point.
- Root Cause.
- Business Need.

### Requirement
- FR/NFR.
- Business Rule.
- Permission.

### Modeling
- Use Case.
- Activity.
- Sequence.
- State.

### Validation
- Review.
- Test Scenario.
- Kiểm tra requirement với solution.

Nếu có evidence trong repository, hãy dẫn chiếu đến:
- task;
- document;
- commit;
- issue;
- test case.

Không nhận phần việc của thành viên khác.

---

## SLIDE 18 — KHÓ KHĂN VÀ CÁCH XỬ LÝ

Tiêu đề:

> **17. Khó khăn, sai sót và cách xử lý**

Bảng:

| Khó khăn | Phân tích | Cách xử lý | Kết quả |
|---|---|---|---|
| Requirement chưa rõ | Review | Làm rõ Business Rule | Requirement rõ hơn |
| Lifecycle phức tạp | Phân tích State | Chuẩn hóa State | Flow nhất quán |
| Permission phức tạp | Tách Permission/Ownership | Bổ sung rule | Authorization rõ |
| Tích hợp RTC | Phân tách responsibility | Xác định flow | Tích hợp ổn định |

Chỉ dùng vấn đề thực tế có trong project.

Nếu không có evidence thì ghi:

`[CẦN BỔ SUNG EVIDENCE]`

Không bịa.

---

## SLIDE 19 — TỔNG KẾT THEO CLO

Tiêu đề:

> **18. Tổng kết theo chuẩn đầu ra**

| CLO | Minh chứng |
|---|---|
| CLO1 | Ownership, trách nhiệm, minh bạch, xử lý khó khăn |
| CLO2 | AS-IS, Pain Point, Root Cause, Business Need, Solution |
| CLO3 | Giao tiếp và phối hợp nhóm |
| CLO4 | Use Case, Activity, Sequence, State, Architecture |
| CLO5 | Development, Deployment, Testing, Operation |

Câu kết:

> **Từ bài toán nghiệp vụ → yêu cầu → giải pháp → thiết kế → triển khai → kiểm thử và đánh giá.**

---

# 10. YÊU CẦU THIẾT KẾ HTML

## 10.1. Tỷ lệ

Dùng:

```css
aspect-ratio: 16 / 9;
```

Kích thước slide:

```text
1280 × 720
```

Mỗi slide là một `.slide`.

---

## 10.2. Navigation

HTML phải có:

- Previous.
- Next.
- số slide hiện tại / tổng slide.
- bàn phím:
  - ArrowLeft;
  - ArrowRight;
  - PageUp;
  - PageDown;
  - Home;
  - End.
- Fullscreen.

---

## 10.3. Animation

Animation vừa phải:

- fade;
- slide-up;
- scale nhẹ.

Không dùng animation quá mạnh.

Không làm mất thời gian trình bày.

---

# 11. PHONG CÁCH THỊ GIÁC

## Tổng thể

Phong cách:

> **Academic + Professional + Modern Software Engineering**

Không dùng phong cách:
- gaming;
- neon;
- quá nhiều gradient;
- quá nhiều icon;
- infographic màu mè.

### Màu

Nên sử dụng:
- nền trắng hoặc rất sáng;
- xanh navy/xanh đậm làm màu chủ đạo;
- xanh dương làm accent;
- xám cho secondary text.

Có thể dùng một màu accent thống nhất cho:
- highlight;
- số slide;
- đường nối;
- keyword.

---

# 12. QUY TẮC TYPOGRAPHY

### Title

Lớn, rõ:

```css
font-size: 32–40px;
font-weight: 700;
```

### Body

```css
font-size: 18–24px;
```

### Caption

```css
font-size: 13–16px;
```

Không dùng font quá nhỏ.

Không để người xem phải đọc paragraph dài.

---

# 13. QUY TẮC HÌNH ẢNH

Nếu đã có hình trong:

```text
services/test/report/image/
```

thì ưu tiên dùng hình đó.

Không:
- crop sai nội dung;
- bóp méo tỷ lệ;
- làm ảnh quá nhỏ;
- phủ text lên ảnh khó đọc.

Có thể đặt ảnh trong card:

```text
[ image ]
caption
```

---

# 14. QUY TẮC NỘI DUNG

## Một slide = một thông điệp

Không đưa quá nhiều ý.

### Không tốt

Một slide chứa:
- bối cảnh;
- requirement;
- architecture;
- database;
- API;
- test.

### Tốt

Một slide chỉ trả lời:

> “Vấn đề hiện tại là gì?”

hoặc:

> “Tại sao chọn giải pháp này?”

---

# 15. QUY TẮC ĐẶC BIỆT CHO VAI TRÒ BA

Trong toàn bộ deck phải duy trì logic:

```text
Context
 ↓
Current State
 ↓
Problem
 ↓
Root Cause
 ↓
Need
 ↓
Requirement
 ↓
Business Rule
 ↓
Solution
 ↓
Design
 ↓
Implementation
 ↓
Validation
```

DeepSeek phải đảm bảo người xem có thể nhìn thấy rõ:

> **Sinh viên hiểu bài toán trước khi nói về công nghệ.**

---

# 16. KHÔNG ĐƯỢC LÀM

Không được:

1. Tự bịa số liệu.
2. Tự bịa kết quả kiểm thử.
3. Tự bịa requirement.
4. Tự bịa đóng góp cá nhân.
5. Tự bịa business rule.
6. Tự thêm service không có trong kiến trúc nguồn.
7. Đưa quá nhiều code lên slide.
8. Đưa nguyên văn paragraph dài từ báo cáo.
9. Chỉ giới thiệu công nghệ mà không giải thích business problem.
10. Gắn CLO một cách máy móc lên mọi slide.
11. Dùng quá nhiều animation.
12. Dùng hình ảnh không có trong project nếu không cần thiết.

---

# 17. CÁCH XỬ LÝ THIẾU THÔNG TIN

Nếu nội dung nguồn không đủ để tạo một phần:

```text
[CẦN BỔ SUNG]
```

hoặc:

```text
[THIẾU EVIDENCE]
```

Không tự suy diễn thành thông tin thực tế.

---

# 18. OUTPUT BẮT BUỘC

Hãy tạo:

```text
services/test/report/docs/index.html
```

HTML phải chạy độc lập trong trình duyệt.

Nếu cần CSS/JS riêng thì có thể tạo:

```text
services/test/report/docs/
├── index.html
├── style.css
└── script.js
```

Nhưng ưu tiên **một file HTML hoàn chỉnh** để dễ mở và demo.

HTML phải sử dụng hình ảnh từ:

```text
services/test/report/image/
```

với đường dẫn tương đối chính xác.

---

# 19. CHECKLIST TRƯỚC KHI HOÀN THÀNH

Trước khi xuất HTML, hãy tự kiểm tra:

### Nội dung
- [ ] Có bối cảnh.
- [ ] Có AS-IS.
- [ ] Có Pain Point.
- [ ] Có Root Cause.
- [ ] Có Business Need.
- [ ] Có Objective.
- [ ] Có Scope.
- [ ] Có Stakeholder.
- [ ] Có Requirement.
- [ ] Có Business Rule.
- [ ] Có AS-IS → TO-BE.
- [ ] Có lý do lựa chọn solution.
- [ ] Có Architecture.
- [ ] Có Design Diagram.
- [ ] Có Deployment.
- [ ] Có Testing.
- [ ] Có Result.
- [ ] Có Personal Contribution.
- [ ] Có Difficulties.
- [ ] Có CLO Summary.

### CLO
- [ ] CLO1 có evidence.
- [ ] CLO2 thể hiện rõ năng lực BA.
- [ ] CLO3 có teamwork.
- [ ] CLO4 có mô hình hóa.
- [ ] CLO5 có development/deployment/testing/operation.

### HTML
- [ ] 16:9.
- [ ] Navigation hoạt động.
- [ ] Fullscreen hoạt động.
- [ ] Keyboard navigation hoạt động.
- [ ] Không lỗi đường dẫn ảnh.
- [ ] Không ảnh bị méo.
- [ ] Không text overflow.
- [ ] Không slide quá nhiều chữ.
- [ ] Font dễ đọc.
- [ ] Animation vừa phải.
- [ ] Có thể mở trực tiếp bằng trình duyệt.

---

# 20. MỤC TIÊU CUỐI CÙNG CỦA DECK

Sau khi xem slide, giảng viên phải có thể kết luận:

> **Sinh viên hiểu bối cảnh và bài toán thực tế.**

> **Sinh viên có khả năng phân tích nghiệp vụ từ hiện trạng đến nguyên nhân và nhu cầu.**

> **Sinh viên biết chuyển nhu cầu thành requirement và business rule.**

> **Sinh viên biết đề xuất và lý giải giải pháp.**

> **Sinh viên hiểu các mô hình thiết kế và biết liên kết chúng với requirement.**

> **Sinh viên có tham gia thực tế vào phát triển, triển khai, kiểm thử hoặc vận hành.**

> **Sinh viên xác định rõ phần việc cá nhân và có evidence.**

> **Sinh viên có khả năng giao tiếp, phối hợp và xử lý vấn đề trong nhóm.**

---

# 21. NGUYÊN TẮC QUAN TRỌNG NHẤT

Không làm slide theo tư duy:

> **“Em đã làm những công nghệ gì?”**

Mà phải làm theo tư duy:

> **“Em đã giải quyết bài toán gì, em phân tích nó như thế nào, tại sao giải pháp này phù hợp, em đã biến phân tích thành thiết kế và triển khai ra sao, và đâu là bằng chứng cho phần việc em thực sự thực hiện?”**

Đây là trọng tâm để bảo vệ đồ án trước giảng viên phản biện theo chuẩn CLO.
