import { Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/LandingPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import AdminLayout from './layouts/AdminLayout.jsx'
import MainLayout from './layouts/MainLayout.jsx'
import {
  AdminDashboardPage,
  AdminDocumentsPage,
  AdminExperimentsPage,
  AdminIndexingPage,
  AdminLogsPage,
  AdminModelSettingsPage,
  AdminResearchDashboardPage,
  AdminSubjectsPage,
  AdminTestSetPage,
  AdminUsersPage,
} from './pages/admin/AdminPages.jsx'
import DocumentDetailPage from './pages/DocumentDetailPage.jsx'
import LibraryPage from './pages/LibraryPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import WorkspacePage from './pages/WorkspacePage.jsx'
import { isAdminSession, isAuthenticated } from './services/authService.js'

function RequireAuth({ children }) {
  return isAuthenticated() ? children : <Navigate replace to="/login" />
}

function RequireAdmin({ children }) {
  if (!isAuthenticated()) {
    return <Navigate replace to="/login" />
  }

  return isAdminSession() ? children : <Navigate replace to="/workspace" />
}

function PublicOnly({ children }) {
  if (!isAuthenticated()) {
    return children
  }

  return <Navigate replace to={isAdminSession() ? '/admin' : '/workspace'} />
}

function App() {
  return (
    <Routes>
      <Route element={<LandingPage />} path="/" />
      <Route element={<PublicOnly><LoginPage /></PublicOnly>} path="/login" />
      <Route element={<PublicOnly><RegisterPage /></PublicOnly>} path="/register" />
      <Route element={<RequireAuth><SettingsPage /></RequireAuth>} path="/settings" />
      <Route element={<RequireAuth><SettingsPage /></RequireAuth>} path="/profile" />
      <Route element={<RequireAdmin><AdminLayout /></RequireAdmin>} path="/admin">
        <Route index element={<Navigate replace to="/admin/dashboard" />} />
        <Route path="dashboard" element={<AdminDashboardPage />} />
        <Route path="users" element={<AdminUsersPage />} />
        <Route path="documents" element={<AdminDocumentsPage />} />
        <Route path="subjects" element={<AdminSubjectsPage />} />
        <Route path="indexing" element={<AdminIndexingPage />} />
        <Route path="model-settings" element={<AdminModelSettingsPage />} />
        <Route path="test-set" element={<AdminTestSetPage />} />
        <Route path="experiments" element={<AdminExperimentsPage />} />
        <Route path="research-dashboard" element={<AdminResearchDashboardPage />} />
        <Route path="logs" element={<AdminLogsPage />} />
      </Route>
      <Route element={<RequireAuth><MainLayout /></RequireAuth>}>
        <Route element={<Navigate replace to="/workspace" />} path="/app" />
        <Route element={<WorkspacePage />} path="/workspace" />
        <Route element={<Navigate replace to="/workspace" />} path="/chat" />
        <Route element={<LibraryPage />} path="/library" />
        <Route element={<DocumentDetailPage />} path="/library/documents/:id" />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
