
import {
  type FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react'

import { askAiSupportCopilot } from '../api/aiSupportCopilot'

import type {
  AiSupportCopilotResponse,
  AiSupportCopilotTool,
} from '../types/aiSupportCopilot'

import './AiSupportCopilotCard.css'

interface Props {
  accessToken: string | null
  ticketNumber: string
}

const toolLabels: Record<AiSupportCopilotTool, string> = {
  GET_TICKET_OVERVIEW: 'Ticket overview',
  GET_PUBLIC_CONVERSATION: 'Public conversation',
  GET_SLA_SNAPSHOT: 'SLA snapshot',
}

export function AiSupportCopilotCard({
  accessToken,
  ticketNumber,
}: Props) {
  const [question, setQuestion] = useState('')

  const [result, setResult] =
    useState<AiSupportCopilotResponse | null>(null)

  const [error, setError] = useState<string | null>(null)

  const [isAsking, setIsAsking] = useState(false)

  const requestSequence = useRef(0)

  useEffect(() => {
    return () => {
      requestSequence.current += 1
    }
  }, [])

  async function handleAsk(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (isAsking) return

    const nextQuestion = question.trim()

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
      const response = await askAiSupportCopilot(
        accessToken,
        ticketNumber,
        nextQuestion,
      )

      if (currentRequest !== requestSequence.current) return

      if (response.ticketNumber !== ticketNumber) {
        setError('Copilot returned an unexpected ticket reference.')
        return
      }

      setResult(response)
    } catch (caughtError) {
      if (currentRequest !== requestSequence.current) return

      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to get a Copilot answer.',
      )
    } finally {
      if (currentRequest === requestSequence.current) {
        setIsAsking(false)
      }
    }
  }

  return (
    <section
      className="ai-copilot-card"
      aria-label="Read-only Support Copilot"
    >
      <div className="ai-copilot-heading">
        <div>
          <p className="support-section-label">AI assistance</p>
          <h3>Support Copilot</h3>
        </div>

        <span className="ai-copilot-readonly">Read-only</span>
      </div>

      <p className="ai-copilot-help">
        Ask about this assigned ticket, its public conversation,
        or SLA urgency. Copilot gives advice only and cannot
        change the ticket or send messages.
      </p>

      <form onSubmit={(event) => void handleAsk(event)}>
        <label htmlFor="ai-copilot-question">
          Your question
        </label>

        <textarea
          id="ai-copilot-question"
          value={question}
          onChange={(event) => {
            setQuestion(event.target.value)
            setError(null)
          }}
          rows={3}
          maxLength={1000}
          placeholder="What should I focus on next?"
          disabled={isAsking}
        />

        <div className="ai-copilot-actions">
          <span>{question.length}/1000</span>

          <button
            type="submit"
            className="primary-button"
            disabled={
              isAsking ||
              !question.trim() ||
              !accessToken
            }
          >
            {isAsking
              ? 'Asking Copilot...'
              : 'Ask Copilot'}
          </button>
        </div>
      </form>

      {isAsking && (
        <p role="status">
          Reviewing available ticket information...
        </p>
      )}

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {result && (
        <div
          className="ai-copilot-answer"
          aria-live="polite"
        >
          <h4>Copilot suggestion</h4>

          <p className="ai-copilot-answer-text">
            {result.answer}
          </p>

          <p className="ai-copilot-help">
            Review this suggestion before taking action
            through the normal FlowDesk workflow.
          </p>

          {result.toolsUsed.length > 0 && (
            <p className="ai-copilot-sources">
              Information consulted:{' '}
              {result.toolsUsed
                .map((tool) => toolLabels[tool] ?? tool)
                .join(', ')}
            </p>
          )}

          <p className="ai-copilot-model">
            Generated using {result.model}
          </p>
        </div>
      )}
    </section>
  )
}
