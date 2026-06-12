package br.com.easypet.booking.dto.response;

import java.math.BigDecimal;

public record RevenueStatsResponse(
        BigDecimal revenue,
        int month,
        int year
) {}
