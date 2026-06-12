package br.com.easypet.booking.client.dto;

import java.util.UUID;

public record CreditConsumeRequest(
    UUID userId,
    UUID partnerId,
    UUID serviceId
) {}
