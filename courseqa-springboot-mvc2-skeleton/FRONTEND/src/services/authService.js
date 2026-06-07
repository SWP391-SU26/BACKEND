import { request } from './httpClient.js'

export async function login({ email, password }) {
  const auth = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })

  return toSession(auth)
}

export async function register(payload) {
  const auth = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      fullName: payload.fullName,
      email: payload.email,
      password: payload.password,
      roleName: payload.roleName ?? 'USER',
    }),
  })

  return toSession(auth)
}

export function logout(userId) {
  return request(`/auth/logout/${userId}`, {
    method: 'POST',
  })
}

export function getUsers() {
  return request('/auth/users')
}

export function getUserRoles(userId) {
  return request(`/auth/users/${userId}/roles`)
}

export function updateUserRole(userId, roleName) {
  return request(`/auth/users/${userId}/role`, {
    method: 'PUT',
    body: JSON.stringify({ roleName }),
  })
}

export function saveSession(session) {
  localStorage.setItem('fstu_access_token', session.accessToken)
  localStorage.setItem('fstu_user', JSON.stringify(session.user))
}

export function clearSession() {
  localStorage.removeItem('fstu_access_token')
  localStorage.removeItem('fstu_user')
}

export function getSavedUser() {
  try {
    return JSON.parse(localStorage.getItem('fstu_user') ?? 'null')
  } catch {
    clearSession()
    return null
  }
}

export function isAuthenticated() {
  return Boolean(localStorage.getItem('fstu_access_token') && getSavedUser()?.id)
}

export function isAdminSession() {
  return isAuthenticated() && getSavedUser()?.role === 'admin'
}

function toSession(auth) {
  const roles = auth.user.roles ?? []
  const isAdmin = roles.some((role) => role?.toUpperCase() === 'ADMIN')

  return {
    accessToken: auth.token,
    user: {
      id: auth.user.userId,
      email: auth.user.email,
      name: auth.user.fullName,
      role: isAdmin ? 'admin' : 'user',
      roles,
    },
  }
}
