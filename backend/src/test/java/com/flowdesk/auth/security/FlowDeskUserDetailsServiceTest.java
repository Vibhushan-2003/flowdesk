package com.flowdesk.auth.security;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowDeskUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private FlowDeskUserDetailsService userDetailsService;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        userDetailsService = new FlowDeskUserDetailsService(userRepository);
        employeeRole = new Role(RoleCode.EMPLOYEE, "Employee");
    }

    @Test
    @DisplayName("Active user should be loaded with enabled = true")
    void activeUserIsEnabled() {
        User activeUser = new User("active@flowdesk.test", "hash", "Active", "User");
        ReflectionTestUtils.setField(activeUser, "id", UUID.randomUUID());
        activeUser.assignRole(employeeRole);

        when(userRepository.findByEmailIgnoreCase("active@flowdesk.test")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("active@flowdesk.test");

        assertNotNull(userDetails);
        assertTrue(userDetails.isEnabled());
    }

    @Test
    @DisplayName("Invited user should be loaded with enabled = false preventing login")
    void invitedUserIsNotEnabled() {
        User invitedUser = User.createInvitedUser("invited@flowdesk.test", "Invited", "User", "!INVITED!abc");
        ReflectionTestUtils.setField(invitedUser, "id", UUID.randomUUID());
        invitedUser.assignRole(employeeRole);

        when(userRepository.findByEmailIgnoreCase("invited@flowdesk.test")).thenReturn(Optional.of(invitedUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("invited@flowdesk.test");

        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    @DisplayName("Inactive user should be loaded with enabled = false")
    void inactiveUserIsNotEnabled() {
        User inactiveUser = new User("inactive@flowdesk.test", "hash", "Inactive", "User");
        ReflectionTestUtils.setField(inactiveUser, "id", UUID.randomUUID());
        inactiveUser.assignRole(employeeRole);
        inactiveUser.deactivate();

        when(userRepository.findByEmailIgnoreCase("inactive@flowdesk.test")).thenReturn(Optional.of(inactiveUser));

        UserDetails userDetails = userDetailsService.loadUserByUsername("inactive@flowdesk.test");

        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled());
    }

    @Test
    @DisplayName("Unknown email should throw UsernameNotFoundException")
    void unknownUserThrowsException() {
        when(userRepository.findByEmailIgnoreCase("unknown@flowdesk.test")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("unknown@flowdesk.test")
        );
    }
}
