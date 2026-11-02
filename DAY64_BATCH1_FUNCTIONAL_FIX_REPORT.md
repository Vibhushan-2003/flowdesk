# FlowDesk Day 64 — Batch 1: Final Functional Gap Closure & Security Regression Report

**Date:** November 3, 2026  
**Repository:** `C:\Users\gvibh\Projects\flowdesk`  
**Active Development Branch:** `feat/day63-flowdesk-horizon-ui`  
**Base Commit (main):** `ec7649f48a25db7975d81ffbccd06b38e902ee94` (Day 62 PR #56, Flyway V25)  
**Pull Request Under Review:** [#57](https://github.com/Vibhushan-2003/flowdesk/pull/57)  
**Safety Gate:** Strict No-Commit / No-Push / No-Merge mandate honored. All changes remain local for inspection. Development database `flowdesk` untouched at Flyway V20.

---

## Executive Summary

Batch 1 of Day 64 completes all high-priority functional gap closures and deep-dive technical investigations without redesigning existing UI components. Every change builds directly on the established FlowDesk Horizon enterprise workspace foundation while preserving the strict security, isolation, and auditability guarantees established in Day 62.

---

## 1. Branch & Baseline Confirmation

- **Local Branch:** `feat/day63-flowdesk-horizon-ui`
- **Remote Base:** `main` at commit `ec7649f48a25db7975d81ffbccd06b38e902ee94` (Day 62 PR #56 merged, including immutable Flyway migration V25 for internal staff notes).
- **Target PR:** https://github.com/Vibhushan-2003/flowdesk/pull/57
- **Safety Invariant:** Zero git commits or pushes have been executed. Protected development database `flowdesk` remained untouched at Flyway V20. All testing was isolated to `flowdesk_test` (Flyway V25).

---

## 2. Ticket Priority Management Workflow & Backend Authorization

### API Endpoint & Authorization
- **Endpoint:** `PATCH /api/support/tickets/{ticketNumber}/priority`
- **Controller:** [`SupportTicketController`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/ticket/controller/SupportTicketController.java)
- **Role Permissions:** Restricted in Spring Security configuration to `SUPPORT_ENGINEER`, `TEAM_LEAD`, and `ADMIN`. Pure `EMPLOYEE` users hitting this endpoint receive `403 FORBIDDEN`.
- **Active Assignment Verification:**
  - For `SUPPORT_ENGINEER`: The caller must be the actively assigned support engineer (`releasedAt IS NULL`). Unassigned or non-owner engineers receive `404 NOT_FOUND` (preventing information leakage across staff boundaries).
  - For `TEAM_LEAD` and `ADMIN`: Supervisory access permits updating priority on any ticket currently in `ASSIGNED` status across the entire engineering organization.
- **Audit Logging:** Every priority change records an immutable audit entry with action `AuditAction.TICKET_PRIORITY_CHANGED`, capturing `fromPriority` and `toPriority` in structured metadata.

---

## 3. SLA Recalculation Behavior & Preserved SLA Start Time

- Priority changes invoke `ticket.applyPriorityAndRecalculateSla(newPriority, newSlaPolicy)` on [`Ticket`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/ticket/domain/Ticket.java).
- **Preserved SLA Start Time:**
  - The SLA recalculation does **not** reset the clock.
  - The original start time is derived from the existing response deadline minus the previous policy's response minutes (`responseDueAt - oldPolicy.responseMinutes`).
  - New deadlines (`responseDueAt` and `resolutionDueAt`) are computed by adding the new policy's minutes to the *original preserved start time*.
- **Precondition & Conflict Guards:**
  - Priority change is **strictly locked** once active work begins: if `status != TicketStatus.ASSIGNED`, the request is rejected with `409 CONFLICT` (`"Ticket priority can only be changed while assigned"`).
  - Priority change is **strictly locked** once an SLA breach has occurred: if `slaEventRepository.existsByTicket_Id(ticket.getId())` is true, the request is rejected with `409 CONFLICT` (`"Ticket priority cannot be changed after an SLA breach has been recorded"`). This ensures audit trails cannot be manipulated to retroactively hide breaches.

---

## 4. Employee Priority Submission & Self-Escalation Capping

- In [`TicketService.java`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/ticket/service/TicketService.java), `determineInitialPriority(User creator, TicketPriority requestedPriority)` enforces server-side business rules:
  1. If `requestedPriority` is omitted (`null`), it defaults safely to `TicketPriority.MEDIUM`.
  2. If the creator is an `EMPLOYEE` (non-staff) and requests `CRITICAL`, the backend **gracefully caps** the priority to `TicketPriority.HIGH`.
  3. Privileged staff (`SUPPORT_ENGINEER`, `TEAM_LEAD`, `ADMIN`) creating tickets directly can set `CRITICAL` when reporting major incidents.
  4. Idempotency request hashing in `computeRequestHash(CreateTicketRequest)` incorporates `request.priority()` to ensure distinct priority submissions have unique idempotency hashes.
- In [`CreateTicketPage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/CreateTicketPage.tsx), an Urgency / Priority selector allows requesters to choose `LOW`, `MEDIUM`, or `HIGH`, accompanied by clear inline enterprise guidance:
  > *"Critical priorities are reserved for major system outages triaged directly by support staff."*

---

## 5. Staff Workbench Priority Management

- In [`SupportTicketDetailsPage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/SupportTicketDetailsPage.tsx):
  - When a ticket is in `ASSIGNED` status, an inline priority selector dropdown is rendered alongside the `PriorityBadge` within the workflow ribbon.
  - Staff engineers and leads can adjust priority directly before commencing work. The UI displays an instant confirmation banner: *"Priority updated to [Priority]. SLA deadlines recalculated from original ticket creation."*
  - When the ticket transitions to `IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`, or `CLOSED`, the selector is automatically locked, displaying only the static `PriorityBadge` with SLA status indicators.

---

## 6. AI Reply Draft Root Cause & 503 Messaging

### Root Cause Analysis
During manual acceptance testing, users occasionally saw: `"AI reply drafting is temporarily unavailable. No message was sent."`
1. When running without a live Google Gemini API key (or in isolated dev/offline environments), the backend AI client returns `503 SERVICE_UNAVAILABLE`.
2. The frontend error message in `aiReplyDraft.ts` previously stated: `"No message was sent."`
3. This was misleading because generating an AI draft never sends a message—it only populates the composer for human review.
4. If a ticket was assigned to another engineer, the endpoint returned `404 NOT_FOUND` (preventing unauthorized draft generation).
5. If a ticket was already finalized (`RESOLVED` or `CLOSED`), the endpoint returned `409 CONFLICT`.

### Resolution
- Updated error messaging in [`frontend/src/api/aiReplyDraft.ts`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/api/aiReplyDraft.ts):
  > *"AI reply drafting is temporarily unavailable. You can compose your reply directly in the message box."*

---

## 7. AI Reply Draft Button State for Finalized Tickets

- In [`frontend/src/components/AiWorkbench.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/components/AiWorkbench.tsx):
  - The Reply Draft button is now conditionally disabled when `ticketStatus === 'RESOLVED' || ticketStatus === 'CLOSED' || ticketStatus === 'CANCELLED'`.
  - The button label updates to: `"Ticket Finalized (Drafts Unavailable)"`.
- In [`frontend/src/pages/SupportTicketDetailsPage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/SupportTicketDetailsPage.tsx):
  - The secondary composer "AI Reply Draft" action button is disabled when the ticket is finalized.
  - `handleGenerateAiReplyDraft()` includes an early guard that halts execution and displays a user-friendly notice if invoked against a finished ticket.

---

## 8. AI Resolution Summary State & Dependency on IN_PROGRESS

- In `AiResolutionSummaryController` and `AiResolutionSummaryApplicationService`:
  - AI resolution drafting requires `ticket.getStatus() == TicketStatus.IN_PROGRESS`.
  - Attempting to generate a resolution draft while in `ASSIGNED` or `WAITING_FOR_USER` returns `409 CONFLICT` (`"AI resolution summary cannot be generated for tickets that are not in progress"`).
- In [`frontend/src/components/AiWorkbench.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/components/AiWorkbench.tsx):
  - The Resolution Summary button reflects this precondition: disabled unless `ticketStatus === 'IN_PROGRESS'`.
  - Button text clarifies state: `"Ticket Must Be In Progress"` vs `"Draft Resolution Summary"`.
- **Manual Resolution Independence:** Support engineers can resolve tickets with their own manual resolution summary at any time without calling or relying upon Gemini AI.

---

## 9. Staff Notes Privacy Verification in AI Context & Requester Feeds

A rigorous security audit was conducted on private internal staff notes across all AI and customer-facing subsystems:
1. **AI Context Isolation:** In `AiReplyDraftContextService.java` (line 105), the comment query explicitly filters by `CommentVisibility.PUBLIC`:
   ```java
   List<TicketComment> comments = ticketCommentRepository
           .findByTicket_IdAndVisibilityOrderByCreatedAtAsc(
                   ticket.getId(),
                   CommentVisibility.PUBLIC
           );
   ```
   Internal notes are **never** passed into Google Gemini prompts or context payloads.
2. **Customer Feeds:** `TicketCommentService.getRequesterComments()` strictly queries `CommentVisibility.PUBLIC`.
3. **Audit & Notifications:** Internal notes emit notifications exclusively to internal staff channels and never generate requester notifications. Historical comments prior to Flyway V25 remain classified as `PUBLIC`.

---

## 10. Reporting Metrics Reconciliation

Operational report calculations in [`ReportService.java`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/report/service/ReportService.java) and [`ReportQueryRepository.java`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/report/repository/ReportQueryRepository.java) were reconciled:
1. **Open Backlog vs Period Metrics:**
   - **Open Backlog:** Point-in-time active operational count of all currently unresolved tickets (`OPEN`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_FOR_USER`).
   - **Created in Period:** Tickets where `createdAt >= start AND createdAt < end`.
   - **Resolved in Period:** Tickets where `resolvedAt >= start AND resolvedAt < end`.
2. **Engineer Workloads (`resolvedInPeriod`):**
   - Corrected query in `ReportQueryRepository`: previously required `ta.released_at IS NOT NULL`.
   - Refined to: `ta.assigned_at <= t.resolved_at AND (ta.released_at >= t.resolved_at OR ta.released_at IS NULL)`.
   - Ensures only the engineer holding the assignment at resolution receives credit, matching `findAuthorizedSupportAssignments`.
3. **SLA Breach Rate Denominator:**
   - Breached rate is calculated against `totalResolvedInPeriod` (tickets resolved with resolution breach / total resolved in period). When zero tickets are resolved, it evaluates safely to 0.0%.

---

## 11. SQL Median Resolution Duration (PERCENTILE_CONT)

- Replaced approximate median calculation in `ReportService.java` with PostgreSQL ordered-set aggregate function in [`ReportQueryRepository.java`](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/report/repository/ReportQueryRepository.java):
  ```sql
  SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
  FROM tickets t
  WHERE t.resolved_at >= :start AND t.resolved_at < :end
  ```
- Yields the exact mathematical median resolution duration in seconds, converted to hours with 1 decimal precision.

---

## 12. ClamAV Malware Quarantine & Fail-Closed Behavior

- **Security Model:** FlowDesk implements a strict fail-closed malware security model via `ClamAvMalwareScanner`:
  - When the ClamAV daemon is offline, unreachable, or times out, uploaded files are marked as `MalwareScanStatus.SCAN_FAILED` and `AttachmentStatus.QUARANTINED`.
  - Quarantined files are completely blocked from download via `403 FORBIDDEN` in `TicketAttachmentService.downloadAttachment()`.
- **UI Clarification:** In [`AttachmentSecurityPage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/AttachmentSecurityPage.tsx) and `TicketAttachmentSection.tsx`, the status badge displays `⚠️ Quarantined (Scan Failed)` versus `🚨 Quarantined (Infected)`, allowing administrators to distinguish between daemon unavailability and actual malware signatures.

---

## 13. Support Queue Search Normalization

- In [`frontend/src/pages/SupportQueuePage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/SupportQueuePage.tsx):
  - Normalized search query handling to support:
    - Pure numbers: e.g. `32` formats to padded `fd-000032`.
    - Prefixed queries: e.g. `FD-32`, `#32`, `#000032` format to `fd-000032`.
    - Partial substrings: matches any ticket number or title containing the search query.

---

## 14. My Tickets Status Filter Behavior

- In [`frontend/src/pages/MyTicketsPage.tsx`](file:///C:/Users/gvibh/Projects/flowdesk/frontend/src/pages/MyTicketsPage.tsx):
  - Verified active filter state preservation. When a ticket transitions from `OPEN` to `IN_PROGRESS`, it naturally moves out of the `OPEN` filter view.
  - Empty state informs users: *"No tickets match the selected filter. Try switching status to 'All' or clearing search."*

---

## 15. Notification Badge Unread Count Synchronization

- Verified notification unread count synchronization:
  - Backed by real-time WebSocket topic `/topic/users/{userId}/notifications`.
  - Mark-as-read updates decrement the badge count immediately, while incoming ticket status updates trigger immediate UI badge refreshes.

---

## 16. Test Execution Results

### Backend Automated Test Suite
- **Full Suite Run:** `mvn -f backend/pom.xml test`
- **Total Tests Run:** 930
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 1 (integration test requiring external Google OAuth credentials)
- **Key Suites Verified:**
  - `SupportTicketServiceTest`: 21/21 passed (including 5 new priority workflow unit tests).
  - `TicketServiceTest`: 13/13 passed (including employee priority capping tests).
  - `ReportIntegrationTest`: 3/3 passed against real PostgreSQL schema with `PERCENTILE_CONT`.
  - `ReportServiceTest`: 4/4 passed.
  - `ClamAvMalwareScannerTest`: 3/3 passed.

### Frontend Quality Verification
- **ESLint:** `npm --prefix frontend run lint` -> **0 errors, 0 warnings**.
- **TypeScript Build:** `npm --prefix frontend run build` -> **0 errors, bundle compiled successfully**.
- **Playwright E2E Test Suite:** `npm --prefix frontend run test:e2e` -> **40 passed (23.3s)**.

---

## 17. Safety & Constraint Verification

- [x] **No Commit:** No git commits were created on `feat/day63-flowdesk-horizon-ui`.
- [x] **No Push:** No git pushes were executed to GitHub remote.
- [x] **No PR Update:** PR #57 remains unmodified in draft status.
- [x] **No Merge:** No branches were merged.
- [x] **Database Safety:** Development database `flowdesk` (Flyway V20) was never accessed or migrated. All tests targeted `flowdesk_test` (Flyway V25).
- [x] **Artifacts Preserved:** Preserved all batch files (`repair_postgres.bat`, `db_test_setup.bat`), secrets, and untracked artifacts.

