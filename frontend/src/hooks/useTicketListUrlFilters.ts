
import { type FormEvent, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import type { TicketListFilters } from '../api/ticket'
import type {
  TicketPriority,
  TicketStatus,
} from '../types/ticket'

const STATUSES: TicketStatus[] = [
  'PENDING_APPROVAL',
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'RESOLVED',
  'CLOSED',
  'CANCELLED',
]

const PRIORITIES: TicketPriority[] = [
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]

interface DraftFilters {
  urlKey: string
  searchText: string
  selectedStatus: TicketStatus | ''
  selectedPriority: TicketPriority | ''
  selectedSort: 'newest' | 'oldest'
}

function readUrlState(params: URLSearchParams) {
  const rawPage = params.get('page') ?? '1'

  const parsedPage = /^\d+$/.test(rawPage)
    ? Number(rawPage)
    : 1

  const page =
    Number.isSafeInteger(parsedPage) && parsedPage > 0
      ? Math.min(parsedPage - 1, 999999)
      : 0

  const rawStatus = params.get('status') ?? ''

  const status: TicketStatus | '' =
    STATUSES.includes(rawStatus as TicketStatus)
      ? (rawStatus as TicketStatus)
      : ''

  const rawPriority = params.get('priority') ?? ''

  const priority: TicketPriority | '' =
    PRIORITIES.includes(rawPriority as TicketPriority)
      ? (rawPriority as TicketPriority)
      : ''

  const filters: TicketListFilters = {
    search: (params.get('search') ?? '')
      .trim()
      .slice(0, 100),
    status,
    priority,
    sort:
      params.get('sort') === 'oldest'
        ? 'oldest'
        : 'newest',
  }

  return { page, filters }
}

function toUrlParams(
  page: number,
  filters: TicketListFilters,
) {
  const params = new URLSearchParams()

  if (filters.search?.trim()) {
    params.set('search', filters.search.trim())
  }

  if (filters.status) {
    params.set('status', filters.status)
  }

  if (filters.priority) {
    params.set('priority', filters.priority)
  }

  if (filters.sort === 'oldest') {
    params.set('sort', 'oldest')
  }

  if (page > 0) {
    params.set('page', String(page + 1))
  }

  return params
}

export function useTicketListUrlFilters() {
  const [searchParams, setSearchParams] =
    useSearchParams()

  const urlKey = searchParams.toString()

  const { page, filters: appliedFilters } = useMemo(
    () => readUrlState(searchParams),
    [searchParams],
  )

  const [draft, setDraft] =
    useState<DraftFilters | null>(null)

  // A draft only belongs to the URL it was edited on.
  // Back/Forward navigation automatically restores URL values.
  const activeDraft =
    draft?.urlKey === urlKey ? draft : null

  const searchText =
    activeDraft?.searchText ??
    appliedFilters.search ??
    ''

  const selectedStatus =
    activeDraft?.selectedStatus ??
    appliedFilters.status ??
    ''

  const selectedPriority =
    activeDraft?.selectedPriority ??
    appliedFilters.priority ??
    ''

  const selectedSort =
    activeDraft?.selectedSort ??
    appliedFilters.sort ??
    'newest'

  function updateDraft(
    changes: Partial<
      Omit<DraftFilters, 'urlKey'>
    >,
  ) {
    setDraft({
      urlKey,
      searchText,
      selectedStatus,
      selectedPriority,
      selectedSort,
      ...changes,
    })
  }

  function setSearchText(value: string) {
    updateDraft({ searchText: value })
  }

  function setSelectedStatus(
    value: TicketStatus | '',
  ) {
    updateDraft({ selectedStatus: value })
  }

  function setSelectedPriority(
    value: TicketPriority | '',
  ) {
    updateDraft({ selectedPriority: value })
  }

  function setSelectedSort(
    value: 'newest' | 'oldest',
  ) {
    updateDraft({ selectedSort: value })
  }

  function applyFilters(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    setDraft(null)

    setSearchParams(
      toUrlParams(0, {
        search: searchText.trim(),
        status: selectedStatus,
        priority: selectedPriority,
        sort: selectedSort,
      }),
    )
  }

  function resetFilters() {
    setDraft(null)
    setSearchParams(new URLSearchParams())
  }

  function setPage(
    next: number | ((previous: number) => number),
  ) {
    const value =
      typeof next === 'function'
        ? next(page)
        : next

    setDraft(null)

    setSearchParams(
      toUrlParams(
        Math.max(0, value),
        appliedFilters,
      ),
    )
  }

  return {
    page,
    setPage,
    appliedFilters,
    searchText,
    setSearchText,
    selectedStatus,
    setSelectedStatus,
    selectedPriority,
    setSelectedPriority,
    selectedSort,
    setSelectedSort,
    applyFilters,
    resetFilters,
  }
}
