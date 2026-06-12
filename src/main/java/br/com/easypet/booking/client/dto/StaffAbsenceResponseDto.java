package br.com.easypet.booking.client.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaffAbsenceResponseDto(
    UUID id,
    UUID staffId,
    LocalDateTime startDate,
    LocalDateTime endDate,
    String reason
) {}
