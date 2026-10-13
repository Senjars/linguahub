package io.github.senjar.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.senjar.userservice.dto.RegisterRequestDto;
import io.github.senjar.userservice.model.Role;
import io.github.senjar.userservice.model.User;
import io.github.senjar.userservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String KEYCLOAK_ID = "kc-123";
    private static final String USERNAME = "jan";
    private static final String EMAIL = "jan@example.com";
    private static final String PASSWORD = "password123";

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RegistrationService registrationService;

    @Nested
    @DisplayName("register")
    class Register {

        @ParameterizedTest
        @EnumSource(Role.class)
        @DisplayName("Should create the Keycloak user, "
                + "give it the requested role and save the user")
        void register_allowedRole_createsUserAssignsRoleAndSaves(Role role) {
            // GIVEN
            when(keycloakAdminService.createUser(USERNAME, EMAIL, PASSWORD))
                    .thenReturn(KEYCLOAK_ID);

            // WHEN
            registrationService.register(provideRequest(role));

            // THEN
            InOrder order = inOrder(keycloakAdminService, userRepository);
            order.verify(keycloakAdminService).createUser(USERNAME, EMAIL, PASSWORD);
            order.verify(keycloakAdminService).assignRealmRole(KEYCLOAK_ID, role.name());
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            order.verify(userRepository).saveAndFlush(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
            assertThat(saved.getUsername()).isEqualTo(USERNAME);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getRole()).isEqualTo(role);
        }

        @Test
        @DisplayName("Should remove the Keycloak user when the role cannot be assigned")
        void register_roleAssignmentFails_removesKeycloakUserAndRethrows() {
            // GIVEN
            when(keycloakAdminService.createUser(USERNAME, EMAIL, PASSWORD))
                    .thenReturn(KEYCLOAK_ID);
            doThrow(new IllegalStateException("role missing"))
                    .when(keycloakAdminService).assignRealmRole(KEYCLOAK_ID, "STUDENT");

            // WHEN / THEN
            assertThatThrownBy(() -> registrationService.register(provideRequest(Role.STUDENT)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("role missing");
            verify(keycloakAdminService).deleteUser(KEYCLOAK_ID);
            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        @DisplayName("Should remove the Keycloak user when saving the local user fails")
        void register_savingUserFails_removesKeycloakUserAndRethrows() {
            // GIVEN
            when(keycloakAdminService.createUser(USERNAME, EMAIL, PASSWORD))
                    .thenReturn(KEYCLOAK_ID);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new IllegalStateException("db down"));

            // WHEN / THEN
            assertThatThrownBy(() -> registrationService.register(provideRequest(Role.TEACHER)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("db down");
            verify(keycloakAdminService).deleteUser(KEYCLOAK_ID);
        }

        @Test
        @DisplayName("Should still report the original failure "
                + "when removing the Keycloak user fails too")
        void register_cleanupFails_stillThrowsOriginalException() {
            // GIVEN
            when(keycloakAdminService.createUser(USERNAME, EMAIL, PASSWORD))
                    .thenReturn(KEYCLOAK_ID);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new IllegalStateException("db down"));
            doThrow(new IllegalStateException("keycloak down"))
                    .when(keycloakAdminService).deleteUser(KEYCLOAK_ID);

            // WHEN / THEN
            assertThatThrownBy(() -> registrationService.register(provideRequest(Role.STUDENT)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("db down");
        }
    }

    private RegisterRequestDto provideRequest(Role role) {
        return new RegisterRequestDto(
                USERNAME, EMAIL, PASSWORD, "Jan", "Kowalski", role);
    }
}
