package io.github.senjar.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record EmailRequestDto(
        @NotEmpty
        String to,

        @NotBlank
        String subject,

        @NotBlank
        String htmlBody
) {
}
