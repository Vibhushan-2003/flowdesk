package com.flowdesk.user.controller;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.dto.TestFixtureCreateUserRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestFixtureControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TestFixtureController controller;

    @BeforeEach
    void setUp() {
        controller = new TestFixtureController(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Successfully provisions test user with @flowdesk.test email")
    void shouldProvisionTestUserSuccessfully() {
        TestFixtureCreateUserRequest request = new TestFixtureCreateUserRequest(
                "employee.a@flowdesk.test",
                "Password123!",
                "Alice",
                "Employee",
                Set.of(RoleCode.EMPLOYEE)
        );

        when(userRepository.existsByEmailIgnoreCase("employee.a@flowdesk.test")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-pwd");
        Role role = new Role(RoleCode.EMPLOYEE, "Employee");
        when(roleRepository.findByCode(RoleCode.EMPLOYEE)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<UserResponse> response = controller.provisionTestUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("employee.a@flowdesk.test", response.getBody().email());
        assertTrue(response.getBody().roles().contains(RoleCode.EMPLOYEE));
    }

    @Test
    @DisplayName("Rejects non-@flowdesk.test email domain with HTTP 400 Bad Request")
    void shouldRejectNonTestDomain() {
        TestFixtureCreateUserRequest request = new TestFixtureCreateUserRequest(
                "attacker@gmail.com",
                "Password123!",
                "Attacker",
                "User",
                Set.of(RoleCode.ADMIN)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.provisionTestUser(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("@flowdesk.test"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Returns HTTP 409 Conflict when user already exists")
    void shouldRejectDuplicateUser() {
        TestFixtureCreateUserRequest request = new TestFixtureCreateUserRequest(
                "employee.a@flowdesk.test",
                "Password123!",
                "Alice",
                "Employee",
                Set.of(RoleCode.EMPLOYEE)
        );

        when(userRepository.existsByEmailIgnoreCase("employee.a@flowdesk.test")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.provisionTestUser(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }
}
