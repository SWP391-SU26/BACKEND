# Hướng dẫn sử dụng Swagger UI để test API

## 1. Yêu cầu trước khi dùng

- Backend đang **chạy** (xem mục 2 bên dưới)
- Trình duyệt Chrome / Edge / Firefox

---

## 2. Khởi động Backend

### Lần đầu hoặc sau khi thay đổi code:
```powershell
cd "C:\FALL 2026\SWP\courseqa-springboot-mvc2-skeleton\BACKEND"
.\mvnw.cmd package -DskipTests -q
java -jar target\courseqa-springboot-mvc2-skeleton-1.0.0.jar
```

### Chỉ restart (không thay đổi code):
```powershell
cd "C:\FALL 2026\SWP\courseqa-springboot-mvc2-skeleton\BACKEND"
java -jar target\courseqa-springboot-mvc2-skeleton-1.0.0.jar
```

> ✅ Backend khởi động thành công khi thấy dòng:
> `Started CourseQaApplication in X seconds`

---

## 3. Truy cập Swagger UI

| Ai | Link |
|----|------|
| Người chạy BE trên máy (localhost) | http://localhost:8080/swagger-ui.html |
| Thành viên khác (qua ngrok) | Xem mục 5 bên dưới |

---

## 4. Hướng dẫn sử dụng Swagger UI

### Giao diện chính

Khi mở Swagger UI sẽ thấy danh sách các nhóm API:
- **auth-controller** — Đăng ký, đăng nhập, đăng xuất
- **course-controller** — Quản lý khóa học, chương
- **document-controller** — Upload và quản lý tài liệu
- **chat-controller** — Chatbot AI
- **rag-controller** — RAG pipeline, embeddings
- *(và các nhóm khác)*

---

### Cách test một API (ví dụ: Đăng ký tài khoản)

**Bước 1:** Tìm nhóm **auth-controller** → click để mở rộng

**Bước 2:** Click vào **POST /api/auth/register**

**Bước 3:** Click nút **Try it out** (góc phải)

**Bước 4:** Sửa nội dung trong ô **Request body**:
```json
{
  "fullName": "Nguyen Van A",
  "email": "vana@gmail.com",
  "password": "Password@123",
  "roleName": "STUDENT"
}
```

> `roleName` hợp lệ: `ADMIN`, `TEACHER`, `STUDENT`, `RESEARCHER`, `USER`

**Bước 5:** Click nút **Execute**

**Bước 6:** Xem kết quả trong phần **Server response**:
- `200` — Thành công
- `400` — Dữ liệu không hợp lệ
- `409` — Email đã tồn tại
- `500` — Lỗi server (xem log terminal)

---

### Cách test API cần đăng nhập (Authorization)

**Bước 1:** Đăng nhập qua **POST /api/auth/login**:
```json
{
  "email": "vana@gmail.com",
  "password": "Password@123"
}
```

**Bước 2:** Copy giá trị `token` trong response

**Bước 3:** Click nút **Authorize** 🔒 ở góc trên phải trang Swagger

**Bước 4:** Nhập vào ô:
```
Bearer <token_vừa_copy>
```

**Bước 5:** Click **Authorize** → **Close**

**Bước 6:** Bây giờ tất cả API sẽ tự động gửi kèm token

---

### Các API quan trọng

#### Auth
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/auth/register` | Đăng ký tài khoản |
| POST | `/api/auth/login` | Đăng nhập |
| POST | `/api/auth/logout/{userId}` | Đăng xuất |
| GET | `/api/auth/users` | Lấy danh sách user |

#### Course
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/courses` | Lấy danh sách khóa học |
| POST | `/api/courses` | Tạo khóa học mới |
| PUT | `/api/courses/{id}` | Cập nhật khóa học |
| DELETE | `/api/courses/{id}` | Xóa khóa học |

#### Document
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/documents/upload` | Upload tài liệu (PDF/Word) |
| GET | `/api/documents/workspace/{id}` | Lấy tài liệu theo workspace |

---

## 5. Cho thành viên khác dùng qua ngrok

Người đang chạy BE cần mở thêm terminal và chạy:
```powershell
ngrok http 8080
```

Sẽ xuất hiện URL dạng:
```
Forwarding  https://xxxx-xxxx.ngrok-free.app -> http://localhost:8080
```

Gửi link cho team:
```
https://xxxx-xxxx.ngrok-free.app/swagger-ui.html
```

> ⚠️ **Lưu ý:**
> - URL ngrok thay đổi mỗi lần restart ngrok (Free plan)
> - Lần đầu mở link ngrok → bấm **Visit Site** để bỏ qua trang cảnh báo
> - Phải giữ **cả 2 terminal** (BE và ngrok) luôn mở

---

## 6. Xem log khi có lỗi 500

Khi API trả về lỗi 500, xem terminal đang chạy BE để biết nguyên nhân chi tiết.

---

## 7. Cấu hình kết nối DB (application.properties)

File: `src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=RAGChatbot20TablesDB;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=Sa@123456
```

> Thay đổi `username` và `password` theo SQL Server của bạn nếu khác.
