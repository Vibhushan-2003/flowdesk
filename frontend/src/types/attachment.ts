export type AttachmentScanStatus = 'PENDING_SCAN' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED'

export interface TicketAttachmentResponse {
  id: string
  ticketNumber: string
  filename: string
  contentType: string
  sizeBytes: number
  uploadedByUserId: string
  uploadedByName: string
  createdAt: string
  scanStatus?: AttachmentScanStatus
  scanDetails?: string | null
}
