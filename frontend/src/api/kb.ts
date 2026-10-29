import { API_BASE_URL } from './config'
import type {
  ArticleStatus,
  CreateKbArticlePayload,
  CreateKbCategoryPayload,
  KbArticle,
  KbArticleDeflection,
  KbArticleSummary,
  KbCategory,
  PageResponse,
  UpdateKbArticlePayload,
  UpdateKbCategoryPayload,
} from '../types/kb'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to perform this action')
    }
    if (response.status === 404) {
      throw new Error('Article or category not found')
    }

    let errorMessage = `Request failed (${response.status})`
    try {
      const errorJson = (await response.json()) as { message?: string; error?: string }
      if (errorJson.message) {
        errorMessage = errorJson.message
      } else if (errorJson.error) {
        errorMessage = errorJson.error
      }
    } catch {
      // Use fallback error message
    }
    throw new Error(errorMessage)
  }

  return response.json() as Promise<T>
}

// ----------------------------------------------------------------------------
// Public / Employee Endpoints
// ----------------------------------------------------------------------------

export async function getCategories(token: string): Promise<KbCategory[]> {
  const response = await fetch(`${API_BASE_URL}/api/kb/categories`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<KbCategory[]>(response)
}

export async function getArticles(
  token: string,
  categoryId?: string,
  search?: string,
  page = 0,
  size = 10,
): Promise<PageResponse<KbArticleSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  if (categoryId) {
    params.set('categoryId', categoryId)
  }
  if (search && search.trim()) {
    params.set('search', search.trim())
  }

  const response = await fetch(`${API_BASE_URL}/api/kb/articles?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<PageResponse<KbArticleSummary>>(response)
}

export async function getArticleBySlug(token: string, slug: string): Promise<KbArticle> {
  const response = await fetch(`${API_BASE_URL}/api/kb/articles/${encodeURIComponent(slug)}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<KbArticle>(response)
}

export async function getDeflectionSuggestions(
  token: string,
  query: string,
): Promise<KbArticleDeflection[]> {
  if (!query || query.trim().length < 3) {
    return []
  }

  const params = new URLSearchParams({
    query: query.trim(),
  })

  const response = await fetch(`${API_BASE_URL}/api/kb/articles/deflect?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<KbArticleDeflection[]>(response)
}

// ----------------------------------------------------------------------------
// Support Engineer / Team Lead / Admin Endpoints
// ----------------------------------------------------------------------------

export async function getManagementArticles(
  token: string,
  status?: ArticleStatus | '',
  categoryId?: string,
  search?: string,
  page = 0,
  size = 10,
): Promise<PageResponse<KbArticleSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  if (status) {
    params.set('status', status)
  }
  if (categoryId) {
    params.set('categoryId', categoryId)
  }
  if (search && search.trim()) {
    params.set('search', search.trim())
  }

  const response = await fetch(`${API_BASE_URL}/api/support/kb/articles?${params.toString()}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<PageResponse<KbArticleSummary>>(response)
}

export async function getArticleByIdForManagement(token: string, id: string): Promise<KbArticle> {
  const response = await fetch(`${API_BASE_URL}/api/support/kb/articles/${id}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<KbArticle>(response)
}

export async function createDraftArticle(
  token: string,
  payload: CreateKbArticlePayload,
): Promise<KbArticle> {
  const response = await fetch(`${API_BASE_URL}/api/support/kb/articles`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })
  return handleResponse<KbArticle>(response)
}

export async function updateArticle(
  token: string,
  id: string,
  payload: UpdateKbArticlePayload,
): Promise<KbArticle> {
  const response = await fetch(`${API_BASE_URL}/api/support/kb/articles/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })
  return handleResponse<KbArticle>(response)
}

export async function transitionArticleStatus(
  token: string,
  id: string,
  status: ArticleStatus,
): Promise<KbArticle> {
  const response = await fetch(`${API_BASE_URL}/api/support/kb/articles/${id}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ status }),
  })
  return handleResponse<KbArticle>(response)
}

// ----------------------------------------------------------------------------
// Admin / Team Lead Category Endpoints
// ----------------------------------------------------------------------------

export async function getAdminCategories(token: string): Promise<KbCategory[]> {
  const response = await fetch(`${API_BASE_URL}/api/admin/kb/categories`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
  return handleResponse<KbCategory[]>(response)
}

export async function createAdminCategory(
  token: string,
  payload: CreateKbCategoryPayload,
): Promise<KbCategory> {
  const response = await fetch(`${API_BASE_URL}/api/admin/kb/categories`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })
  return handleResponse<KbCategory>(response)
}

export async function updateAdminCategory(
  token: string,
  id: string,
  payload: UpdateKbCategoryPayload,
): Promise<KbCategory> {
  const response = await fetch(`${API_BASE_URL}/api/admin/kb/categories/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })
  return handleResponse<KbCategory>(response)
}
