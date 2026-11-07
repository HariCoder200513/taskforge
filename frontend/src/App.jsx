import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext.jsx'
import Layout from './components/Layout.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import QueuesPage from './pages/QueuesPage.jsx'
import JobsPage from './pages/JobsPage.jsx'
import AdminPage from './pages/AdminPage.jsx'

function Protected({ children }) {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <FullScreen>Loading…</FullScreen>
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function FullScreen({ children }) {
  return <div className="flex min-h-screen items-center justify-center text-slate-500">{children}</div>
}

export default function App() {
  const { loading } = useAuth()
  if (loading) return <FullScreen>Loading…</FullScreen>

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <Protected>
            <Layout />
          </Protected>
        }
      >
        <Route index element={<Navigate to="/jobs" replace />} />
        <Route path="/queues" element={<QueuesPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
