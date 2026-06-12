package br.com.easypet.booking.client.dto;

import java.util.UUID;

public record CreditConsumeResponse(
    UUID customerPackageId,
    Integer remainingCredits,
    Boolean success
) {}
