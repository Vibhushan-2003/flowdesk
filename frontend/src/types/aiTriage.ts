import type { TicketPriority } from './ticket'

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
}