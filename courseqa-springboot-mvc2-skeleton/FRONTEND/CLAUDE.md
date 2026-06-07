# CLAUDE.md

## Project Context

Project name: **FStu**.

This is the frontend for an FPT HCM SWP project. The product is a course-document QA chatbot that lets students upload course materials and ask questions grounded in those documents.

Frontend stack:

- React
- Vite
- Tailwind CSS v4
- React Router
- JavaScript/JSX
- Manrope font from Google Fonts

Main working structure:

```text
src/
  assets/
  components/
  config/
  hooks/
  layouts/
  pages/
  services/
  styles/
  utils/
```

## Current Implementation

Implemented routes:

```text
/          Landing page
/login     Mock login page
/register  Mock register page
/app       Authenticated workspace placeholder
*          Redirect to /
```

Implemented pages/components:

```text
src/pages/
  LandingPage.jsx
  LoginPage.jsx
  RegisterPage.jsx
  WorkspacePage.jsx

src/components/landing/
  LandingHeader.jsx
  LandingHero.jsx
  FeatureOverview.jsx
  WorkflowTimeline.jsx
  ResearchSection.jsx
  LandingFooter.jsx

src/components/common/
  Button.jsx
```

Current auth behavior is mock-only:

- Login form submit navigates to `/app`.
- Register form submit navigates to `/app`.
- No backend auth integration yet.

The logo is stored in `public/`:

```text
public/Gemini_Generated_Image_gyb1mfgyb1mfgyb1.png
```

## Language Rules

- All visible website UI text must be English only.
- The chatbot must still understand Vietnamese if the user types Vietnamese in the chatbox.
- Vietnamese should only appear as user-generated chat content, not as labels, buttons, headings, placeholders, errors, or landing copy.

## Product Scope

### Public Landing Page

For guests, graders, professors, and students who want to understand the system.

Landing page includes:

- Floating pill navbar with FStu logo.
- Hero section with video background target `public/hero-bg.mp4`.
- CTA: `Let's get started`, linking to `/login`.
- Feature section.
- Workflow section.
- Research/RBL section.
- Footer with product links and mock contact info.

Current landing design direction:

- Keep the existing academic teal/green brand theme.
- Do not introduce unrelated multi-color palettes.
- Sections alternate between light background and primary green/teal background.
- Navbar changes theme based on the active section using `IntersectionObserver`.
- Desktop/laptop is the priority for now; responsive mobile can come later.

### Document Management

Planned features:

- Upload PDF, DOCX, and lecture slides.
- Backend chunks and embeds documents.
- Manage documents by subject/chapter.
- Demo only needs one subject.
- Show indexed documents.
- Show document states: uploaded, indexing, indexed, failed.

### Chat & QA

Planned features:

- Natural chat with session context.
- Segmented module toggle: `RAG` / `Fine-tuning`.
- Default mode should be `RAG`.
- User can upload documents; uploaded/indexed documents are the main input for RAG.
- Assistant answers should cite original document sources.
- Answers should stay within uploaded/indexed document scope in RAG mode.
- Conversation history by session.
- If a question is outside the available documents, UI should show an out-of-scope response.

## API Assumptions

Base API URL:

```js
import { env } from '../config/env'
```

Default:

```text
http://localhost:8080/api
```

Suggested endpoints:

```text
GET    /subjects
GET    /documents
POST   /documents/upload
GET    /documents/:id
DELETE /documents/:id

GET    /chat/sessions
POST   /chat/sessions
GET    /chat/sessions/:id/messages
POST   /chat/sessions/:id/messages
```

Chat request should include the selected mode:

```js
{
  message: 'What is RAG?',
  mode: 'rag' // 'rag' | 'fine_tuning'
}
```

For file upload, use `FormData`; do not manually set `Content-Type: application/json`.

## Data Shape Suggestions

Document:

```js
{
  id: 'doc_1',
  fileName: 'SWP_Chapter_1.pdf',
  subject: 'SWP391',
  chapter: 'Chapter 1',
  type: 'pdf',
  status: 'indexed',
  uploadedAt: '2026-05-31T09:00:00Z',
  chunkCount: 24
}
```

Chat message:

```js
{
  id: 'msg_1',
  role: 'user', // 'user' | 'assistant'
  content: 'What is RAG?',
  mode: 'rag',
  createdAt: '2026-05-31T09:10:00Z',
  citations: [
    {
      documentId: 'doc_1',
      fileName: 'SWP_Chapter_1.pdf',
      page: 3,
      chunkText: '...'
    }
  ]
}
```

## Scripts

```bash
npm install
npm run dev
npm run build
npm run lint
```

Before ending a meaningful code change, run:

```bash
npm run build
npm run lint
```

## Implementation Priorities

Current next priorities:

1. Build the authenticated app shell at `/app`.
2. Add document upload/list UI with mock data.
3. Add chat workspace with `RAG` / `Fine-tuning` toggle.
4. Add citation panel and session history.
5. Move mock data behind service functions.
6. Integrate real backend APIs when available.
7. Add mobile responsiveness later.

## Important Behavior Rules

- Chatbox must include a segmented `RAG` / `Fine-tuning` toggle.
- When sending chat messages, include the selected mode.
- In `RAG` mode, citations are expected for in-document answers.
- In `Fine-tuning` mode, citations may be absent; UI must handle that gracefully.
- Upload file validation should allow PDF, DOCX, PPT, and PPTX.
- UI must show clearly when documents are not indexed yet.
- Session history should be visible in the app workspace.

## Code Quality Notes

- Keep API logic in `src/services`, not inside large components.
- Split large UI into focused components.
- Do not add large UI libraries unless necessary.
- Do not remove user changes unless explicitly asked.
- Keep implementation easy to explain in an SWP demo.
