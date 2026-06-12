package br.com.easypet.booking.client.dto;

import br.com.easypet.booking.domain.enums.BillingUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponseDto(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    Integer durationMinutes,
    BillingUnit billingUnit
) {}
