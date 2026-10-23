import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import type { TicketListFilters } from '../api/ticket'
import type { TicketPriority, TicketStatus } from '../types/ticket'

const STATUSES: TicketStatus[] = [
  'PENDING_APPROVAL', 'OPEN', 'ASSIGNED', 'IN_PROGRESS',
  'WAITING_FOR_USER', 'RESOLVED', 'CLOSED', 'CANCELLED',
]

const PRIORITIES: TicketPriority[] = [
  'LOW', 'MEDIUM', 'HIGH', 'CRITICAL',
]

function readUrlState(params: URLSearchParams) {
  const rawPage = params.get('page') ?? '1'
  const parsedPage = /^\d+$/.test(rawPage) ? Number(rawPage) : 1
  const page = Number.isSafeInteger(parsedPage) && parsedPage > 0
    ? Math.min(parsedPage - 1, 999999)
    : 0

  const rawStatus = params.get('status') ?? ''
  const status: TicketStatus | '' = STATUSES.includes(rawStatus as TicketStatus)
    ? rawStatus as TicketStatus : ''

  const rawPriority = params.get('priority') ?? ''
  const priority: TicketPriority | '' = PRIORITIES.includes(rawPriority as TicketPriority)
    ? rawPriority as TicketPriority : ''

  const filters: TicketListFilters = {
    search: (params.get('search') ?? '').trim().slice(0, 100),
    status,
    priority,
    sort: params.get('sort') === 'oldest' ? 'oldest' : 'newest',
  }

  return { page, filters }
}

function toUrlParams(page: number, filters: TicketListFilters) {
  const params = new URLSearchParams()
  if (filters.search?.trim()) params.set('search', filters.search.trim())
  if (filters.status) params.set('status', filters.status)
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.sort === 'oldest') params.set('sort', 'oldest')
  if (page > 0) params.set('page', String(page + 1))
  return params
}

export function useTicketListUrlFilters() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { page, filters: appliedFilters } = useMemo(
    () => readUrlState(searchParams), [searchParams],
  )

  const [searchText, setSearchText] = useState(appliedFilters.search ?? '')
  const [selectedStatus, setSelectedStatus] = useState<TicketStatus | ''>(appliedFilters.status ?? '')
  const [selectedPriority, setSelectedPriority] = useState<TicketPriority | ''>(appliedFilters.priority ?? '')
  const [selectedSort, setSelectedSort] = useState<'newest' | 'oldest'>(appliedFilters.sort ?? 'newest')

  // Browser back/forward or a pasted URL must update the form too.
  useEffect(() => {
    setSearchText(appliedFilters.search ?? '')
    setSelectedStatus(appliedFilters.status ?? '')
    setSelectedPriority(appliedFilters.priority ?? '')
    setSelectedSort(appliedFilters.sort ?? 'newest')
  }, [appliedFilters])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSearchParams(toUrlParams(0, {
      search: searchText.trim(),
      status: selectedStatus,
      priority: selectedPriority,
      sort: selectedSort,
    }))
  }

  function resetFilters() {
    setSearchText('')
    setSelectedStatus('')
    setSelectedPriority('')
    setSelectedSort('newest')
    setSearchParams(new URLSearchParams())
  }

  function setPage(next: number | ((previous: number) => number)) {
    const value = typeof next === 'function' ? next(page) : next
    setSearchParams(toUrlParams(Math.max(0, value), appliedFilters))
  }

  return {
    page, setPage, appliedFilters,
    searchText, setSearchText,
    selectedStatus, setSelectedStatus,
    selectedPriority, setSelectedPriority,
    selectedSort, setSelectedSort,
    applyFilters, resetFilters,
  }
}
