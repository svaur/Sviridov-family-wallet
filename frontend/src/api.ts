import { createApi } from './http'

export { HttpError } from './http'
export type { ApiErrorBody } from './http'

export const api = createApi({ loginPath: '/api/auth/login', unauthorizedEvent: 'wallet:unauthorized' })
