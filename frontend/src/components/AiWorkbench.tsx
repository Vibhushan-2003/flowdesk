import { useState } from 'react'
import {
  Sparkles,
  Bot,
  MessageSquare,
  FileCheck2,
} from 'lucide-react'

import { AiTriageCard } from './AiTriageCard'
import { AiSupportCopilotCard } from './AiSupportCopilotCard'
import type { TicketPriority, TicketStatus } from '../types/ticket'

interface AiWorkbenchProps {
  accessToken: string | null
  ticketNumber: string
  currentPriority: TicketPriority
  ticketStatus: TicketStatus
  onTriageApplied: () => Promise<void>
  onGenerateReplyDraft: () => void
  isGeneratingReplyDraft: boolean
  aiReplyDraftNotice: string | null
  aiReplyDraftError: string | null
  onGenerateResolutionSummary: () => void
  isGeneratingResolutionSummary: boolean
  aiResolutionSummaryNotice: string | null
  aiResolutionSummaryError: string | null
}

type TabKey = 'copilot' | 'triage' | 'draft' | 'summary'

export function AiWorkbench({
  accessToken,
  ticketNumber,
  currentPriority,
  ticketStatus,
  onTriageApplied,
  onGenerateReplyDraft,
  isGeneratingReplyDraft,
  aiReplyDraftNotice,
  aiReplyDraftError,
  onGenerateResolutionSummary,
  isGeneratingResolutionSummary,
  aiResolutionSummaryNotice,
  aiResolutionSummaryError,
}: AiWorkbenchProps) {
  const [activeTab, setActiveTab] = useState<TabKey>('copilot')

  return (
    <div className="horizon-ai-workbench-card">
      {/* Workbench Header */}
      <div className="horizon-ai-header">
        <h3 className="horizon-ai-title">
          <Sparkles size={16} />
          <span>FlowDesk AI Assistant</span>
        </h3>
        <span style={{ fontSize: '11px', color: 'var(--color-primary)', fontWeight: 'var(--font-weight-medium)' }}>
          Horizon Intel
        </span>
      </div>

      {/* Tabs */}
      <div className="horizon-ai-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'copilot'}
          className={`horizon-ai-tab-btn ${activeTab === 'copilot' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('copilot')}
        >
          <Bot size={13} />
          <span>Copilot</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'triage'}
          className={`horizon-ai-tab-btn ${activeTab === 'triage' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('triage')}
        >
          <Sparkles size={13} />
          <span>Triage</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'draft'}
          className={`horizon-ai-tab-btn ${activeTab === 'draft' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('draft')}
        >
          <MessageSquare size={13} />
          <span>Reply Draft</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'summary'}
          className={`horizon-ai-tab-btn ${activeTab === 'summary' ? 'is-active' : ''}`}
          onClick={() => setActiveTab('summary')}
        >
          <FileCheck2 size={13} />
          <span>Resolution Summary</span>
        </button>
      </div>

      {/* Body */}
      <div className="horizon-ai-body">
        {activeTab === 'copilot' && (
          <AiSupportCopilotCard accessToken={accessToken} ticketNumber={ticketNumber} />
        )}

        {activeTab === 'triage' && (
          <AiTriageCard
            accessToken={accessToken}
            ticketNumber={ticketNumber}
            currentPriority={currentPriority}
            ticketStatus={ticketStatus}
            onApplied={onTriageApplied}
          />
        )}

        {activeTab === 'draft' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                Draft Customer Reply
              </span>
              <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Assisted response</span>
            </div>

            <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Generates a suggested response based on the conversation history and ticket details. The draft will be inserted directly into your message composer for review and editing before sending.
            </p>

            {aiReplyDraftError && (
              <div className="horizon-auth-alert horizon-auth-alert--error" style={{ margin: 0, padding: '0.5rem' }}>
                <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiReplyDraftError}</span>
              </div>
            )}

            {aiReplyDraftNotice && (
              <div className="horizon-auth-alert horizon-auth-alert--success" style={{ margin: 0, padding: '0.5rem' }}>
                <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiReplyDraftNotice}</span>
              </div>
            )}

            <button
              type="button"
              className="horizon-btn horizon-btn--primary horizon-btn--sm"
              style={{ width: '100%', marginTop: '4px' }}
              onClick={onGenerateReplyDraft}
              disabled={isGeneratingReplyDraft}
            >
              {isGeneratingReplyDraft ? 'Generating Draft...' : 'Generate AI Reply Draft'}
            </button>
          </div>
        )}

        {activeTab === 'summary' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                Resolution Summary Draft
              </span>
              <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Post-incident summary</span>
            </div>

            <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Generates a technical resolution summary for closing tickets in progress. It summarizes the diagnosis, actions taken, and final outcome.
            </p>

            {aiResolutionSummaryError && (
              <div className="horizon-auth-alert horizon-auth-alert--error" style={{ margin: 0, padding: '0.5rem' }}>
                <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiResolutionSummaryError}</span>
              </div>
            )}

            {aiResolutionSummaryNotice && (
              <div className="horizon-auth-alert horizon-auth-alert--success" style={{ margin: 0, padding: '0.5rem' }}>
                <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiResolutionSummaryNotice}</span>
              </div>
            )}

            <button
              type="button"
              className="horizon-btn horizon-btn--primary horizon-btn--sm"
              style={{ width: '100%', marginTop: '4px' }}
              onClick={onGenerateResolutionSummary}
              disabled={isGeneratingResolutionSummary || ticketStatus !== 'IN_PROGRESS'}
            >
              {isGeneratingResolutionSummary
                ? 'Generating Summary...'
                : ticketStatus !== 'IN_PROGRESS'
                ? 'Ticket Must Be In Progress'
                : 'Draft Resolution Summary'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

