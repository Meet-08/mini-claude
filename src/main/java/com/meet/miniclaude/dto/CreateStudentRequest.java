package com.meet.miniclaude.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStudentRequest(
        @NotBlank(message = "Name is mandatory") String name,
        @NotBlank(message = "Email is mandatory") @Email(message = "Email should be valid") String email
) {
}