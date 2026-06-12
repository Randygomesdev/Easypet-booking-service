package br.com.easypet.booking.dto.response;

import br.com.easypet.booking.domain.enums.BookingStatus;
import br.com.easypet.booking.domain.enums.BookingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    UUID petId,
    UUID partnerId,
    UUID userId,
    LocalDateTime bookingDate,
    BookingType type,
    BookingStatus status,
    String notes,
    BigDecimal price,
    LocalDateTime checkIn,
    LocalDateTime checkOut,
    UUID serviceId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    br.com.easypet.booking.domain.enums.PaymentMethod paymentMethod,
    UUID customerPackageId,
    UUID staffId,
    Boolean isFittingRequest
) {}
