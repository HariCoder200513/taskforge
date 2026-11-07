import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { Banner, Button, Card, Empty, Select, StatusBadge, Textarea } from '../components/ui.jsx'

export default function JobsPage() {
  const qc = useQueryClient()
  const queues = useQuery({ queryKey: ['queues'], queryFn: () => api.get('/queues') })

  // Poll so submitted jobs visibly move PENDING → RUNNING → SUCCEEDED/DEAD.
  const jobs = useQuery({
    queryKey: ['jobs'],
    queryFn: () => api.get('/jobs'),
    refetchInterval: 3000,
  })

  const submit = useMutation({
    mutationFn: ({ queueId, payload }) => api.post('/jobs', { queueId, payload }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  })

  const retry = useMutation({
    mutationFn: (id) => api.post(`/jobs/${id}/retry`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  })

  const [queueId, setQueueId] = useState('')
  const [payloadText, setPayloadText] = useState('{\n  "echo": "hello"\n}')
  const [formError, setFormError] = useState(null)

  const onSubmit = (e) => {
    e.preventDefault()
    setFormError(null)
    const qId = queueId || queues.data?.[0]?.id
    if (!qId) {
      setFormError('No queue available — create one or ask for SUBMIT access.')
      return
    }
    let payload
    try {
      payload = JSON.parse(payloadText)
    } catch {
      setFormError('Payload must be valid JSON.')
      return
    }
    submit.mutate({ queueId: qId, payload })
  }

  return (
    <div className="space-y-6">
      <Card title="Submit a job">
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Queue" value={queueId} onChange={(e) => setQueueId(e.target.value)}>
              {queues.data?.length ? (
                queues.data.map((q) => <option key={q.id} value={q.id}>{q.name}</option>)
              ) : (
                <option value="">No visible queues</option>
              )}
            </Select>
          </div>
          <Textarea label="Payload (JSON)" rows={5} value={payloadText} onChange={(e) => setPayloadText(e.target.value)} />
          <p className="text-xs text-slate-400">
            Demo handler: <code>{'{"fail": true}'}</code> fails (then retries/dead-letters),
            <code>{' {"sleepMs": 500}'}</code> sleeps, anything else succeeds.
          </p>
          {formError && <Banner>{formError}</Banner>}
          {submit.isError && <Banner>{submit.error.message}</Banner>}
          {submit.isSuccess && <Banner kind="success">Job submitted.</Banner>}
          <Button type="submit" disabled={submit.isPending}>{submit.isPending ? 'Submitting…' : 'Submit job'}</Button>
        </form>
      </Card>

      <Card
        title="Jobs"
        actions={<span className="text-xs text-slate-400">{jobs.isFetching ? 'refreshing…' : 'auto-refresh 3s'}</span>}
      >
        {jobs.isLoading && <Empty>Loading…</Empty>}
        {jobs.isError && <Banner>{jobs.error.message}</Banner>}
        {jobs.data?.length === 0 && <Empty>No jobs yet.</Empty>}
        {jobs.data?.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="py-2 pr-4">Job</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Attempts</th>
                  <th className="py-2 pr-4">Last error</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {jobs.data.map((j) => (
                  <tr key={j.id}>
                    <td className="py-2 pr-4 font-mono text-xs text-slate-500">{j.id.slice(0, 8)}…</td>
                    <td className="py-2 pr-4"><StatusBadge status={j.status} /></td>
                    <td className="py-2 pr-4 text-slate-600">{j.attempts}/{j.maxAttempts}</td>
                    <td className="py-2 pr-4 max-w-xs truncate text-rose-600">{j.lastError || '—'}</td>
                    <td className="py-2 text-right">
                      {(j.status === 'FAILED' || j.status === 'DEAD') && (
                        <Button variant="ghost" onClick={() => retry.mutate(j.id)} disabled={retry.isPending}>
                          Retry
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {retry.isError && <div className="mt-3"><Banner>{retry.error.message}</Banner></div>}
      </Card>
    </div>
  )
}
