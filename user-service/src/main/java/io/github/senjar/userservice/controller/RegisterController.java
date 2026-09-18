package io.github.senjar.userservice.controller;

import io.github.senjar.userservice.dto.RegisterRequestDto;
import io.github.senjar.userservice.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class RegisterController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterRequestDto request) {
        registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
