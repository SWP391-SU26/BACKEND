# Design System: FStu

## 1. Design Read

FStu is a course-document QA product for an FPT HCM SWP project. It helps students upload course materials, ask document-grounded questions, compare RAG with fine-tuning, and verify answers with citations.

Design direction:

- **Page kind:** public landing page + authenticated study workspace.
- **Audience:** students, professors, project graders.
- **Vibe:** academic, focused, clean, trustworthy.
- **Style:** modern SaaS with a soft academic teal identity.
- **Font:** Manrope for display and body.
- **Primary platform:** desktop/laptop first. Mobile responsiveness comes later.

## 2. Language Rule

- Website UI text must be English only.
- The chatbot must still understand Vietnamese chat input.
- Vietnamese appears only if typed by the user inside the chatbox.

## 3. Current Visual System

FStu uses HSL CSS variables in [index.css](D:/SWP/FRONTEND/src/styles/index.css):

```css
--primary: 176 77% 26%;
--primary-foreground: 0 0% 100%;
--background: 0 0% 100%;
--foreground: 240 10% 10%;
--muted-foreground: 240 5% 55%;
--border: 210 24% 88%;
--nav-bg: 180 23% 97%;
--secondary: 210 31% 96%;
--accent: 210 31% 96%;
```

Brand rule:

- Keep the academic teal/green theme.
- Do not introduce unrelated section palettes like purple, amber, cyan, or indigo as dominant themes.
- Sections may alternate between light surfaces and soft green/teal surfaces.
- CTA color remains `primary`.

## 4. Landing Page Design

Current landing page route: `/`.

Implemented sections:

- `LandingHeader`
- `LandingHero`
- `FeatureOverview`
- `WorkflowTimeline`
- `ResearchSection`
- `LandingFooter`

Landing behavior:

- Navbar is a floating pill.
- CTA text is `Let's get started`.
- CTA links to `/login`.
- Hero uses video background source `/hero-bg.mp4`; place the file in `public/hero-bg.mp4`.
- Hero uses a muted video with overlay blur.
- Dashboard preview currently uses a placeholder image.
- Footer includes mock contact information.

Theme alternation:

- Hero: light background with teal accent.
- Features/Documents: soft green/teal background.
- Workflow: light background.
- Research: soft green/emerald background.
- Footer: light background.

Navbar theme:

- Navbar theme changes based on the current section.
- Implementation uses `IntersectionObserver` in `LandingPage.jsx`.
- Do not use continuous `window.addEventListener('scroll')` for this.

## 5. Auth Pages

Routes:

```text
/login
/register
```

Current behavior:

- Login and register are mock pages.
- Form submission navigates to `/app`.
- No backend auth yet.

Login page design:

- Two-column premium auth layout.
- Left brand panel with teal background.
- Right form panel.
- Uses FStu logo from `public`.
- UI text is English only.

## 6. Workspace Direction

Route:

```text
/app
```

Current state:

- Placeholder workspace page only.

Next app shell should include:

- Top bar with FStu logo and user/status area.
- Left sidebar for sessions and documents.
- Center chat workspace.
- Right citation/source panel.

Desktop layout target:

```text
┌──────────────────────────────────────────────────────────────┐
│ Top Bar: FStu | Subject SWP391 | User/Status                 │
├───────────────┬──────────────────────────────┬───────────────┤
│ Left Sidebar  │ Main Chat Workspace           │ Right Context │
│ Sessions      │ Messages + Composer           │ Sources       │
│ Documents     │                              │ Document Info │
└───────────────┴──────────────────────────────┴───────────────┘
```

## 7. Chat Workspace Requirements

Must include:

- Session history.
- Chat messages.
- Composer.
- `RAG` / `Fine-tuning` segmented toggle.
- Citation panel.
- Out-of-scope response state.

Module toggle:

- Default: `RAG`.
- Changing mode must not clear current messages.
- New messages should be sent with selected mode.
- In `RAG`, prioritize source citations.
- In `Fine-tuning`, handle missing citations gracefully.

## 8. Document Management Requirements

Must include:

- Upload zone.
- Accepted file types: PDF, DOCX, PPT, PPTX.
- Document list/table.
- Status badges: uploaded, indexing, indexed, failed.
- Chapter/subject metadata.
- Clear note that documents are usable in RAG only after indexing.

## 9. Component Rules

Buttons:

- Use `src/components/common/Button.jsx`.
- `cta` variant uses primary background and white text.
- Rounded-full CTAs are preferred on landing/auth.

Cards and panels:

- Use soft rounded shapes.
- Prefer border + subtle shadow.
- Avoid nested cards inside cards unless the outer layer acts as a bezel/shell.

Inputs:

- Label above input.
- Focus ring in primary color.
- Error/help text below.

Badges:

- Use text labels, not color alone.
- Status badges can use teal/green/amber/red semantic colors.

## 10. Motion & Interaction

- Use lightweight transitions.
- Animate `transform` and `opacity`.
- Navbar theme changes should be smooth.
- Avoid heavy continuous scroll handlers.
- Respect reduced-motion later when animation scope grows.

## 11. Accessibility

- Buttons and links must have readable contrast.
- Icon-only buttons need `aria-label`.
- Forms need visible labels.
- Status must be expressed with text, not color alone.
- Text should not overflow containers.

## 12. Implementation Plan

Completed:

- Landing page.
- Manrope font setup.
- HSL CSS variables.
- Floating navbar.
- Section theme alternation.
- Navbar theme switching by section.
- Login/register mock pages.
- `/app` placeholder route.

Next:

1. Build `/app` authenticated app shell.
2. Add mock document management.
3. Add chat workspace.
4. Add `RAG` / `Fine-tuning` toggle.
5. Add citation panel.
6. Add session history.
7. Move mock data into services.
8. Integrate backend APIs.
9. Add responsive/mobile polish later.

## 13. Anti-Patterns

- Do not change brand theme away from teal/green.
- Do not add unrelated dominant palettes per section.
- Do not use Vietnamese for visible UI labels.
- Do not turn the landing page into generic marketing unrelated to course documents.
- Do not remove citation emphasis.
- Do not hide the RAG vs fine-tuning research angle.
- Do not hardcode API logic into large components.

## 14. Acceptance Checklist

- Landing page uses FStu branding.
- UI text is English.
- CTA links to `/login`.
- Login/register mock flow leads to `/app`.
- Landing sections alternate between light and green/teal surfaces.
- Navbar changes theme with active section.
- Build passes.
- Lint passes.
