import type { Project, ProjectCategory, ProjectSummary } from '../types/project'

export class ProjectApiError extends Error {
  constructor(message: string, readonly notFound = false) { super(message) }
}

export interface Listing { items: ProjectSummary[]; total: number; catalogTotal: number; page: number; pageSize: number; totalPages: number }
export interface Filters { q?: string; category?: ProjectCategory | '全部'; featured?: boolean; page?: number; pageSize?: number }

const invalid = () => new ProjectApiError('作品資料格式不完整，請重新載入。')
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid()
  return value as Record<string, unknown>
}
function strings(value: unknown) {
  return Array.isArray(value) && value.every(item => typeof item === 'string')
}
function fields(value: Record<string, unknown>, keys: string[]) {
  if (keys.some(key => typeof value[key] !== 'string')) throw invalid()
}
function https(value: unknown) {
  try { return typeof value === 'string' && new URL(value).protocol === 'https:' } catch { return false }
}
function image(value: unknown) {
  const item = object(value)
  if (typeof item.src !== 'string' || !/^\/(?!\/)/.test(item.src) || typeof item.alt !== 'string') throw invalid()
}
function summary(value: unknown): ProjectSummary {
  const item = object(value)
  fields(item, ['slug', 'title', 'subtitle', 'category', 'status', 'summary', 'year', 'theme', 'technologyLabel'])
  if (!strings(item.technologies) || typeof item.featured !== 'boolean'
    || !['Planned', 'In Progress', 'Completed'].includes(String(item.status))
    || !['forest', 'blue', 'peach', 'violet'].includes(String(item.theme))) throw invalid()
  if (item.coverImage != null) image(item.coverImage)
  return item as unknown as ProjectSummary
}

async function read(path: string, signal: AbortSignal): Promise<unknown> {
  const response = await fetch('/api/public/' + path, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]), credentials: 'omit',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    if (response.status === 404 && body?.code === 'PROJECT_NOT_FOUND') {
      throw new ProjectApiError('找不到這個專案', true)
    }
    throw new ProjectApiError(response.status === 400 ? '搜尋或篩選條件不正確。' : '作品暫時無法載入，請稍後重試。')
  }
  try { return await response.json() } catch { throw invalid() }
}

export async function getProjects(filters: Filters, signal: AbortSignal): Promise<Listing> {
  const params = new URLSearchParams()
  if (filters.q) params.set('q', filters.q)
  if (filters.category && filters.category !== '全部') params.set('category', filters.category)
  if (filters.page != null) params.set('page', String(filters.page))
  if (filters.pageSize != null) params.set('pageSize', String(filters.pageSize))
  if (filters.featured != null) params.set('featured', String(filters.featured))
  const value = object(await read('projects?' + params, signal))
  if (!Array.isArray(value.items) || !Number.isSafeInteger(value.total) || !Number.isSafeInteger(value.catalogTotal)
    || !['page','pageSize','totalPages'].every(k => Number.isSafeInteger(value[k]))
    || Number(value.page) < 1 || Number(value.pageSize) < 1 || Number(value.pageSize) > 50 || Number(value.total) < 0
    || value.totalPages !== Math.ceil(Number(value.total) / Number(value.pageSize))
    || value.items.length !== Math.min(Number(value.pageSize), Math.max(0, Number(value.total) - (Number(value.page) - 1) * Number(value.pageSize)))
    || Number(value.catalogTotal) < Number(value.total)) throw invalid()
  return { items: value.items.map(summary), total: Number(value.total), catalogTotal: Number(value.catalogTotal), page: Number(value.page), pageSize: Number(value.pageSize), totalPages: Number(value.totalPages) }
}

export async function getCategories(signal: AbortSignal): Promise<ProjectCategory[]> {
  const value = await read('categories', signal)
  if (!strings(value) || (value as string[]).some(item => !item.trim() || item.length > 40 || item === '全部')) throw invalid()
  return value as ProjectCategory[]
}

export function validateProject(payload: unknown, slug: string): Project {
  const value = object(payload)
  summary(value)
  fields(value, ['statusNote', 'description', 'role', 'duration'])
  if (value.slug !== slug || !strings(value.features) || !strings(value.architecture)) throw invalid()
  for (const key of ['verification', 'limitations', 'plannedGoals']) {
    if (value[key] != null && !strings(value[key])) throw invalid()
  }
  for (const key of ['githubUrl', 'liveDemoUrl']) {
    if (value[key] != null && !https(value[key])) throw invalid()
  }
  if (!Array.isArray(value.challenges) || !Array.isArray(value.screenshots)) throw invalid()
  for (const challenge of value.challenges) fields(object(challenge), ['title', 'description'])
  for (const screenshot of value.screenshots) {
    const item = object(screenshot)
    fields(item, ['title', 'description'])
    if (item.src != null) image(item)
  }
  if (value.evidenceLinks != null) {
    if (!Array.isArray(value.evidenceLinks) || typeof value.evidenceCheckedAt !== 'string') throw invalid()
    for (const link of value.evidenceLinks) {
      const item = object(link)
      if (typeof item.label !== 'string' || !https(item.url)) throw invalid()
    }
  }
  return value as unknown as Project
}

export async function getProject(slug: string, signal: AbortSignal): Promise<Project> {
  return validateProject(await read('projects/' + encodeURIComponent(slug), signal), slug)
}

export async function getNextProject(slug: string, signal: AbortSignal): Promise<ProjectSummary | undefined> {
  const v = object(await read(`projects/${encodeURIComponent(slug)}/next`, signal))
  if (!('next' in v)) throw invalid()
  return v.next === null ? undefined : summary(v.next)
}
