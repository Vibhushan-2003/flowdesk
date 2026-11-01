package com.flowdesk.report.repository;

import com.flowdesk.ticket.domain.Ticket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportQueryRepository extends JpaRepository<Ticket, UUID> {

    interface StatusCount {
        String getStatus();
        Long getCount();
    }

    interface PriorityCount {
        String getPriority();
        Long getCount();
    }

    interface DateCount {
        java.sql.Date getDate();
        Long getCount();
    }

    interface PriorityAvgDuration {
        String getPriority();
        Double getAvgDurationSeconds();
    }

    interface EventTypeCount {
        String getEventType();
        Long getCount();
    }

    interface EngineerWorkload {
        UUID getUserId();
        String getFirstName();
        String getLastName();
        String getEmail();
        Long getActiveAssigned();
        Long getAssignedInPeriod();
        Long getResolvedInPeriod();
    }

    @Query(value = """
            SELECT t.status AS status, COUNT(t.id) AS count
            FROM tickets t
            WHERE t.created_at >= :start AND t.created_at < :end
            GROUP BY t.status
            """, nativeQuery = true)
    List<StatusCount> countVolumeByStatus(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT t.priority AS priority, COUNT(t.id) AS count
            FROM tickets t
            WHERE t.created_at >= :start AND t.created_at < :end
            GROUP BY t.priority
            """, nativeQuery = true)
    List<PriorityCount> countVolumeByPriority(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT CAST(t.created_at AS date) AS date, COUNT(t.id) AS count
            FROM tickets t
            WHERE t.created_at >= :start AND t.created_at < :end
            GROUP BY CAST(t.created_at AS date)
            ORDER BY date ASC
            """, nativeQuery = true)
    List<DateCount> countDailyCreated(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT CAST(t.resolved_at AS date) AS date, COUNT(t.id) AS count
            FROM tickets t
            WHERE t.resolved_at >= :start AND t.resolved_at < :end
            GROUP BY CAST(t.resolved_at AS date)
            ORDER BY date ASC
            """, nativeQuery = true)
    List<DateCount> countDailyResolved(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)))
            FROM tickets t
            WHERE t.resolved_at >= :start AND t.resolved_at < :end
            """, nativeQuery = true)
    Double getOverallAvgResolutionDurationSeconds(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT t.priority AS priority,
                   AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at))) AS avgDurationSeconds
            FROM tickets t
            WHERE t.resolved_at >= :start AND t.resolved_at < :end
            GROUP BY t.priority
            """, nativeQuery = true)
    List<PriorityAvgDuration> getAvgResolutionDurationByPriority(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT e.event_type AS eventType, COUNT(e.id) AS count
            FROM sla_events e
            WHERE e.occurred_at >= :start AND e.occurred_at < :end
            GROUP BY e.event_type
            """, nativeQuery = true)
    List<EventTypeCount> countSlaEventsByType(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT COUNT(DISTINCT t.id)
            FROM tickets t
            JOIN sla_events e ON t.id = e.ticket_id
            WHERE t.resolved_at >= :start AND t.resolved_at < :end
              AND e.event_type = 'RESOLUTION_BREACHED'
            """, nativeQuery = true)
    Long countResolvedTicketsWithResolutionBreach(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT u.id AS userId, u.first_name AS firstName, u.last_name AS lastName, u.email AS email,
                   COUNT(DISTINCT CASE WHEN ta.released_at IS NULL THEN ta.id END) AS activeAssigned,
                   COUNT(DISTINCT CASE WHEN ta.assigned_at >= :start AND ta.assigned_at < :end THEN ta.id END) AS assignedInPeriod,
                   COUNT(DISTINCT CASE WHEN t.status IN ('RESOLVED', 'CLOSED') AND t.resolved_at >= :start AND t.resolved_at < :end AND ta.released_at IS NOT NULL THEN t.id END) AS resolvedInPeriod
            FROM users u
            JOIN user_roles ur ON u.id = ur.user_id
            JOIN roles r ON ur.role_id = r.id AND r.code = 'SUPPORT_ENGINEER'
            LEFT JOIN ticket_assignments ta ON u.id = ta.assigned_to_user_id
            LEFT JOIN tickets t ON ta.ticket_id = t.id
            GROUP BY u.id, u.first_name, u.last_name, u.email
            ORDER BY activeAssigned DESC, u.last_name ASC
            """, nativeQuery = true)
    List<EngineerWorkload> getEngineerWorkloads(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT t
            FROM Ticket t
            LEFT JOIN FETCH t.createdByUser
            WHERE t.createdAt >= :start AND t.createdAt < :end
            ORDER BY t.createdAt DESC
            """)
    List<Ticket> findTicketsForExport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            Pageable pageable
    );
}

