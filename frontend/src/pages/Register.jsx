import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { Banner, Button, Input } from '../components/ui.jsx'
import { AuthShell } from './Login.jsx'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const onSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    if (form.password.length < 12) {
      setError('Password must be at least 12 characters.')
      return
    }
    setBusy(true)
    try {
      await register(form.email.trim(), form.password, form.name.trim())
      // Backend does not auto-login on register (by design) — send to sign-in.
      navigate('/login', { replace: true, state: { registered: true } })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account">
      <form onSubmit={onSubmit} className="space-y-4">
        <Input label="Name" required value={form.name} onChange={set('name')} />
        <Input label="Email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
        <Input label="Password" type="password" autoComplete="new-password" required
               value={form.password} onChange={set('password')} />
        <p className="text-xs text-slate-400">At least 12 characters. New accounts start as VIEWER.</p>
        <Banner>{error}</Banner>
        <Button type="submit" className="w-full" disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </Button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-500">
        Already have an account? <Link to="/login" className="font-medium text-brand-600 hover:underline">Sign in</Link>
      </p>
    </AuthShell>
  )
}
