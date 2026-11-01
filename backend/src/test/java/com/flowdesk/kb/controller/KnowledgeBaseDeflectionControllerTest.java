package com.flowdesk.kb.controller;

import com.flowdesk.kb.service.KnowledgeBaseService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseDeflectionControllerTest {

    @Mock
    private KnowledgeBaseService kbService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KnowledgeBaseController kbController;

    @Test
    @DisplayName("recordDeflection: increments deflection count and returns 204 No Content")
    void recordDeflectionAuthenticated() {
        UUID articleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        User user = mock(User.class);

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ResponseEntity<Void> response = kbController.recordDeflection(jwt, articleId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(kbService).recordDeflection(articleId, user);
    }

    @Test
    @DisplayName("recordDeflection: handles unauthenticated invocation safely")
    void recordDeflectionUnauthenticated() {
        UUID articleId = UUID.randomUUID();

        ResponseEntity<Void> response = kbController.recordDeflection(null, articleId);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(kbService).recordDeflection(articleId, null);
    }
}

