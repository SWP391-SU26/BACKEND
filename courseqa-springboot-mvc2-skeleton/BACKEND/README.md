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
3. Tạo database. `VietnameseCourseQA20DB.sql` chỉ là schema gốc và đã cũ hơn code
   hiện tại, nên phải chạy kèm toàn bộ migration — `APPLY_ALL.sql` làm cả hai
   theo đúng thứ tự và chạy lại nhiều lần vẫn an toàn:

```text
cd database
sqlcmd -S localhost,1433 -U sa -P <password> -C -i APPLY_ALL.sql
```

4. Sửa SQL Server trong:

```text
src/main/resources/application.properties
```

5. Chạy file:

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
