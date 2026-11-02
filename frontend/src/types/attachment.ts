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

export interface AttachmentRescanReport {
  totalPending: number
  scannedCount: number
  cleanCount: number
  infectedCount: number
  failedCount: number
  message: string
}
