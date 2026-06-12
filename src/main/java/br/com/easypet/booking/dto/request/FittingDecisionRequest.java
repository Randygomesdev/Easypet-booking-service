package br.com.easypet.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FittingDecisionRequest(
    @NotBlank(message = "A decisão é obrigatória")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "A decisão deve ser APPROVE ou REJECT")
    String decision
) {}
