export interface OperationalReport {
  dateRange: {
    startDate: string
    endDate: string
    generatedAt: string
  }
  ticketVolume: {
    totalCreated: number
    totalResolved: number
    openBacklog: number
  }
  volumeByStatus: Record<string, number>
  volumeByPriority: Record<string, number>
  dailyTrends: {
    date: string
    created: number
    resolved: number
  }[]
  resolutionMetrics: {
    resolvedCount: number
    avgDurationHours: number
    medianDurationHours: number
    avgDurationByPriorityHours: Record<string, number>
  }
  slaCompliance: {
    totalResolved: number
    compliantResolved: number
    complianceRatePercent: number
    responseBreaches: number
    resolutionBreaches: number
  }
  engineerWorkloads: {
    engineerId: string
    engineerName: string
    engineerEmail: string
    activeAssignedTickets: number
    assignedInPeriod: number
    resolvedInPeriod: number
  }[]
  kbDeflection: {
    totalArticles: number
    totalViews: number
    totalDeflections: number
    deflectionRatePercent: number
    topArticles: {
      id: string
      title: string
      slug: string
      categoryName: string
      viewCount: number
      deflectionCount: number
    }[]
  }
}

