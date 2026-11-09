import { adminRequest, AdminApiError, type AdminSession } from './admin'
import { managed, type ManagedProject } from './adminProjects'
export type MediaItem = { id: string; kind: 'COVER' | 'SCREENSHOT'; position: number; title: string | null; description: string | null; src: string | null; alt: string | null; managed: boolean; width: number | null; height: number | null; bytes: number | null }
export async function getMedia(slug: string): Promise<MediaItem[]> {
  const value: unknown = await (await adminRequest(`projects/${encodeURIComponent(slug)}/media`)).json()
  if (!Array.isArray(value)) throw new AdminApiError('圖片列表回應不完整')
  return value.map(item => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new AdminApiError('圖片列表回應不完整')
    const row = item as Record<string, unknown>
    if (typeof row.id !== 'string' || !/^[0-9a-f-]{36}$/.test(row.id) || !['COVER', 'SCREENSHOT'].includes(String(row.kind))
        || !Number.isSafeInteger(row.position) || Number(row.position) < 0 || typeof row.managed !== 'boolean'
        || ['title', 'description', 'alt', 'src'].some(key => row[key] !== null && typeof row[key] !== 'string')
        || (row.src !== null && !/^\/(?!\/)/.test(String(row.src)))
        || (row.managed && ['width', 'height', 'bytes'].some(key => !Number.isSafeInteger(row[key]) || Number(row[key]) <= 0))) throw new AdminApiError('圖片列表回應不完整')
    return row as MediaItem
  })
}
export async function uploadMedia(slug: string, version: number, fields: { kind: string; title: string; description: string; alt: string; file: File }, session: AdminSession): Promise<ManagedProject> {
  const data = new FormData()
  data.set('expectedVersion', String(version)); data.set('kind', fields.kind); data.set('title', fields.title); data.set('description', fields.description); data.set('alt', fields.alt); data.set('file', fields.file)
  return managed(await (await adminRequest(`projects/${encodeURIComponent(slug)}/media`, { method: 'POST', headers: { [session.csrf.headerName]: session.csrf.token }, body: data })).json(), slug)
}
export async function editMedia(slug: string, version: number, item: MediaItem, session: AdminSession): Promise<ManagedProject> {
  return managed(await (await adminRequest(`projects/${encodeURIComponent(slug)}/media/${item.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify({ expectedVersion: version, title: item.title ?? '', description: item.description ?? '', alt: item.alt ?? '', position: Number(item.position) }) })).json(), slug)
}
export async function removeMedia(slug: string, version: number, id: string, session: AdminSession): Promise<ManagedProject> {
  return managed(await (await adminRequest(`projects/${encodeURIComponent(slug)}/media/${id}`, { method: 'DELETE', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify({ expectedVersion: version }) })).json(), slug)
}
export async function cleanupMedia(session: AdminSession): Promise<{ retired: number; orphaned: number; temporary: number }> {
  const value: unknown = await (await adminRequest('media/cleanup', { method: 'POST', headers: { [session.csrf.headerName]: session.csrf.token } })).json()
  if (!value || typeof value !== 'object' || ['retired', 'orphaned', 'temporary'].some(key => !Number.isSafeInteger((value as Record<string, unknown>)[key]) || Number((value as Record<string, unknown>)[key]) < 0)) throw new AdminApiError('圖片回收結果不完整')
  return value as { retired: number; orphaned: number; temporary: number }
}
export function preview(src: string) { return src.startsWith('/api/public/media/') ? src.replace('/public/', '/admin/') : src }
