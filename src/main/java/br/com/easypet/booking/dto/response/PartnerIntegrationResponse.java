package br.com.easypet.booking.dto.response;

import java.util.UUID;

public record PartnerIntegrationResponse(
    UUID id,
    String name,
    java.util.List<PartnerBusinessHourIntegrationResponse> businessHours
) {}
