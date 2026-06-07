# Landing Page Implementation Plan

## 1. Scope Hien Tai

Trang landing page dung cho:

- Nguoi chua dang nhap.
- Giang vien / nguoi cham do an.
- Sinh vien muon hieu nhanh he thong.

Giai doan dau:

- Uu tien desktop/laptop layout.
- Chua can responsive mobile.
- Chua can authentication that.
- Nut `Login`, `Register`, `Bat dau` co the dieu huong placeholder hoac gan route sau.

## 2. Muc Tieu Trang

Landing page can truyen tai 3 y chinh:

1. Day la chatbot hoi dap dua tren tai lieu mon hoc.
2. Sinh vien co the upload tai lieu, hoi dap, xem citation va lich su hoi thoai.
3. Do an co phan nghien cuu: RAG vs Fine-tuning, benchmark embedding model, RAGAS evaluation.

## 3. Page Structure

Thu tu section nen lam:

```text
Header
Hero
Feature Overview
Workflow
Research / RBL
Footer
```

## 4. Section Plan

### 4.1 Header

Thanh phan:

- Logo / ten he thong: `FStu`.
- Navigation ngan:
  - `Features`
  - `Workflow`
  - `Research`
- Actions:
  - `Login`
  - `Register`

Design:

- Height khoang `72px`.
- Nen trang, border duoi nhe.
- Logo ben trai, nav giua hoac ben phai, action ben phai.
- `Register` la primary button.

### 4.2 Hero Section

Thanh phan:

- H1: `Chatbot hoi dap tai lieu mon hoc`
- Mo ta ngan: upload tai lieu, hoi dap theo noi dung mon hoc, cau tra loi co citation.
- Primary CTA: `Bat dau`
- Secondary CTA: `Dang nhap`
- Visual ben phai: mock UI chatbox gom:
  - Cau hoi cua sinh vien.
  - Cau tra loi cua bot.
  - Citation card.
  - Badge `RAG mode`.

Design:

- Split layout 2 cot.
- Trai la noi dung, phai la mock product.
- Khong dung hero qua marketing hay gradient neon.

### 4.3 Feature Overview

Feature can co:

- Upload tai lieu.
- Chat hoi dap theo tai lieu.
- Trich dan nguon.
- Lich su hoi thoai.
- Benchmark RAG/Fine-tuning.

Design:

- Dung grid bat doi xung 2 cot hoac 3 cot co kich thuoc khac nhau.
- Khong dung 5 card bang nhau nam ngang.
- Moi feature co title ngan va mo ta 1-2 dong.

### 4.4 Workflow Section

Quy trinh:

```text
Upload tai lieu -> Chunk & embedding -> Truy van -> Sinh cau tra loi co citation
```

Thanh phan:

- 4 step cards hoac timeline ngang.
- Moi step co title, mo ta ngan.
- Co the them mini label:
  - `Input`
  - `Indexing`
  - `Retrieval`
  - `Answer`

Design:

- Timeline ro rang de giang vien nhin la hieu RAG pipeline.
- Nen co line noi giua cac step.

### 4.5 Research / RBL Section

Noi dung can co:

- So sanh RAG va Fine-tuning.
- Benchmark embedding model.
- RAGAS evaluation.

Goi y layout:

- Ben trai: heading va mo ta phan nghien cuu.
- Ben phai: comparison table hoac 3 research blocks.

Bang so sanh RAG vs Fine-tuning:

```text
RAG
- Tot cho tai lieu moi upload.
- Co citation ro rang.
- Cap nhat tri thuc bang cach index tai lieu.

Fine-tuning
- Tot cho hoc phong cach tra loi hoac pattern.
- Can dataset training.
- Khong tu dong co citation neu khong ket hop retrieval.
```

RAGAS metrics nen hien:

- Faithfulness
- Answer relevancy
- Context precision
- Context recall

### 4.6 Footer

Thanh phan:

- Ten project.
- `FPT HCM - SWP Project`
- Link ngan: `Login`, `Register`, `Research`

Design:

- Don gian, khong qua day.
- Border top nhe.

## 5. Component Plan

Tao cac component:

```text
src/components/landing/
  LandingHeader.jsx
  LandingHero.jsx
  ProductMockup.jsx
  FeatureOverview.jsx
  WorkflowTimeline.jsx
  ResearchSection.jsx
  LandingFooter.jsx
```

Page:

```text
src/pages/LandingPage.jsx
```

App wiring:

```text
src/App.jsx
```

Giai doan dau co the render truc tiep `LandingPage` trong `App.jsx`.

## 6. Copy Draft

Hero title:

```text
Chatbot hoi dap tai lieu mon hoc
```

Hero description:

```text
Upload tai lieu bai giang, dat cau hoi theo ngu canh mon hoc va nhan cau tra loi co trich dan nguon.
```

Feature intro:

```text
Mot workspace hoc tap ket hop quan ly tai lieu, chat theo ngu canh va truy vet nguon tra loi.
```

Research intro:

```text
He thong khong chi la chatbot demo, ma con dung de so sanh RAG va Fine-tuning qua benchmark embedding va RAGAS evaluation.
```

## 7. Implementation Order

1. Tao `LandingPage.jsx`.
2. Tao folder `src/components/landing`.
3. Build `LandingHeader`.
4. Build `LandingHero` + `ProductMockup`.
5. Build `FeatureOverview`.
6. Build `WorkflowTimeline`.
7. Build `ResearchSection`.
8. Build `LandingFooter`.
9. Gan `LandingPage` vao `App.jsx`.
10. Chay `npm run build`.
11. Chay `npm run lint`.

## 8. Acceptance Checklist

- Header co logo, Login, Register.
- Hero co ten he thong, mo ta ngan, nut `Bat dau`.
- Co visual mock chatbox/citation.
- Co section tinh nang gom 5 y theo requirement.
- Co section workflow 4 buoc.
- Co section research gom RAG vs Fine-tuning, embedding benchmark, RAGAS.
- Footer co thong tin project.
- Desktop layout khong bi vo tren laptop.
- Chua can responsive mobile trong phase hien tai.
