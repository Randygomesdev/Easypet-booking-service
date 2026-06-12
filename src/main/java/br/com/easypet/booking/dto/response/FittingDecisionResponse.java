package br.com.easypet.booking.dto.response;

import br.com.easypet.booking.domain.enums.BookingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record FittingDecisionResponse(
    UUID id,
    BookingStatus status,
    Boolean isFittingRequest,
    UUID staffId,
    LocalDateTime decisionDate
) {}
