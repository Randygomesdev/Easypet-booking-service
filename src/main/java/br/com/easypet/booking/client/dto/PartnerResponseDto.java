package br.com.easypet.booking.client.dto;

import java.util.List;
import java.util.UUID;

public record PartnerResponseDto(
    UUID id,
    String name,
    Integer boardingCapacity,
    List<ServiceResponseDto> services
) {}
