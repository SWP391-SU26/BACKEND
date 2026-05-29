# CourseQA Spring Boot MVC2 Skeleton

Đây là sườn backend cho dự án **Vietnamese CourseQA: RAG vs Fine-tuning**.

Framework chính:

```text
Spring Boot
```

Kiến trúc:

```text
MVC2
```

## Cấu trúc MVC2

```text
src/main/java/com/courseqa
├── controller
├── service
├── repository
├── model
│   ├── entity
│   └── dto
├── config
└── exception
```

## Ý nghĩa từng folder

| Folder | Mục đích |
|---|---|
| `controller` | Nhận request từ frontend và trả response |
| `service` | Xử lý logic nghiệp vụ |
| `repository` | Làm việc với database |
| `model/entity` | Class ánh xạ với table database |
| `model/dto` | Class nhận/trả dữ liệu API |
| `config` | Cấu hình project |
| `exception` | Xử lý lỗi |
| `database` | Chứa script SQL Server |
| `data/fine_tuning` | Chứa file JSONL để fine-tuning |
| `uploads` | Chứa file PDF/DOCX/PPTX/TXT upload |

## Chạy project trên VS Code

1. Mở folder này bằng VS Code.
2. Cài extension Java + Spring Boot.
3. Sửa SQL Server trong:

```text
src/main/resources/application.properties
```

4. Chạy file:

```text
src/main/java/com/courseqa/CourseQaApplication.java
```

## Nhóm API cần tạo

```text
/api/auth
/api/courses
/api/documents
/api/chat
/api/rag
/api/evaluation
/api/fine-tuning
```

## Ghi chú

Đây là sườn để tự add code. Các class đã được tạo rỗng theo đúng mô hình MVC2.
