# CourseQA - Backend & Frontend Configuration

## Cấu trúc dự án

```
BACKEND/courseqa-springboot-mvc2-skeleton/
├── BACKEND/                  # Spring Boot REST API
│   ├── src/
│   ├── pom.xml
│   ├── .gitignore
│   └── application.properties
└── FRONTEND/                 # React + Vite
    ├── src/
    ├── .env                  # Environment variables (local)
    ├── .env.example          # Template for .env
    ├── package.json
    └── vite.config.js
```

## Cấu hình Backend

### 1. Database Setup

Tạo database từ file SQL:
```bash
cd BACKEND
# Chạy script SQL trong SQL Server
# File: database/VietnameseCourseQA20DB.sql
```

### 2. Cấu hình CORS

File `BACKEND/src/main/java/com/courseqa/config/CorsConfig.java` đã được tạo để cho phép Frontend kết nối.

Các origin được phép:
- `http://localhost:5173` (Vite dev server)
- `http://localhost:3000` (Alternative port)

### 3. Chạy Backend

```bash
cd BACKEND/courseqa-springboot-mvc2-skeleton/BACKEND
mvn clean install
mvn spring-boot:run
```

Backend chạy trên: `http://localhost:8080`

API endpoints: `http://localhost:8080/api/*`

---

## Cấu hình Frontend

### 1. Setup Environment Variables

Tạo file `.env` trong thư mục FRONTEND (nếu chưa có):

```bash
cd FRONTEND
cp .env.example .env
```

Nội dung `.env`:
```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_ENV=development
```

### 2. Cài đặt Dependencies

```bash
cd FRONTEND/courseqa-springboot-mvc2-skeleton/FRONTEND
npm install
```

### 3. Chạy Frontend

```bash
npm run dev
```

Frontend chạy trên: `http://localhost:5173`

---

## Cách sử dụng API trong Frontend

Tất cả services trong Frontend đã được cấu hình để sử dụng `env.apiBaseUrl` từ file `.env`.

**Ví dụ:**
```javascript
// src/services/authService.js
import { request } from './httpClient.js'

export async function login({ email, password }) {
  const auth = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  return toSession(auth)
}

// API URL sẽ được kết hợp từ:
// VITE_API_BASE_URL + '/auth/login'
// = http://localhost:8080/api/auth/login
```

---

## Troubleshooting

### CORS Error
Nếu nhận lỗi CORS, hãy kiểm tra:
1. Backend đang chạy trên `http://localhost:8080`
2. File `CorsConfig.java` đã được load (kiểm tra logs)
3. Frontend đang chạy trên `http://localhost:5173` hoặc port trong config

### .env không được load
1. Kiểm tra file `.env` tồn tại trong folder FRONTEND
2. Restart dev server: `npm run dev`
3. Biến phải bắt đầu với `VITE_` để Vite nhận dạng

### API request failed
1. Kiểm tra Backend đang chạy: `http://localhost:8080/api/health`
2. Kiểm tra VITE_API_BASE_URL trong `.env`
3. Mở DevTools → Network tab để xem actual request URL

---

## Deploy

### Production (.env)
Cập nhật `.env` với production API URL:
```
VITE_API_BASE_URL=https://api.yourdomain.com/api
VITE_ENV=production
```

### Backend CORS update
Cập nhật `CorsConfig.java` với production domain:
```java
.allowedOrigins(
    "https://yourdomain.com",
    "https://app.yourdomain.com"
)
```

---

## Git Configuration

Frontend `.gitignore` đã được cấu hình để:
- Bỏ qua `.env` (local config)
- Giữ `.env.example` (template)

Backend `.gitignore` cũng được tạo tương tự.

Khi pull/clone project, tạo `.env` từ `.env.example` và cập nhật giá trị nếu cần.
