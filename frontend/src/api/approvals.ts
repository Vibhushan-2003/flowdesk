import {
  API_BASE_URL,
} from './config'

import type {
  Approval,
  ApprovalsPage,
} from '../types/approval'

export async function getPendingApprovals(
  accessToken: string,
  page = 0,
  size = 10,
): Promise<ApprovalsPage> {
  const searchParams =
    new URLSearchParams({
      page: String(page),
      size: String(size),
    })

  const response =
    await fetch(
      `${API_BASE_URL}/api/approvals/pending?${searchParams.toString()}`,
      {
        method: 'GET',
        headers: {
          Authorization:
            `Bearer ${accessToken}`,
        },
      },
    )

  if (!response.ok) {
    throwApprovalError(
      response.status,
      'load pending approvals',
    )
  }

  return response.json() as Promise<
    ApprovalsPage
  >
}

export async function approveApproval(
  accessToken: string,
  approvalId: string,
  note: string,
): Promise<Approval> {
  const normalizedNote =
    note.trim()

  const response =
    await fetch(
      `${API_BASE_URL}/api/approvals/${approvalId}/approve`,
      {
        method: 'POST',
        headers: {
          Authorization:
            `Bearer ${accessToken}`,
          'Content-Type':
            'application/json',
        },
        body: JSON.stringify({
          note:
            normalizedNote === ''
              ? null
              : normalizedNote,
        }),
      },
    )

  if (!response.ok) {
    throwApprovalError(
      response.status,
      'approve this request',
    )
  }

  return response.json() as Promise<
    Approval
  >
}

export async function rejectApproval(
  accessToken: string,
  approvalId: string,
  reason: string,
): Promise<Approval> {
  const response =
    await fetch(
      `${API_BASE_URL}/api/approvals/${approvalId}/reject`,
      {
        method: 'POST',
        headers: {
          Authorization:
            `Bearer ${accessToken}`,
          'Content-Type':
            'application/json',
        },
        body: JSON.stringify({
          reason: reason.trim(),
        }),
      },
    )

  if (!response.ok) {
    throwApprovalError(
      response.status,
      'reject this request',
    )
  }

  return response.json() as Promise<
    Approval
  >
}

function throwApprovalError(
  status: number,
  action: string,
): never {
  if (status === 400) {
    throw new Error(
      'The approval request contains invalid information.',
    )
  }

  if (status === 401) {
    throw new Error(
      'Your session is no longer valid',
    )
  }

  if (status === 403) {
    throw new Error(
      'You do not have permission to manage approvals.',
    )
  }

  if (status === 404) {
    throw new Error(
      'The approval request could not be found.',
    )
  }

  if (status === 409) {
    throw new Error(
      'This approval can no longer be decided. It may already have been processed or belong to you.',
    )
  }

  throw new Error(
    `Unable to ${action}. Please try again.`,
  )
}