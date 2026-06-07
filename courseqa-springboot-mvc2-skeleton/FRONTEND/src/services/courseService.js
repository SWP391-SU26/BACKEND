import { request } from './httpClient.js'

export async function getCourses() {
  const courses = await request('/courses')
  return courses.map(toUiCourse)
}

export async function createCourse(payload) {
  const course = await request('/courses', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return toUiCourse(course)
}

export async function getChapters(courseId) {
  const chapters = await request(`/courses/${courseId}/chapters`)
  return chapters.map(toUiChapter)
}

export async function createChapter(courseId, payload) {
  const chapter = await request(`/courses/${courseId}/chapters`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return toUiChapter(chapter)
}

export async function getWorkspaces() {
  const workspaces = await request('/courses/workspaces')
  return workspaces.map(toUiWorkspace)
}

export async function getWorkspacesByCourse(courseId) {
  const workspaces = await request(`/courses/${courseId}/workspaces`)
  return workspaces.map(toUiWorkspace)
}

export async function createWorkspace(courseId, payload) {
  const workspace = await request(`/courses/${courseId}/workspaces`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return toUiWorkspace(workspace)
}

function toUiCourse(course) {
  return {
    id: course.courseId,
    code: course.courseCode,
    name: course.courseName,
    description: course.description,
    createdBy: course.createdBy,
    isActive: course.isActive,
    createdAt: course.createdAt,
  }
}

function toUiChapter(chapter) {
  return {
    id: chapter.chapterId,
    courseId: chapter.courseId,
    title: chapter.chapterTitle,
    description: chapter.description,
    orderIndex: chapter.orderIndex,
    isActive: chapter.isActive,
    createdAt: chapter.createdAt,
  }
}

function toUiWorkspace(workspace) {
  return {
    id: workspace.workspaceId,
    courseId: workspace.courseId,
    ownerUserId: workspace.ownerUserId,
    name: workspace.workspaceTitle,
    term: workspace.visibility ?? 'COURSE',
    description: workspace.description,
    isActive: workspace.isActive,
    createdAt: workspace.createdAt,
    color: 'teal',
  }
}
