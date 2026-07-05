# Forge App với Remote Backend tự host

Tài liệu tham khảo cho mô hình **Forge app + backend tự host (Forge Remote)**, tập
trung vào cách backend Spring Boot xác thực request từ Forge và gọi Jira Cloud REST
API một cách type-safe.

## 1. Bối cảnh & lựa chọn nền tảng

### 1.1 Connect vs Forge (2026)

Atlassian đang trong giai đoạn End-of-Support cho **Atlassian Connect**
(End-of-Support dự kiến Q4 2026). Với project mới nên chọn **Forge**.

| Tiêu chí        | Atlassian Connect            | Atlassian Forge                    |
| --------------- | ---------------------------- | ---------------------------------- |
| Hosting         | Tự host (external)           | Serverless do Atlassian quản lý    |
| Ngôn ngữ        | Bất kỳ (Java, Node, ...)     | JavaScript/TypeScript              |
| Credential      | Lưu `sharedSecret` lâu dài   | Token per-invocation, TTL ngắn     |
| Bảo mật         | Developer chịu trách nhiệm   | Atlassian-managed, zero-trust      |
| Trạng thái      | End-of-Support Q4 2026       | Nền tảng tương lai                 |

### 1.2 Forge Remote

Khi cần backend viết bằng **Java/Spring Boot** nhưng vẫn dùng nền tảng Forge, dùng
**Forge Remote**: Forge app (manifest + UI/resolver) uỷ quyền logic backend cho một
service tự host trên hạ tầng riêng.

Trường hợp sử dụng:

- Backend viết bằng ngôn ngữ ngoài JavaScript (Java, ...)
- Chạy background job dài, gọi nhiều external API
- Yêu cầu data residency (lưu dữ liệu theo region cụ thể)

## 2. Kiến trúc & luồng hoạt động

```
Forge app (UI/resolver)  --invokeRemote-->  Backend tự host (Spring Boot)  --Bearer token-->  Jira Cloud API
```

Ba thành phần:

- **Frontend (Forge app)**: UI Kit / Custom UI gọi remote qua `invokeRemote()`.
- **Manifest declaration**: khai báo `remotes` + `endpoint`.
- **Self-hosted backend**: nhận request từ Forge kèm token, verify và gọi Jira.

Khác biệt cốt lõi so với Connect: backend **không giữ credential lâu dài**. Mỗi lần
Forge gọi backend, request đính kèm token mới (TTL ~4h, tự động rotate) → an toàn
hơn, không sợ leak shared secret.

## 3. Header Forge gửi tới backend

Request từ Forge tới backend là HTTPS, timeout 25s, kèm các header:

| Header                 | Nội dung                                              | Điều kiện                                    |
| ---------------------- | ----------------------------------------------------- | -------------------------------------------- |
| `Authorization`        | **Forge Invocation Token (FIT)** — JWT ký bởi Atlassian | Luôn có                                     |
| `x-forge-oauth-system` | **App token** (TTL ~4h) — gọi Jira như app/bot        | Khi `appSystemToken.enabled: true`           |
| `x-forge-oauth-user`   | **User token** (TTL ~4h) — gọi Jira với quyền user    | Khi `appUserToken.enabled: true` + có session |
| `x-b3-traceid`, `x-b3-spanid` | Distributed tracing headers                    | Luôn có                                      |

Lưu ý: `x-forge-oauth-user` **không có** trong scheduled trigger (không có user
session) — khi đó dùng app token hoặc offline user impersonation.

## 4. Cấu hình manifest.yml

```yaml
app:
  id: ari:cloud:ecosystem::app/YOUR-APP-ID
  name: My Jira Integration

permissions:
  scopes:
    - read:jira-work        # đọc issues, projects
    - write:jira-work       # tạo/sửa issues, comments
    - read:app-system-token # bắt buộc để dùng app system token

remotes:
  - key: jira-backend
    baseUrl: https://your-server.com
    operations:
      - compute             # bắt buộc cho invokeRemote
    storage:
      inScopeEUD: false     # không lưu End-User Data trên remote

modules:
  endpoint:
    - key: jira-sync-ep
      remote: jira-backend
      route:
        path: /sync-issues
      auth:
        appSystemToken:
          enabled: true     # -> gửi x-forge-oauth-system
        appUserToken:
          enabled: false
```

Scopes Jira thường dùng:

- `read:jira-work` — đọc issues, projects
- `write:jira-work` — tạo/sửa issues, comments
- `manage:jira-project` — quản lý project settings
- `read:app-system-token` — cho phép dùng app system token

Forge sẽ từ chối outbound request đến domain không nằm trong `remotes.baseUrl` /
`permissions.external.fetch.backend`.

## 5. Xác thực request (verify FIT)

FIT là JWT ký bởi Atlassian, chứa context invocation. Backend **bắt buộc** verify.

### 5.1 Claims chính của FIT

```json
{
  "app": {
    "id": "ari:cloud:ecosystem::app/...",
    "installationId": "ari:cloud:ecosystem::installation/...",
    "apiBaseUrl": "https://api.atlassian.com/ex/jira/4c822e2f-510f-48b9-b8d0-8419d0932949",
    "appVersion": "1.0.0"
  },
  "context": {
    "cloudId": "4c822e2f-510f-48b9-b8d0-8419d0932949"
  },
  "principal": "655362:312d3308-8954-42b0-aa38-771a10c88656",
  "aud": "ari:cloud:ecosystem::app/YOUR_APP_ID",
  "iss": "forge/invocation-token",
  "iat": 1700175149,
  "exp": 1700175174
}
```

### 5.2 Yêu cầu validation

1. Verify chữ ký JWT bằng public key từ JWKS:
   `https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json`
2. Verify `aud` = app id trong manifest
3. Verify `iss` = `forge/invocation-token`
4. Check `exp` chưa hết hạn
5. Cho phép **clock skew ~5-10s** (lỗi verify phổ biến nhất là lệch giờ server)

### 5.3 FITValidator (Spring Boot, dùng jose4j)

Pattern chính thức từ app mẫu `bitbucket.org/atlassian/forge-remote-spring-boot`:

```java
@Component
public class FITValidator {

    @Value("${jwks.endpoint:https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json}")
    private String jwksUrl;

    @Value("${appId}")
    private String appId;

    public void validate(String invocationToken) throws InvalidJwtException {
        var httpsJwks = new HttpsJwks(jwksUrl);
        var keyResolver = new HttpsJwksVerificationKeyResolver(httpsJwks);
        var jwtConsumer = new JwtConsumerBuilder()
                .setVerificationKeyResolver(keyResolver)
                .setExpectedAudience(appId)
                .setExpectedIssuer("forge/invocation-token")
                .setAllowedClockSkewInSeconds(10)
                .build();

        jwtConsumer.process(invocationToken);
    }
}
```

## 6. Gọi Jira REST API từ backend

Backend dùng token opaque từ header (`x-forge-oauth-system` hoặc
`x-forge-oauth-user`) làm Bearer, gọi qua API gateway:

```
https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/...
```

`cloudId` và base URL lấy trực tiếp từ FIT (`context.cloudId`, `app.apiBaseUrl`).

```java
@PostMapping("/sync-issues")
public ResponseEntity<?> sync(
        @RequestHeader("Authorization") String fitBearer,
        @RequestHeader("x-forge-oauth-system") String appToken) {

    var fit = fitValidator.validate(fitBearer.substring("Bearer ".length()));

    var headers = new HttpHeaders();
    headers.setBearerAuth(appToken);
    headers.setContentType(MediaType.APPLICATION_JSON);

    var url = fit.getApp().getApiBaseUrl() + "/rest/api/3/issue";
    var entity = new HttpEntity<>(issueBody, headers);
    return restTemplate.postForEntity(url, entity, String.class);
}
```

Nguyên tắc token:

- Token là **opaque** — chỉ dùng làm Bearer, không parse.
- `x-forge-oauth-system` TTL ~4h, có thể cache đến gần `exp` (Forge rotate trước hạn).
- Cần gọi với quyền user khác (offline): dùng GraphQL mutation
  `offlineUserAuthToken` tại `https://api.atlassian.com/graphql` với app system token.

## 7. Type safety khi gọi Jira API

Không có **SDK Java chính thức** cho Jira Cloud v3:

- **JRJC (jira-rest-java-client)**: deprecated, chỉ cho Jira Server/DC, không hỗ trợ
  Cloud v3.
- **`@forge/api` (requestJira)**: chỉ có trong Forge serverless (JS), không dùng được
  ở backend tự host, và cũng không type-safe response.

### 7.1 Giải pháp khuyến nghị: generate client từ OpenAPI spec

Atlassian publish OpenAPI spec cho Jira Cloud:

| Product                | OpenAPI Spec URL                                                        |
| ---------------------- | ----------------------------------------------------------------------- |
| Jira Cloud Platform v3 | `https://developer.atlassian.com/cloud/jira/platform/swagger-v3.v3.json` |
| Jira Service Management | `https://developer.atlassian.com/cloud/jira/service-desk/swagger.v3.json` |

Dùng OpenAPI Generator với `library = resttemplate` (sync) hoặc `webclient`
(reactive). Cách này khớp với hạ tầng codegen sẵn có của monorepo.

```gradle
plugins { id 'org.openapi.generator' version '7.5.0' }

task generateJiraClient(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
  inputSpec = "$projectDir/src/main/resources/jira-v3-openapi.json"
  outputDir = "$buildDir/generated/jira-client"
  generatorName = "java"
  library = "resttemplate"
  apiPackage = "com.smiski.jira.api"
  modelPackage = "com.smiski.jira.model"
  invokerPackage = "com.smiski.jira.client"
  configOptions = [
    useJakartaEe: "true",
    dateLibrary: "java8",
    hideGenerationTimestamp: "true"
  ]
}
```

Cấu hình base URL + Bearer token tại runtime (mỗi tenant có `cloudId` riêng):

```java
ApiClient apiClient = new ApiClient();
apiClient.setBasePath("https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3");
apiClient.setBearerToken(appToken);

Issue issue = new IssueApi(apiClient).createIssue(issueBody, false, null);
```

### 7.2 Lựa chọn thay thế

- **everit-org/atlassian-restclient-jiracloud**: client generated sẵn, đang maintain
  (2025). Nhược điểm: dùng RxJava + Jetty Client, lệch khỏi Spring ecosystem.
- **DIY hand-code**: không khuyến nghị, mất type safety và tốn công maintain.

### 7.3 Tích hợp vào monorepo

1. Thêm OpenAPI Generator vào convention plugin trong `build-logic/` (giống web SDK).
2. Tạo module `services/jira-client/` chứa generated code; service nào cần Jira thì
   depend vào.
3. **Commit spec file** vào repo thay vì fetch live URL khi build (tránh build flaky).
4. Nếu spec quá nặng (~100k LOC), cân nhắc chỉ generate DTO rồi tự gọi bằng
   `RestClient` — đánh đổi giữa full typed client và build time.

## 8. Gotchas & best practices

| Vấn đề                         | Nguyên nhân                              | Cách xử lý                                        |
| ------------------------------ | ---------------------------------------- | ------------------------------------------------- |
| FIT verify fail                | Lệch giờ server                          | Set clock skew 5-10s                              |
| Miss key rotation              | Cache JWKS quá lâu                       | Refresh JWKS ~1h hoặc khi verify fail             |
| Thiếu token trong trigger      | Scheduled trigger không có user session  | Dùng app token / offline user impersonation       |
| 403 từ Jira                    | Thiếu scope trong manifest               | Khai báo đúng scope match operation               |
| Build chậm / code lớn          | Spec Jira v3 rất lớn (~100k LOC)         | Generate ra artifact riêng, cache                 |
| Generated code không compile   | Spec dùng `oneOf`/`anyOf` phức tạp       | Patch spec hoặc generated code, QA lần build đầu  |
| Fail audit data residency      | `inScopeEUD: true` nhưng không theo region | Dùng region-specific URL trong manifest         |

## 9. Nguồn tham khảo

**Forge Remote**

- Forge Remote overview — https://developer.atlassian.com/platform/forge/remote/
- Forge Remote essentials — https://developer.atlassian.com/platform/forge/remote/essentials/
- Calling product APIs from a remote — https://developer.atlassian.com/platform/forge/remote/calling-product-apis/
- Endpoint manifest reference — https://developer.atlassian.com/platform/forge/manifest-reference/endpoint/
- Remotes manifest reference — https://developer.atlassian.com/platform/forge/manifest-reference/remotes/
- Jira product scopes — https://developer.atlassian.com/platform/forge/manifest-reference/scopes-product-jira/
- Forge Remote Spring Boot reference app — https://bitbucket.org/atlassian/forge-remote-spring-boot
- FIT clock skew discussion — https://community.developer.atlassian.com/t/im-having-problems-validating-the-fit-in-forge-remote/80099

**Jira REST API & type-safe client**

- Jira Cloud Platform REST API v3 — https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/
- Jira Cloud OpenAPI spec — https://developer.atlassian.com/cloud/jira/platform/swagger-v3.v3.json
- Update to Jira Cloud's Swagger/OpenAPI docs — https://www.atlassian.com/blog/development/update-to-jira-clouds-swagger-openapi-docs
- Generating a REST client for Jira Cloud — https://community.atlassian.com/forums/Jira-articles/Generating-a-REST-client-for-Jira-Cloud/ba-p/1307133
- everit-org/atlassian-restclient-jiracloud — https://github.com/everit-org/atlassian-restclient-jiracloud
- OpenAPI Generator Java docs — https://openapi-generator.tech/docs/generators/java/

**Connect (bối cảnh migration)**

- Connect end-of-support timeline — https://www.atlassian.com/blog/development/connect-end-of-support-what-it-means-for-custom-apps-and-how-to-migrate-to-forge
- Adopting Forge from Connect — https://developer.atlassian.com/platform/adopting-forge-from-connect/how-to-adopt/
