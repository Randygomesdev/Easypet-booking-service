package br.com.easypet.booking.dto.response;

import java.time.DayOfWeek;

public record PartnerBusinessHourIntegrationResponse(
    DayOfWeek dayOfWeek,
    String businessStartHour,
    String businessEndHour,
    String lunchStartHour,
    String lunchEndHour,
    boolean closed
) {}
