package br.com.easypet.booking.client.dto;

import java.util.List;
import java.util.UUID;

public record StaffResponseDto(
    UUID id,
    UUID partnerId,
    String name,
    String photoUrl,
    String status,
    List<UUID> serviceIds
) {}
