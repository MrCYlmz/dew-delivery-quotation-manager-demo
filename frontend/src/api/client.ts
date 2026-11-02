import type {
  AddressSide,
  ApiErrorBody,
  BoardCard,
  DeliveryDetail,
  DeliveryListRow,
  DeliveryMoveBody,
  DeliveryQuery,
  DeliveryWriteBody,
  EstimateComparables,
  EstimateComparablesQuery,
  ParsedPackingList,
} from './types'

const BASE = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly detail: string | null
  readonly fields: Record<string, string> | null

  constructor(status: number, message: string, body?: ApiErrorBody | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = body?.detail ?? null
    this.fields = body?.fields ?? null
  }
}

export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Could not reach the server. Is the backend running on port 8080?')
    this.name = 'NetworkError'
    this.cause = cause
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    const parts: string[] = []
    if (err.status === 404) parts.push('Not found.')
    else if (err.status === 422) parts.push('The file could not be parsed.')
    else if (err.status === 400) parts.push('The server rejected the data.')
    else if (err.status >= 500) {
      parts.push(`The server could not handle the request (HTTP ${err.status}).`)
    } else parts.push(`Request failed (HTTP ${err.status}).`)
    if (err.detail) parts.push(err.detail)
    else if (err.message && err.message !== 'validation') parts.push(err.message)
    return parts.join(' ')
  }
  if (err instanceof NetworkError) return err.message
  if (err instanceof Error) return err.message
  return String(err)
}

async function readErrorBody(res: Response): Promise<ApiErrorBody | null> {
  try {
    const text = await res.text()
    if (!text) return null
    return JSON.parse(text) as ApiErrorBody
  } catch {
    return null
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(BASE + path, init)
  } catch (cause) {
    throw new NetworkError(cause)
  }
  if (!res.ok) {
    const body = await readErrorBody(res)
    throw new ApiError(res.status, body?.error ?? res.statusText ?? 'request failed', body)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

function queryString<P extends { [K in keyof P]: string | string[] | undefined }>(params: P): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries<string | string[] | undefined>(params)) {
    if (Array.isArray(value)) {
      for (const repeated of value) search.append(key, repeated)
    } else if (value !== undefined && value !== '') {
      search.set(key, value)
    }
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}

export function listDeliveries(query: DeliveryQuery): Promise<DeliveryListRow[]> {
  return request<DeliveryListRow[]>(`/deliveries${queryString(query)}`)
}

export function getDelivery(id: number): Promise<DeliveryDetail> {
  return request<DeliveryDetail>(`/deliveries/${id}`)
}

export function createDelivery(body: DeliveryWriteBody): Promise<DeliveryDetail> {
  return request<DeliveryDetail>('/deliveries', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function updateDelivery(id: number, body: DeliveryWriteBody): Promise<DeliveryDetail> {
  return request<DeliveryDetail>(`/deliveries/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function deleteDelivery(id: number): Promise<void> {
  return request<void>(`/deliveries/${id}`, { method: 'DELETE' })
}

export function getBoard(): Promise<BoardCard[]> {
  return request<BoardCard[]>('/board')
}

export function moveDelivery(id: number, body: DeliveryMoveBody): Promise<BoardCard> {
  return request<BoardCard>(`/deliveries/${id}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function uploadPdfs(id: number, files: File[]): Promise<unknown> {
  const form = new FormData()
  for (const file of files) form.append('files', file, file.name)
  return request<unknown>(`/deliveries/${id}/pdfs`, { method: 'POST', body: form })
}

export function deletePdf(id: number, pdfId: number): Promise<void> {
  return request<void>(`/deliveries/${id}/pdfs/${pdfId}`, { method: 'DELETE' })
}

export function pdfContentUrl(id: number, pdfId: number): string {
  return `${BASE}/deliveries/${id}/pdfs/${pdfId}/content`
}

export function uploadExcel(id: number, file: File): Promise<DeliveryDetail> {
  const form = new FormData()
  form.append('file', file, file.name)
  return request<DeliveryDetail>(`/deliveries/${id}/excel`, { method: 'PUT', body: form })
}

export function excelDownloadUrl(id: number): string {
  return `${BASE}/deliveries/${id}/excel`
}

export function parseExcel(file: File): Promise<ParsedPackingList> {
  const form = new FormData()
  form.append('file', file, file.name)
  return request<ParsedPackingList>('/excel/parse', { method: 'POST', body: form })
}

export function suggestCompanies(): Promise<string[]> {
  return request<string[]>('/suggestions/companies')
}

export function suggestCountries(field: AddressSide): Promise<string[]> {
  return request<string[]>(`/suggestions/countries${queryString({ field })}`)
}

export function suggestCities(field: AddressSide, country?: string): Promise<string[]> {
  return request<string[]>(`/suggestions/cities${queryString({ field, country })}`)
}

export function getEstimateComparables(query: EstimateComparablesQuery): Promise<EstimateComparables> {
  return request<EstimateComparables>(`/estimate/comparables${queryString(query)}`)
}
