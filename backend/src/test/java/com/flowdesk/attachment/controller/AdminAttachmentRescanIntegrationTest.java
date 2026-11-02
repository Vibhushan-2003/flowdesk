package com.flowdesk.attachment.controller;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.ScanStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.dto.AttachmentRescanReport;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.service.TicketAttachmentService;
import com.flowdesk.attachment.storage.AttachmentStorageService;
import com.flowdesk.attachment.storage.StoredFile;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAttachmentRescanIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketAttachmentService attachmentService;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentStorageService storageService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Ticket testTicket;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleCode.ADMIN, "Administrator")));

        testAdmin = new User("admin." + UUID.randomUUID() + "@flowdesk.test", "hash", "Admin", "User");
        testAdmin.getRoles().add(adminRole);
        testAdmin = userRepository.save(testAdmin);

        testTicket = new Ticket(
                "RESCAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                TicketType.INCIDENT,
                "Rescan Ticket",
                "Testing administrative rescan",
                testAdmin
        );
        testTicket = ticketRepository.save(testTicket);
    }

    @Test
    @DisplayName("Security: non-admin users cannot access rescan-pending endpoint")
    void nonAdminCannotAccessRescan() throws Exception {
        mockMvc.perform(post("/api/admin/attachments/rescan-pending")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin: successfully rescans pending attachment and marks it clean with active test scanner")
    void adminCanRescanPendingAttachments() throws Exception {
        String content = "Safe test attachment for rescan validation";
        StoredFile stored = storageService.store(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), content.length());

        TicketAttachment attachment = new TicketAttachment(
                testTicket,
                testAdmin,
                "rescan-test.txt",
                "rescan-test.txt",
                "text/plain",
                content.length(),
                stored.storageKey(),
                "a".repeat(64),
                AttachmentStatus.AVAILABLE,
                ScanStatus.PENDING_SCAN
        );
        attachment = attachmentRepository.saveAndFlush(attachment);
        assertThat(attachment.getScanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);

        mockMvc.perform(post("/api/admin/attachments/rescan-pending")
                        .param("batchSize", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.cleanCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        TicketAttachment refreshed = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(refreshed.getScanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(refreshed.getStatus()).isEqualTo(AttachmentStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Concurrency: concurrent rescan requests process attachments safely without duplicate release")
    void concurrentRescanRequestsAreSafe() throws Exception {
        String content1 = "Concurrent safe attachment 1";
        StoredFile stored1 = storageService.store(new ByteArrayInputStream(content1.getBytes(StandardCharsets.UTF_8)), content1.length());
        TicketAttachment att1 = new TicketAttachment(
                testTicket, testAdmin, "c1.txt", "c1.txt", "text/plain",
                content1.length(), stored1.storageKey(), "b".repeat(64),
                AttachmentStatus.AVAILABLE, ScanStatus.PENDING_SCAN
        );
        attachmentRepository.saveAndFlush(att1);

        String content2 = "Concurrent safe attachment 2";
        StoredFile stored2 = storageService.store(new ByteArrayInputStream(content2.getBytes(StandardCharsets.UTF_8)), content2.length());
        TicketAttachment att2 = new TicketAttachment(
                testTicket, testAdmin, "c2.txt", "c2.txt", "text/plain",
                content2.length(), stored2.storageKey(), "c".repeat(64),
                AttachmentStatus.AVAILABLE, ScanStatus.PENDING_SCAN
        );
        attachmentRepository.saveAndFlush(att2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<AttachmentRescanReport> f1 = executor.submit(() -> attachmentService.rescanPendingAttachments(10));
        Future<AttachmentRescanReport> f2 = executor.submit(() -> attachmentService.rescanPendingAttachments(10));

        AttachmentRescanReport r1 = f1.get(10, TimeUnit.SECONDS);
        AttachmentRescanReport r2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        int totalClean = r1.cleanCount() + r2.cleanCount();
        assertThat(totalClean).isEqualTo(2);

        TicketAttachment ref1 = attachmentRepository.findById(att1.getId()).orElseThrow();
        TicketAttachment ref2 = attachmentRepository.findById(att2.getId()).orElseThrow();
        assertThat(ref1.getScanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(ref2.getScanStatus()).isEqualTo(ScanStatus.CLEAN);
    }
}
