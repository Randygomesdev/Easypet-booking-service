package br.com.easypet.booking.client.dto;

import java.time.LocalTime;
import java.util.UUID;

public record StaffScheduleResponseDto(
    UUID id,
    UUID staffId,
    Integer dayOfWeek,
    LocalTime startTime,
    LocalTime endTime
) {}
