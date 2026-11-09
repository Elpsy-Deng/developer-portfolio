import { adminRequest, AdminApiError, type AdminSession } from './admin'
export type PasswordProfile = { username: string; role: 'ADMIN' | 'VIEWER'; enabled: boolean; version: number }
export async function getPasswordProfile(): Promise<PasswordProfile> {
  const v: unknown = await (await adminRequest('password')).json()
  if (!v || typeof v !== 'object') throw new AdminApiError('身份資料不完整')
  const row = v as Record<string, unknown>
  if (typeof row.username !== 'string' || !/^[a-z][a-z0-9_-]{2,63}$/.test(row.username) || !['ADMIN', 'VIEWER'].includes(String(row.role))
    || row.enabled !== true || !Number.isSafeInteger(row.version) || Number(row.version) < 1
    || !Object.keys(row).every(k => ['username', 'role', 'enabled', 'version'].includes(k))) throw new AdminApiError('身份資料不完整')
  return v as PasswordProfile
}
export async function changePassword(profile: PasswordProfile, currentPassword: string, newPassword: string, session: AdminSession): Promise<void> {
  const result = await adminRequest('password', { method: 'POST', headers: { 'Content-Type': 'application/json', [session.csrf.headerName]: session.csrf.token },
    body: JSON.stringify({ expectedVersion: profile.version, currentPassword, newPassword }) })
  if (result.status !== 204) throw new AdminApiError('密碼更改結果未確認，請重新登入核對，不要直接重送')
}
