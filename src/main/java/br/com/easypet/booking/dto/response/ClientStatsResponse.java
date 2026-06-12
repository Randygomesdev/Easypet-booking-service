package br.com.easypet.booking.dto.response;

public record ClientStatsResponse(
        long newClients,
        int month,
        int year
) {}
