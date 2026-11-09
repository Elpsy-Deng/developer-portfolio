import { adminRequest, AdminApiError, type AdminSession } from './admin'
export type Account = { username: string; role: 'ADMIN' | 'VIEWER'; enabled: boolean; version: number }
export type IdentityAudit = { id: string; username: string; actor: string; action: 'CREATE' | 'UPDATE' | 'PASSWORD_CHANGE'; before: Account | null; after: Account; createdAt: string }
function account(value: unknown): value is Account {
  if (!value || typeof value !== 'object') return false
  const v = value as Record<string, unknown>
  return typeof v.username === 'string' && /^[a-z][a-z0-9_-]{2,63}$/.test(v.username) && ['ADMIN', 'VIEWER'].includes(String(v.role))
    && typeof v.enabled === 'boolean' && Number.isSafeInteger(v.version) && Number(v.version) > 0
    && Object.keys(v).every(k => ['username', 'role', 'enabled', 'version'].includes(k))
}
export async function listAccounts(): Promise<Account[]> {
  const v: unknown = await (await adminRequest('accounts')).json()
  if (!Array.isArray(v) || !v.every(account)) throw new AdminApiError('帳號資料回應不完整')
  return v
}
export async function listIdentityAudit(): Promise<IdentityAudit[]> {
  const v: unknown = await (await adminRequest('accounts/audit')).json()
  if (!Array.isArray(v) || !v.every(x => x && typeof x === 'object' && typeof x.id === 'string' && typeof x.actor === 'string'
    && typeof x.username === 'string' && ['CREATE', 'UPDATE', 'PASSWORD_CHANGE'].includes(x.action) && (x.before === null || account(x.before))
    && account(x.after) && typeof x.createdAt === 'string')) throw new AdminApiError('帳號稽核回應不完整')
  return v
}
export async function createAccount(command: { username: string; password: string; role: Account['role']; enabled: boolean }, session: AdminSession): Promise<Account> {
  return write('accounts', 'POST', command, session)
}
export async function updateAccount(username: string, command: { expectedVersion: number; role: Account['role']; enabled: boolean }, session: AdminSession): Promise<Account> {
  return write(`accounts/${encodeURIComponent(username)}`, 'PUT', command, session)
}
async function write(path: string, method: string, command: object, session: AdminSession): Promise<Account> {
  const v: unknown = await (await adminRequest(path, { method, headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token }, body: JSON.stringify(command) })).json()
  if (!account(v)) throw new AdminApiError('帳號儲存結果不完整，請重新核對，不要直接重送')
  return v
}
