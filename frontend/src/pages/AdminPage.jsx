import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { Banner, Button, Card, Input, Select } from '../components/ui.jsx'

// Role management over the admin-only endpoint. There is no list-users API yet,
// so the admin enters a user id (from registration, or the queue-permission flow).
export default function AdminPage() {
  const [form, setForm] = useState({ userId: '', role: 'DEVELOPER', action: 'GRANT' })
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const changeRole = useMutation({
    mutationFn: ({ userId, role, action }) => api.post(`/users/${userId}/roles`, { role, action }),
  })

  return (
    <div className="space-y-6">
      <Card title="Change a user's roles">
        <form
          className="grid gap-3 sm:grid-cols-4 sm:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            if (!form.userId.trim()) return
            changeRole.mutate({ ...form, userId: form.userId.trim() })
          }}
        >
          <Input label="User ID" className="sm:col-span-2" value={form.userId} onChange={set('userId')} placeholder="uuid" />
          <Select label="Role" value={form.role} onChange={set('role')}>
            {['ADMIN', 'DEVELOPER', 'VIEWER'].map((r) => <option key={r}>{r}</option>)}
          </Select>
          <Select label="Action" value={form.action} onChange={set('action')}>
            {['GRANT', 'REVOKE'].map((a) => <option key={a}>{a}</option>)}
          </Select>
          <Button type="submit" className="sm:col-span-4 sm:w-auto" disabled={changeRole.isPending}>
            {changeRole.isPending ? 'Applying…' : 'Apply'}
          </Button>
        </form>

        <div className="mt-4 space-y-2">
          {changeRole.isError && <Banner>{changeRole.error.message}</Banner>}
          {changeRole.isSuccess && (
            <Banner kind="success">
              {changeRole.data.email} now has: {changeRole.data.roles.join(', ')}
            </Banner>
          )}
        </div>
      </Card>

      <Card title="Notes">
        <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>The very first admin is created by the server-side bootstrap (<code>AUTH_BOOTSTRAP_ADMIN_EMAIL</code>), not here.</li>
          <li>You cannot revoke the last remaining ADMIN — the API returns a 409.</li>
          <li>Role changes take effect on the user's next login (their current access token keeps its old roles until it expires).</li>
        </ul>
      </Card>
    </div>
  )
}
