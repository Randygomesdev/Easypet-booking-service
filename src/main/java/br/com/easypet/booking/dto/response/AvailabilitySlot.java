package br.com.easypet.booking.dto.response;

import java.util.List;

public record AvailabilitySlot(
    String time,
    boolean available,
    List<br.com.easypet.booking.client.dto.StaffResponseDto> staff,
    String reason,
    Boolean allowFittingRequest
) {
    public AvailabilitySlot(String time, boolean available) {
        this(time, available, List.of(), null, null);
    }
}
