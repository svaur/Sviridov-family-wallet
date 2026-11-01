export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: { field: string; message: string }[]
}

export class HttpError extends Error {
  status: number
  fieldErrors: { field: string; message: string }[]

  constructor(status: number, message: string, fieldErrors: { field: string; message: string }[] = []) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    ...init,
    headers: {
      ...(typeof init?.body === 'string' ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.headers ?? {}),
    },
  })

  if (res.status === 204) return undefined as T

  const text = await res.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }

  if (!res.ok) {
    // A 401 outside the login form means the session is gone (expired, server
    // restart, password changed elsewhere). AuthProvider listens for this and
    // clears the user, which makes RequireAuth show the login page.
    if (res.status === 401 && !path.startsWith('/api/auth/login')) {
      window.dispatchEvent(new Event('wallet:unauthorized'))
    }
    const err = body as ApiErrorBody | null
    throw new HttpError(res.status, err?.message ?? `Ошибка ${res.status}`, err?.fieldErrors ?? [])
  }

  return body as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<T>(path, { method: 'POST', body: form })
  },
}
