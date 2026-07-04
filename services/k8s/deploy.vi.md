# Zero Meeting System — Hướng Dẫn Triển Khai Kubernetes

Hướng dẫn này bao gồm toàn bộ quá trình triển khai hệ thống Zero Meeting System
(ZMS) trên cụm k3s, từ việc build image dịch vụ cho đến xác nhận hệ thống đang
chạy.

---

## Mục Lục

1. [Yêu Cầu Tiền Đề](#1-yêu-cầu-tiền-đề)
2. [Build Image Dịch Vụ](#2-build-image-dịch-vụ)
3. [Cấu Hình Secret](#3-cấu-hình-secret)
4. [Cài Đặt Helm Chart](#4-cài-đặt-helm-chart)
5. [Triển Khai Kafka Cluster và Topic](#5-triển-khai-kafka-cluster-và-topic)
6. [Áp Dụng Kustomize Overlay](#6-áp-dụng-kustomize-overlay)
7. [Xác Nhận Triển Khai](#7-xác-nhận-triển-khai)
8. [Truy Cập Dịch Vụ](#8-truy-cập-dịch-vụ)
9. [Tổng Quan Kiến Trúc](#9-tổng-quan-kiến-trúc)
10. [Xử Lý Sự Cố](#10-xử-lý-sự-cố)

---

## 1. Yêu Cầu Tiền Đề

Cài đặt các công cụ sau trước khi tiến hành.

<!-- markdownlint-disable MD060 -->

| Công Cụ              | Ghi Chú                                                                         |
| -------------------- | ------------------------------------------------------------------------------- |
| **k3s**              | Cài với `--disable traefik --disable metrics-server`                            |
| **helm**             | Cần thiết cho các chart Strimzi, Kong, LiveKit và PLG                           |
| **kubectl**          | Đi kèm với k3s; cũng có thể cài độc lập                                         |
| **kustomize**        | Không bắt buộc — `kubectl kustomize` / `kubectl apply -k` hoạt động tương đương |
| **Java 25 + Gradle** | Chỉ cần khi build image dịch vụ tại máy cục bộ                                  |

<!-- markdownlint-enable MD060 -->

**Cài đặt k3s:**

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --disable traefik --disable metrics-server" sh -
```

**Cài đặt Helm:**

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

---

## 2. Build Image Dịch Vụ

ZMS sử dụng Spring Boot Buildpacks (thông qua Gradle) để tạo OCI image. Chạy
từng lệnh sau từ thư mục gốc của repository.

```bash
./services/gradlew -p services/ user-management:bootBuildImage
./services/gradlew -p services/ meeting-management:bootBuildImage
./services/gradlew -p services/ chat-management:bootBuildImage
./services/gradlew -p services/ notification:bootBuildImage
```

Mặc định, image được gắn tag theo định dạng:

```text
ghcr.io/phunguy65/zms/<service>:0.0.1-SNAPSHOT
```

Tag này khớp với tham chiếu image trong các manifest Kustomize base. Nếu bạn
push image lên registry riêng hoặc đổi tag, hãy cập nhật tham chiếu image trong
patch tương ứng của overlay trước khi triển khai.

---

## 3. Cấu Hình Secret

Secret được lưu dưới dạng manifest `Secret` của Kubernetes thuần túy trong thư
mục `secrets/` của mỗi overlay. **Không bao giờ commit thông tin xác thực
thật.** Thay tất cả giá trị giả định bằng secret thực trước khi apply.

### Staging overlay — `overlays/staging/secrets/`

<!-- markdownlint-disable MD013 MD060 -->

| File                              | Tên Secret                   | Namespace        | Các Key                                                                                                                                      |
| --------------------------------- | ---------------------------- | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `user-management-secrets.yaml`    | `user-management-secrets`    | `default`        | `jwt-secret`, `cursor-secret`, `db-username`, `db-password`                                                                                  |
| `meeting-management-secrets.yaml` | `meeting-management-secrets` | `default`        | `db-username`, `db-password`, `cursor-secret`, `livekit-api-key`, `livekit-api-secret`, `recording-s3-access-key`, `recording-s3-secret-key` |
| `chat-management-secrets.yaml`    | `chat-management-secrets`    | `default`        | `livekit-api-key`, `livekit-api-secret`, `chat-jwt-secret`                                                                                   |
| `notification-secrets.yaml`       | `notification-secrets`       | `default`        | `resend-api-key`                                                                                                                             |
| `livekit-secrets.yaml`            | `livekit-secrets`            | `livekit-system` | `api-key`, `api-secret`                                                                                                                      |
| `rustfs-secrets.yaml`             | `rustfs-credentials`         | `rustfs-system`  | `access-key`, `secret-key`                                                                                                                   |

<!-- markdownlint-enable MD013 MD060 -->

### Prod overlay — `overlays/prod/secrets/`

Overlay prod sử dụng cùng tên file và tên secret như dev. Cung cấp giá trị
production thực sự cho mỗi key được liệt kê trong bảng trên.

> Cặp `livekit-api-key` / `livekit-api-secret` phải khớp nhau giữa
> `meeting-management-secrets`, `chat-management-secrets` và `livekit-secrets`
> trong cùng một overlay.

---

## 4. Cài Đặt Helm Chart

Các script tiện ích nằm trong `services/k8s/helm/`. Chạy từ thư mục gốc của
repository.

### 4.1 Strimzi Kafka Operator (namespace: `kafka`)

```bash
bash services/k8s/helm/install-strimzi.sh
```

### 4.2 Kong Gateway Operator (namespace: `kong-system`)

```bash
bash services/k8s/helm/install-kong.sh
```

### 4.3 LiveKit Server và Egress (namespace: `livekit-system`)

```bash
bash services/k8s/helm/install-livekit.sh
```

### 4.4 PLG Stack — Prometheus, Loki, Grafana (namespace: `monitoring`) — tùy chọn

```bash
bash services/k8s/helm/install-plg.sh
```

---

## 5. Triển Khai Kafka Cluster và Topic

Apply Kafka cluster KRaft trước, chờ sẵn sàng, sau đó apply định nghĩa topic.

```bash
kubectl apply -f services/k8s/kafka/kafka-cluster.yaml
kubectl wait kafka/zms-kafka --for=condition=Ready --namespace=kafka --timeout=300s
kubectl apply -f services/k8s/kafka/kafka-topics.yaml
```

Cluster chạy ở chế độ kết hợp đơn node (controller + broker). Các topic trải
rộng trên các domain `user`, `meeting` và `chat` (tổng cộng 16 topic).

---

## 6. Áp Dụng Kustomize Overlay

### Staging

```bash
kubectl apply -k services/k8s/overlays/staging
```

Thay thế sử dụng kustomize độc lập:

```bash
kustomize build services/k8s/overlays/staging | kubectl apply -f -
```

### Prod

```bash
kubectl apply -k services/k8s/overlays/prod
```

### Sự khác biệt giữa các overlay

<!-- markdownlint-disable MD060 -->

| Khía Cạnh             | Staging                     | Prod                           |
| --------------------- | --------------------------- | ------------------------------ |
| Tài nguyên            | Tổng ~4.5 Gi RAM            | 512 Mi – 2 Gi mỗi dịch vụ      |
| Số replica            | 1 mỗi dịch vụ               | 2 mỗi dịch vụ                  |
| Storage class         | `local-path` (tích hợp k3s) | Storage class mặc định của cụm |
| Chính sách pull image | `IfNotPresent`              | `Always`                       |
| Kong LoadBalancer     | NodePort / ClusterIP        | IP pool MetalLB                |

<!-- markdownlint-enable MD060 -->

### Thiết lập staging tự động

Script `dev-setup.sh` thực hiện bước 4 đến 6 (cài Helm, triển khai Kafka, và
overlay staging) theo đúng thứ tự kèm kiểm tra trạng thái sẵn sàng:

```bash
bash services/k8s/scripts/dev-setup.sh
```

---

## 7. Xác Nhận Triển Khai

Kiểm tra tất cả pod đang chạy:

```bash
kubectl get pods -A
```

Chờ mỗi deployment ứng dụng hoàn tất rollout:

```bash
kubectl rollout status deployment/user-management --timeout=300s
kubectl rollout status deployment/meeting-management --timeout=300s
kubectl rollout status deployment/chat-management --timeout=300s
kubectl rollout status deployment/notification --timeout=300s
```

Kiểm tra StatefulSet:

```bash
kubectl rollout status statefulset/chat-mongo --timeout=300s
kubectl rollout status statefulset/valkey --timeout=300s
```

---

## 8. Truy Cập Dịch Vụ

Dùng port-forward để truy cập dịch vụ nội bộ cluster từ máy cục bộ.

### Kong API Gateway (điểm vào cho tất cả API dịch vụ)

```bash
kubectl port-forward -n kong-system svc/kong-gateway-kong-proxy 8000:80
```

API sau đó sẽ truy cập được tại `http://localhost:8000/<route-prefix>`.

### LiveKit Server (tín hiệu WebRTC)

```bash
kubectl port-forward -n livekit-system svc/livekit-server 7880:7880
```

### Grafana (nếu đã cài PLG stack)

```bash
kubectl port-forward -n monitoring svc/grafana 3000:80
```

---

## 9. Tổng Quan Kiến Trúc

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Cluster                                                              │
│                                                                      │
│  ┌─────────────────── namespace: kong-system ──────────────────┐    │
│  │  Kong Gateway  ←─── lưu lượng bên ngoài                    │    │
│  │  (LoadBalancer / port-forward)                              │    │
│  └──────────┬──────────────────────────────────────────────────┘    │
│             │ định tuyến theo tiền tố đường dẫn                     │
│             ▼                                                        │
│  ┌─────────────────── namespace: default ──────────────────────┐    │
│  │                                                             │    │
│  │  user-management ──gRPC──► meeting-management              │    │
│  │       │                         │                          │    │
│  │       │     Kafka (bất đồng bộ) │                          │    │
│  │       └──publish──┬─────────────┘                          │    │
│  │                   │                                        │    │
│  │  chat-management ◄┤◄─ consume                              │    │
│  │  notification    ◄┘◄─ consume                              │    │
│  │                                                             │    │
│  │  user-postgres     meeting-postgres     chat-mongo          │    │
│  │  (Deployment)      (Deployment)         (StatefulSet)       │    │
│  │                                                             │    │
│  │  valkey (StatefulSet) ◄── meeting-management (trạng thái   │    │
│  │                               join, SSE pub/sub)            │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──── namespace: livekit-system ────┐                              │
│  │  livekit-server   livekit-egress  │                              │
│  │  livekit-redis (pub/sub nội bộ)   │                              │
│  └───────────────────────────────────┘                              │
│                                                                      │
│  ┌──── namespace: rustfs-system ─────┐                              │
│  │  rustfs (lưu trữ tương thích S3)  │                              │
│  │  Dùng cho bản ghi cuộc họp        │                              │
│  └───────────────────────────────────┘                              │
│                                                                      │
│  ┌──── namespace: kafka ─────────────┐                              │
│  │  zms-kafka (KRaft, 1 node)        │                              │
│  │  Strimzi operator                 │                              │
│  └───────────────────────────────────┘                              │
└──────────────────────────────────────────────────────────────────────┘
```

**Mô hình giao tiếp:**

- **Bên ngoài → Kong**: toàn bộ lưu lượng client đi qua Kong Gateway
- **user-management → meeting-management**: gRPC đồng bộ (tra cứu thông tin
  người dùng)
- **user-management, meeting-management → Kafka**: publish sự kiện domain
- **chat-management, notification → Kafka**: consume sự kiện domain
- **meeting-management → Valkey**: trạng thái yêu cầu tham gia và fan-out SSE
- **meeting-management, chat-management → LiveKit**: quản lý phòng và tín hiệu
  media thời gian thực
- **meeting-management → RustFS**: lưu trữ bản ghi qua S3 API

---

## 10. Xử Lý Sự Cố

### Kiểm tra trạng thái pod trên tất cả namespace

```bash
kubectl get pods -A
```

### Xem log của pod đang lỗi

```bash
kubectl logs deployment/user-management
kubectl logs deployment/meeting-management
kubectl logs deployment/chat-management
kubectl logs deployment/notification
```

### Mô tả pod để xem sự kiện và vấn đề tài nguyên

```bash
kubectl describe pod -l app=user-management
```

### Khởi động lại deployment

```bash
kubectl rollout restart deployment/user-management
```

### Kiểm tra mức tiêu thụ tài nguyên

```bash
kubectl top pods
kubectl top nodes
```

### Kiểm tra kết nối Kafka

Xác nhận Kafka cluster ở trạng thái `Ready` trước khi apply topic hoặc khởi động
dịch vụ:

```bash
kubectl get kafka -n kafka
kubectl describe kafka zms-kafka -n kafka
```

### Kong route không hoạt động

Xác minh GatewayClass và Gateway đã được chấp nhận, sau đó kiểm tra trạng thái
HTTPRoute:

```bash
kubectl get gatewayclass
kubectl get gateway -n kong-system
kubectl get httproute -A
```
