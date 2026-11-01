import { createApi } from '../http'

export { HttpError } from '../http'
export type { ApiErrorBody } from '../http'

export const adminApi = createApi({
  loginPath: '/api/admin/auth/login',
  unauthorizedEvent: 'admin:unauthorized',
})
