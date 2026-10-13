package io.github.senjar.userservice.service;

import io.github.senjar.userservice.dto.RegisterRequestDto;
import io.github.senjar.userservice.model.User;
import io.github.senjar.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserRepository userRepository;

    @Transactional
    public void register(RegisterRequestDto request) {
        String keycloakId = keycloakAdminService.createUser(
                request.username(), request.email(), request.password());

        try {
            keycloakAdminService.assignRealmRole(keycloakId, request.role().name());
            userRepository.saveAndFlush(toUser(request, keycloakId));
        } catch (RuntimeException e) {
            removeKeycloakUser(keycloakId);
            throw e;
        }
    }

    private User toUser(RegisterRequestDto request, String keycloakId) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        return user;
    }

    private void removeKeycloakUser(String keycloakId) {
        try {
            keycloakAdminService.deleteUser(keycloakId);
        } catch (RuntimeException e) {
            log.error("Could not remove Keycloak user {} after a failed registration",
                    keycloakId, e);
        }
    }
}
