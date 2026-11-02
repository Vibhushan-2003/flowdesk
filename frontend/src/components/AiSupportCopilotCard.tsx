import {
  type FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react'
import ReactMarkdown from 'react-markdown'
import {
  Bot,
  Send,
  AlertCircle,
  Database,
  FileText,
  Clock,
  BookOpen,
} from 'lucide-react'

import { askAiSupportCopilot } from '../api/aiSupportCopilot'
import type {
  AiSupportCopilotResponse,
  AiSupportCopilotTool,
} from '../types/aiSupportCopilot'
import { Button } from './ui/Button'

interface Props {
  accessToken: string | null
  ticketNumber: string
}

const toolLabels: Record<AiSupportCopilotTool, string> = {
  GET_TICKET_OVERVIEW: 'Ticket Overview',
  GET_PUBLIC_CONVERSATION: 'Public Conversation',
  GET_SLA_SNAPSHOT: 'SLA Policy',
  SEARCH_KNOWLEDGE_BASE: 'Knowledge Base',
}

function getToolIcon(tool: AiSupportCopilotTool) {
  switch (tool) {
    case 'GET_TICKET_OVERVIEW':
      return <FileText size={12} />
    case 'GET_PUBLIC_CONVERSATION':
      return <Database size={12} />
    case 'GET_SLA_SNAPSHOT':
      return <Clock size={12} />
    case 'SEARCH_KNOWLEDGE_BASE':
      return <BookOpen size={12} />
  }
}

const QUICK_PROMPTS = [
  'Summarize the core technical issue',
  'What is the next recommended action?',
  'Check SLA deadlines and risk',
  'Find relevant Knowledge Base articles',
]

export function AiSupportCopilotCard({ accessToken, ticketNumber }: Props) {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<AiSupportCopilotResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isAsking, setIsAsking] = useState(false)

  const requestSequence = useRef(0)

  useEffect(() => {
    return () => {
      requestSequence.current += 1
    }
  }, [])

  async function handleAskQuestion(query: string) {
    if (isAsking) return

    const nextQuestion = query.trim()
    if (!nextQuestion || nextQuestion.length > 1000) {
      setError('Enter a question of no more than 1000 characters.')
      return
    }

    if (!accessToken) {
      setError('Your session is no longer valid.')
      return
    }

    const currentRequest = ++requestSequence.current
    setIsAsking(true)
    setError(null)
    setResult(null)

    try {
      const response = await askAiSupportCopilot(accessToken, ticketNumber, nextQuestion)
      if (currentRequest === requestSequence.current) {
        setResult(response)
      }
    } catch (caughtError) {
      if (currentRequest === requestSequence.current) {
        setError(
          caughtError instanceof Error ? caughtError.message : 'Unable to get answer from AI Copilot.',
        )
      }
    } finally {
      if (currentRequest === requestSequence.current) {
        setIsAsking(false)
      }
    }
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    void handleAskQuestion(question)
  }

  return (
    <div className="horizon-ai-copilot" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      {/* Header Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Bot size={16} style={{ color: 'var(--color-primary)' }} />
          <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
            Support Copilot
          </span>
        </div>
        <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>Grounded on live ticket data</span>
      </div>

      {/* Quick Prompt Pills */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
        {QUICK_PROMPTS.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="horizon-ai-tool-pill"
            style={{ cursor: isAsking ? 'not-allowed' : 'pointer', background: 'var(--color-canvas)' }}
            disabled={isAsking}
            onClick={() => {
              setQuestion(prompt)
              void handleAskQuestion(prompt)
            }}
          >
            {prompt}
          </button>
        ))}
      </div>

      {/* Input Box */}
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
        <div className="horizon-input-wrapper" style={{ flex: 1 }}>
          <input
            type="text"
            id="ai-copilot-question-input"
            className="horizon-input"
            style={{ fontSize: 'var(--font-size-xs)', padding: '6px 10px' }}
            placeholder="Ask Copilot about this ticket..."
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={isAsking}
            maxLength={1000}
            aria-label="Ask Copilot about this ticket"
          />
        </div>
        <Button
          variant="primary"
          size="sm"
          type="submit"
          disabled={!question.trim() || isAsking}
          isLoading={isAsking}
          leadingIcon={<Send size={13} />}
          aria-label="Ask Copilot"
        />
      </form>

      {/* Error state */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: 0, padding: '0.5rem' }}>
          <AlertCircle size={15} className="horizon-auth-alert-icon" />
          <span style={{ fontSize: 'var(--font-size-xs)' }}>{error}</span>
        </div>
      )}

      {/* Result Display */}
      {result && (
        <div className="horizon-ai-answer-box">
          {/* Grounding tools used badges */}
          {result.toolsUsed && result.toolsUsed.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginBottom: '8px', paddingBottom: '6px', borderBottom: '1px solid var(--color-divider)' }}>
              <span style={{ fontSize: '10px', color: 'var(--color-text-muted)', display: 'inline-flex', alignItems: 'center', marginRight: '4px' }}>
                Grounding Sources:
              </span>
              {result.toolsUsed.map((tool) => (
                <span key={tool} className="horizon-ai-tool-pill">
                  {getToolIcon(tool)}
                  <span>{toolLabels[tool] || tool}</span>
                </span>
              ))}
            </div>
          )}

          {/* Answer markdown */}
          <div style={{ fontSize: 'var(--font-size-xs)', lineHeight: 'var(--line-height-relaxed)' }}>
            <ReactMarkdown>{result.answer}</ReactMarkdown>
          </div>

          <div style={{ marginTop: '8px', textAlign: 'right', fontSize: '10px', color: 'var(--color-text-muted)' }}>
            Generated with {result.model}
          </div>
        </div>
      )}
    </div>
  )
}
