package io.github.senjar.notificationservice.controller;

import io.github.senjar.notificationservice.dto.EmailRequestDto;
import io.github.senjar.notificationservice.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;

    @PostMapping("/email")
    public ResponseEntity<Void> sendEmail(@RequestBody @Valid EmailRequestDto request) {
        emailService.sendEmail(request);
        return ResponseEntity.ok().build();
    }
}
