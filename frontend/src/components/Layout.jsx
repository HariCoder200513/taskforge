import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { Button } from './ui.jsx'

export default function Layout() {
  const { user, roles, hasRole, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const linkClass = ({ isActive }) =>
    `rounded-md px-3 py-1.5 text-sm font-medium transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
    }`

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-6">
            <span className="text-lg font-bold tracking-tight text-brand-700">TaskForge</span>
            <nav className="flex items-center gap-1">
              <NavLink to="/jobs" className={linkClass}>Jobs</NavLink>
              <NavLink to="/queues" className={linkClass}>Queues</NavLink>
              {hasRole('ADMIN') && <NavLink to="/admin" className={linkClass}>Admin</NavLink>}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <div className="text-right">
              <div className="text-sm font-medium text-slate-800">{user?.email}</div>
              <div className="text-xs text-slate-400">{roles.join(', ') || 'no roles'}</div>
            </div>
            <Button variant="ghost" onClick={onLogout}>Log out</Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
