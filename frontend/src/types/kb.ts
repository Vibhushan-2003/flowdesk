import type { PageResponse } from './ticket'

export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface KbCategory {
  id: string
  name: string
  slug: string
  description?: string
  icon?: string
  displayOrder: number
  active: boolean
  articleCount: number
}

export interface KbArticleSummary {
  id: string
  title: string
  slug: string
  summary: string
  status: ArticleStatus
  categoryId: string
  categoryName: string
  categorySlug: string
  authorId?: string
  authorName: string
  viewCount: number
  publishedAt?: string
  updatedAt: string
}

export interface KbArticle {
  id: string
  title: string
  slug: string
  summary: string
  contentMarkdown: string
  status: ArticleStatus
  categoryId: string
  categoryName: string
  categorySlug: string
  authorId?: string
  authorName: string
  viewCount: number
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface KbArticleDeflection {
  id: string
  title: string
  slug: string
  summary: string
  categoryName: string
  categorySlug: string
}

export interface CreateKbArticlePayload {
  categoryId: string
  title: string
  summary: string
  contentMarkdown: string
}

export interface UpdateKbArticlePayload {
  categoryId: string
  title: string
  summary: string
  contentMarkdown: string
}

export interface UpdateKbArticleStatusPayload {
  status: ArticleStatus
}

export interface CreateKbCategoryPayload {
  name: string
  description?: string
  icon?: string
  displayOrder?: number
}

export interface UpdateKbCategoryPayload {
  name: string
  description?: string
  icon?: string
  displayOrder?: number
  active?: boolean
}

export type { PageResponse }
