package br.com.easypet.booking.dto.request;

import br.com.easypet.booking.domain.enums.BookingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingRequest(
    @NotNull(message = "O ID do pet é obrigatório")
    UUID petId,

    @NotNull(message = "O ID do parceiro é obrigatório")
    UUID partnerId,

    LocalDateTime bookingDate,

    @NotNull(message = "O tipo do agendamento é obrigatório")
    BookingType type,

    @Size(max = 1000, message = "O campo 'notes' não pode exceder 1000 caracteres")
    String notes,

    @NotNull(message = "O preço é obrigatório")
    @Positive(message = "O preço deve ser maior que zero")
    BigDecimal price,

    LocalDateTime checkIn,
    
    LocalDateTime checkOut,

    UUID serviceId,
    br.com.easypet.booking.domain.enums.PaymentMethod paymentMethod,
    UUID customerPackageId,

    UUID staffId,
    Boolean requestFitting
) {}
