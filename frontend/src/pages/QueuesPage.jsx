import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { Banner, Button, Card, Empty, Input, Select } from '../components/ui.jsx'

export default function QueuesPage() {
  const { hasRole } = useAuth()
  const qc = useQueryClient()
  const isAdmin = hasRole('ADMIN')

  const queues = useQuery({ queryKey: ['queues'], queryFn: () => api.get('/queues') })

  const createQueue = useMutation({
    mutationFn: (name) => api.post('/queues', { name }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['queues'] }),
  })

  const [name, setName] = useState('')

  return (
    <div className="space-y-6">
      {isAdmin && (
        <Card title="Create a queue">
          <form
            className="flex items-end gap-3"
            onSubmit={(e) => {
              e.preventDefault()
              if (!name.trim()) return
              createQueue.mutate(name.trim(), { onSuccess: () => setName('') })
            }}
          >
            <Input label="Queue name" className="max-w-xs" value={name} onChange={(e) => setName(e.target.value)} />
            <Button type="submit" disabled={createQueue.isPending}>
              {createQueue.isPending ? 'Creating…' : 'Create'}
            </Button>
          </form>
          {createQueue.isError && <div className="mt-3"><Banner>{createQueue.error.message}</Banner></div>}
        </Card>
      )}

      {isAdmin && queues.data?.length > 0 && <GrantPermission queues={queues.data} />}

      <Card title="Queues">
        {queues.isLoading && <Empty>Loading…</Empty>}
        {queues.isError && <Banner>{queues.error.message}</Banner>}
        {queues.data?.length === 0 && <Empty>No queues you can see yet.</Empty>}
        {queues.data?.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {queues.data.map((q) => (
              <li key={q.id} className="flex items-center justify-between py-3">
                <div>
                  <div className="font-medium text-slate-800">{q.name}</div>
                  <div className="font-mono text-xs text-slate-400">{q.id}</div>
                </div>
                <div className="text-xs text-slate-400">{new Date(q.createdAt).toLocaleString()}</div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function GrantPermission({ queues }) {
  const [form, setForm] = useState({ queueId: queues[0]?.id ?? '', userId: '', permission: 'SUBMIT' })
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const grant = useMutation({
    mutationFn: ({ queueId, userId, permission }) =>
      api.post(`/queues/${queueId}/permissions`, { userId, permission }),
  })

  return (
    <Card title="Grant queue permission">
      <form
        className="grid gap-3 sm:grid-cols-4 sm:items-end"
        onSubmit={(e) => {
          e.preventDefault()
          if (!form.userId.trim()) return
          grant.mutate({ ...form, userId: form.userId.trim() })
        }}
      >
        <Select label="Queue" value={form.queueId} onChange={set('queueId')}>
          {queues.map((q) => <option key={q.id} value={q.id}>{q.name}</option>)}
        </Select>
        <Input label="User ID" value={form.userId} onChange={set('userId')} placeholder="uuid" />
        <Select label="Permission" value={form.permission} onChange={set('permission')}>
          {['VIEW', 'SUBMIT', 'MANAGE', 'RETRY'].map((p) => <option key={p}>{p}</option>)}
        </Select>
        <Button type="submit" disabled={grant.isPending}>{grant.isPending ? 'Granting…' : 'Grant'}</Button>
      </form>
      <div className="mt-3 space-y-2">
        {grant.isError && <Banner>{grant.error.message}</Banner>}
        {grant.isSuccess && <Banner kind="success">Permission granted.</Banner>}
      </div>
    </Card>
  )
}
