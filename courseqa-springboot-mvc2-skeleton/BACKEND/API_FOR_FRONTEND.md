# CourseQA API for Frontend

Base URL local:

```text
http://localhost:8080/api
```

Frontend env:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

All JSON APIs return this wrapper:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

For upload/download/preview APIs, see the document section because file APIs do not always return JSON.

## Auth

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

Request:

```json
{
  "fullName": "System Admin",
  "email": "admin@example.com",
  "password": "123456",
  "roleName": "ADMIN"
}
```

Allowed `roleName`:

```text
ADMIN, TEACHER, STUDENT, RESEARCHER, USER
```

Note:

```text
USER is accepted from frontend, then backend stores it as STUDENT.
Only ADMIN should be allowed to enter admin pages on frontend.
```

Response `data`:

```json
{
  "userId": "uuid",
  "fullName": "System Admin",
  "email": "admin@example.com",
  "roles": ["ADMIN"]
}
```

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Request:

```json
{
  "email": "admin@example.com",
  "password": "123456"
}
```

Response `data`:

```json
{
  "userId": "uuid",
  "fullName": "System Admin",
  "email": "admin@example.com",
  "roles": ["ADMIN"]
}
```

### Logout

```http
POST /api/auth/logout/{userId}
```

### Users

```http
GET /api/auth/users
GET /api/auth/users/{userId}/roles
PUT /api/auth/users/{userId}/role
```

Change role request:

```json
{
  "roleName": "ADMIN"
}
```

## Courses, Chapters, Workspaces

This is the main flow for frontend:

```text
Admin creates course
-> User/Admin creates or selects chapter
-> User/Admin selects workspace
-> Upload document with courseId + chapterId + workspaceId
```

### Get Courses

```http
GET /api/courses
```

Response `data[]`:

```json
{
  "courseId": "uuid",
  "courseCode": "SWT301",
  "courseName": "Software Testing",
  "description": "Testing course",
  "createdBy": "uuid",
  "isActive": true,
  "createdAt": "2026-06-09T16:12:44"
}
```

### Create Course

Admin screen should call this API.

```http
POST /api/courses
Content-Type: application/json
```

Request:

```json
{
  "courseCode": "ECO101",
  "courseName": "Kinh te vi mo",
  "description": "Mon hoc kinh te co ban",
  "createdBy": "admin-user-id"
}
```

Response `data`: same as course object.

### Get Chapters By Course

```http
GET /api/courses/{courseId}/chapters
```

Response `data[]`:

```json
{
  "chapterId": "uuid",
  "courseId": "uuid",
  "chapterTitle": "Chapter 1 - Introduction",
  "description": "Opening materials",
  "orderIndex": 1,
  "isActive": true,
  "createdAt": "2026-06-09T16:12:44"
}
```

### Create Chapter

Use this when user/admin wants to add a new document group for the same course.

```http
POST /api/courses/{courseId}/chapters
Content-Type: application/json
```

Request:

```json
{
  "chapterTitle": "Chapter 2 - Requirement Analysis",
  "description": "Documents that continue after Chapter 1",
  "orderIndex": 2
}
```

`orderIndex` is optional. If frontend does not send it, backend auto-assigns the next order.

### Get All Workspaces

```http
GET /api/courses/workspaces
```

### Get Workspaces By Course

```http
GET /api/courses/{courseId}/workspaces
```

Response `data[]`:

```json
{
  "workspaceId": "uuid",
  "courseId": "uuid",
  "workspaceTitle": "SWT301 Workspace",
  "description": "Shared course workspace",
  "visibility": "COURSE",
  "isActive": true,
  "createdAt": "2026-06-09T16:12:44"
}
```

### Create Workspace

```http
POST /api/courses/{courseId}/workspaces
Content-Type: application/json
```

Request:

```json
{
  "ownerUserId": "user-id",
  "workspaceTitle": "ECO101 Workspace",
  "description": "Tai lieu mon Kinh te",
  "visibility": "COURSE"
}
```

## Documents

Supported upload types:

```text
PDF, DOCX, PPTX, TXT
```

### Upload Document

```http
POST /api/documents/upload
Content-Type: multipart/form-data
```

Form fields:

```text
file: File
workspaceId: uuid, required
courseId: uuid, required
chapterId: uuid, optional
uploadedBy: uuid, optional
```

Important backend validation:

```text
workspaceId must belong to courseId.
chapterId must belong to courseId when chapterId is provided.
```

Response `data`:

```json
{
  "documentId": "uuid",
  "workspaceId": "uuid",
  "courseId": "uuid",
  "chapterId": "uuid",
  "documentTitle": "business_flows_20_functions",
  "originalFilename": "business_flows_20_functions.docx",
  "fileType": "DOCX",
  "processingStatus": "PROCESSED",
  "totalPages": 1,
  "errorMessage": null,
  "storageProvider": "CLOUDINARY",
  "cloudinarySecureUrl": "https://res.cloudinary.com/.../document.docx",
  "cloudinaryPreviewUrl": "https://res.cloudinary.com/.../preview.pdf"
}
```

Frontend upload example:

```js
const formData = new FormData()
formData.append('file', file)
formData.append('workspaceId', workspaceId)
formData.append('courseId', courseId)
if (chapterId) formData.append('chapterId', chapterId)
if (userId) formData.append('uploadedBy', userId)

const response = await fetch(`${apiBaseUrl}/documents/upload`, {
  method: 'POST',
  body: formData,
})
```

### Get Documents By Workspace

```http
GET /api/documents/workspace/{workspaceId}
```

### Get Document Detail

```http
GET /api/documents/{documentId}
```

### Get Document Pages

```http
GET /api/documents/{documentId}/pages
```

Response `data[]`:

```json
{
  "pageId": "uuid",
  "documentId": "uuid",
  "pageNumber": 1,
  "cleanedText": "Extracted page text",
  "wordCount": 120,
  "charCount": 650
}
```

### Get Document Chunks

```http
GET /api/documents/{documentId}/chunks
```

Response `data[]`:

```json
{
  "chunkId": "uuid",
  "documentId": "uuid",
  "chunkIndex": 0,
  "chunkStrategy": "fixed_1200_150",
  "content": "Chunk content",
  "pageStart": 1,
  "pageEnd": 1,
  "tokenCount": 180
}
```

### Preview Document

Use this URL directly in an iframe:

```http
GET /api/documents/{documentId}/preview
```

For Cloudinary documents, backend returns `302` redirect to a PDF preview URL.

Recommended frontend logic:

```js
const previewUrl = document.cloudinaryPreviewUrl
  ?? `${apiBaseUrl}/documents/${document.documentId}/preview`
```

PDF files can also use:

```http
GET /api/documents/{documentId}/file
```

For DOCX/PPTX, the backend converts to PDF preview when uploading. The browser does not need Word, WPS, or LibreOffice.

### Delete Document

```http
DELETE /api/documents/{documentId}
```

Backend deletes database rows and Cloudinary assets when available.

## RAG

### Embedding Models

```http
GET  /api/rag/embedding-models
POST /api/rag/embedding-models
```

Create model request:

```json
{
  "modelName": "keyword-hash-128",
  "provider": "DEMO",
  "dimension": 128,
  "isLocal": true,
  "description": "Demo embedding model"
}
```

### Prepare Chunk Embeddings

```http
POST /api/rag/embeddings/prepare
Content-Type: application/json
```

Request:

```json
{
  "workspaceId": "uuid",
  "embeddingModelId": "uuid"
}
```

### Retrieve

```http
POST /api/rag/retrieve
Content-Type: application/json
```

Request:

```json
{
  "workspaceId": "uuid",
  "queryText": "noi dung can hoi",
  "topK": 5
}
```

### Retrieval Queries, Results, Citations

```http
GET /api/rag/retrieval-queries
GET /api/rag/retrieval-results
GET /api/rag/citations
```

Optional query params depend on screen usage:

```text
workspaceId
chatSessionId
documentId
```

## Common Error Cases

### Invalid Course/Workspace/Chapter

When uploading document:

```text
Workspace does not belong to the selected course.
Chapter does not belong to the selected course.
```

Frontend should show the user:

```text
Ban dang chon sai mon/chapter. Hay chon lai khoa hoc va chuong phu hop.
```

### Preview Not Available

For old DOCX/PPTX files uploaded before LibreOffice was installed:

```text
Document preview was not generated.
```

Frontend should ask user/admin to delete and re-upload the file.

### CORS / Failed To Fetch

Check:

```text
VITE_API_BASE_URL=http://localhost:8080/api
Backend running on port 8080
Frontend origin allowed in CorsConfig
```
