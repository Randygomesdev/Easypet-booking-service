package br.com.easypet.booking.dto.response;

public record DailyStatsEntry(
        String day,   // ISO date: "YYYY-MM-DD"
        long total
) {}
