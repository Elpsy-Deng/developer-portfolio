import { adminRequest, AdminApiError, type AdminSession } from './admin'
export type Kind = 'categories' | 'technologies'
export type Term = { id: string; label: string; version: number; sortOrder: number | null; references: number }
export type TermAudit = { id: string; kind: Kind; termId: string; actor: string; action: 'CREATE' | 'UPDATE' | 'DELETE'; before: Term | null; after: Term | null; createdAt: string }
function term(v: unknown): v is Term {
  if (!v || typeof v !== 'object') return false
  const r = v as Record<string, unknown>
  return typeof r.id === 'string' && /^[0-9a-f-]{36}$/.test(r.id) && typeof r.label === 'string' && !!r.label.trim()
    && Number.isSafeInteger(r.version) && Number(r.version) > 0 && Number.isSafeInteger(r.references) && Number(r.references) >= 0
    && (r.sortOrder === null || (Number.isSafeInteger(r.sortOrder) && Number(r.sortOrder) >= 0))
}
export async function listTerms(kind: Kind, signal?: AbortSignal): Promise<Term[]> {
  const v: unknown = await (await adminRequest(`taxonomy/${kind}`, { signal })).json()
  if (!Array.isArray(v) || !v.every(term)) throw new AdminApiError('分類或技術回應不完整')
  return v
}
export async function saveTerm(kind: Kind, label: string, sortOrder: number, selected: Term | null, session: AdminSession): Promise<Term> {
  const command = { label, ...(kind === 'categories' ? { sortOrder } : {}), ...(selected ? { expectedVersion: selected.version } : {}) }
  const v: unknown = await (await adminRequest(`taxonomy/${kind}${selected ? '/' + selected.id : ''}`, { method: selected ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify(command) })).json()
  if (!term(v)) throw new AdminApiError('儲存結果未確認，請重新核對，不要直接重送')
  return v
}
export async function deleteTerm(kind: Kind, selected: Term, session: AdminSession): Promise<void> {
  await adminRequest(`taxonomy/${kind}/${selected.id}`, { method: 'DELETE', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify({ expectedVersion: selected.version }) })
}
export async function termAudit(signal?: AbortSignal): Promise<TermAudit[]> {
  const v: unknown = await (await adminRequest('taxonomy/audit', { signal })).json()
  if (!Array.isArray(v) || !v.every(x => x && typeof x.id === 'string' && ['categories', 'technologies'].includes(x.kind)
    && typeof x.actor === 'string' && typeof x.termId === 'string' && typeof x.createdAt === 'string'
    && ['CREATE', 'UPDATE', 'DELETE'].includes(x.action) && (x.before === null || term(x.before)) && (x.after === null || term(x.after)))) throw new AdminApiError('分類稽核回應不完整')
  return v
}
