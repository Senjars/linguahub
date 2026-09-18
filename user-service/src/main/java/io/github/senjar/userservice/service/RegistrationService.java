package io.github.senjar.userservice.service;

import io.github.senjar.userservice.dto.RegisterRequestDto;
import io.github.senjar.userservice.model.User;
import io.github.senjar.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final KeycloakAdminService keycloakAdminService;
    private final UserRepository userRepository;

    @Transactional
    public void register(RegisterRequestDto request) {
        String keycloakId = keycloakAdminService.createUser(
                request.username(), request.email(), request.password());

        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        userRepository.save(user);
    }
}
