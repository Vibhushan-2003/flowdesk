
export type AiSupportCopilotTool =
  | 'GET_TICKET_OVERVIEW'
  | 'GET_PUBLIC_CONVERSATION'
  | 'GET_SLA_SNAPSHOT'

export interface AiSupportCopilotResponse {
  ticketNumber: string
  answer: string
  toolsUsed: AiSupportCopilotTool[]
  provider: string
  model: string
  generatedAt: string
}
