import { adminRequest, AdminApiError, type AdminSession } from './admin'
import { validateProject } from './projects'
import type { Project } from '../types/project'
export type ProjectContent = Omit<Project, 'coverImage' | 'screenshots' | 'localDemoUrl'>
export type ManagedProject = { project: Project; version: number; published: boolean; sortOrder: number }
export type ProjectChange = { content: ProjectContent; expectedVersion: number | null; published: boolean; sortOrder: number | null }
export type ManagedRow = { slug: string; title: string; category: string; status: string; published: boolean; featured: boolean; version: number; sortOrder: number }
export type Audit = { id: string; actor: string; action: 'CREATE' | 'UPDATE' | 'DELETE'; before: ManagedProject | null; after: ManagedProject | null; createdAt: string }
const invalid = () => new AdminApiError('作品資料回應不完整，請重新載入')
const object = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw invalid(); return v as Record<string, unknown> }
export function managed(v: unknown, slug: string): ManagedProject {
  const row = object(v)
  if (!Number.isSafeInteger(row.version) || Number(row.version) < 1 || typeof row.published !== 'boolean' || !Number.isSafeInteger(row.sortOrder) || Number(row.sortOrder) < 0) throw invalid()
  return { project: validateProject(row.project, slug), version: Number(row.version), published: row.published, sortOrder: Number(row.sortOrder) }
}
export type ManagedListing = { items: ManagedRow[]; total: number; page: number; pageSize: number; totalPages: number }
export async function listManaged(filters: { q: string; category: string; published: string; page: number; pageSize: number }, signal?: AbortSignal): Promise<ManagedListing> {
  const params = new URLSearchParams(Object.entries(filters).map(([k,v]) => [k, String(v)]))
  const value = object(await (await adminRequest('projects?' + params, { signal })).json())
  if (!Array.isArray(value.items) || !['total','page','pageSize','totalPages'].every(k => Number.isSafeInteger(value[k]))
    || Number(value.page) < 1 || Number(value.pageSize) < 1 || Number(value.pageSize) > 50 || Number(value.total) < 0
    || value.totalPages !== Math.ceil(Number(value.total) / Number(value.pageSize))
    || value.items.length !== Math.min(Number(value.pageSize), Math.max(0, Number(value.total) - (Number(value.page) - 1) * Number(value.pageSize)))) throw invalid()
  const items = value.items.map(v => {
    const row = object(v)
    if (['slug', 'title', 'category', 'status'].some(k => typeof row[k] !== 'string') || typeof row.published !== 'boolean' || typeof row.featured !== 'boolean'
        || !Number.isSafeInteger(row.version) || Number(row.version) < 1 || !Number.isSafeInteger(row.sortOrder) || Number(row.sortOrder) < 0) throw invalid()
    return row as unknown as ManagedRow
  })
  return { items, total: Number(value.total), page: Number(value.page), pageSize: Number(value.pageSize), totalPages: Number(value.totalPages) }
}
export async function getManaged(slug: string, signal?: AbortSignal): Promise<ManagedProject> {
  return managed(await (await adminRequest('projects/' + encodeURIComponent(slug), { signal })).json(), slug)
}
export async function getOptions(signal?: AbortSignal): Promise<{ categories: string[]; technologies: string[] }> {
  const value = object(await (await adminRequest('catalog-options', { signal })).json())
  for (const key of ['categories', 'technologies']) if (!Array.isArray(value[key]) || !(value[key] as unknown[]).every(v => typeof v === 'string')) throw invalid()
  return value as { categories: string[]; technologies: string[] }
}
export async function saveManaged(change: ProjectChange, session: AdminSession, creating: boolean): Promise<ManagedProject> {
  const response = await adminRequest('projects' + (creating ? '' : '/' + encodeURIComponent(change.content.slug)), {
    method: creating ? 'POST' : 'PUT', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify(change),
  })
  return managed(await response.json(), change.content.slug)
}
export async function deleteManaged(slug: string, version: number, session: AdminSession): Promise<void> {
  await adminRequest('projects/' + encodeURIComponent(slug), { method: 'DELETE', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify({ expectedVersion: version }) })
}
export async function getAudit(slug: string, signal?: AbortSignal): Promise<Audit[]> {
  const value: unknown = await (await adminRequest(`projects/${encodeURIComponent(slug)}/audit`, { signal })).json()
  if (!Array.isArray(value)) throw invalid()
  return value.map(v => {
    const row = object(v)
    if (typeof row.id !== 'string' || typeof row.actor !== 'string' || typeof row.createdAt !== 'string' || !['CREATE', 'UPDATE', 'DELETE'].includes(String(row.action))) throw invalid()
    return { id: row.id, actor: row.actor, createdAt: row.createdAt, action: row.action as Audit['action'], before: row.before == null ? null : managed(row.before, slug), after: row.after == null ? null : managed(row.after, slug) }
  })
}
