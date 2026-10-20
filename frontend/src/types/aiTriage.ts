import type {
  TicketPriority,
} from './ticket'

export interface AiTriageRecommendationResponse {
  recommendationId: string
  ticketId: string
  ticketNumber: string
  suggestedPriority: TicketPriority
  confidence: number
  reason: string
  provider: string
  model: string
  promptVersion: string
  generatedByUserId: string
  createdAt: string
  applied: boolean
  appliedByUserId: string | null
  appliedAt: string | null
}

export interface AiTriageApplicationResponse {
  recommendationId: string
  ticketId: string
  ticketNumber: string
  previousPriority: TicketPriority
  appliedPriority: TicketPriority
  previousResponseDueAt: string
  responseDueAt: string
  previousResolutionDueAt: string
  resolutionDueAt: string
  appliedByUserId: string
  appliedAt: string
}