package br.com.easypet.booking.service;

import br.com.easypet.booking.dto.response.PartnerIntegrationResponse;
import br.com.easypet.booking.dto.response.PartnerBusinessHourIntegrationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PartnerIntegrationService {

    private final RestTemplate restTemplate;
    
    private static final String PARTNER_SERVICE_URL = "http://localhost:8083/api/v1/partners/";

    public PartnerIntegrationResponse getPartnerDetails(UUID partnerId) {
        log.info("Buscando detalhes do parceiro {} no partner-service", partnerId);
        try {
            String url = PARTNER_SERVICE_URL + partnerId.toString();
            return restTemplate.getForObject(url, PartnerIntegrationResponse.class);
        } catch (Exception e) {
            log.error("Erro ao obter detalhes do parceiro {}: {}", partnerId, e.getMessage());
            // Fallback resiliente caso o partner-service esteja temporariamente indisponível
            java.util.List<PartnerBusinessHourIntegrationResponse> defaultHours = new java.util.ArrayList<>();
            for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
                if (day == java.time.DayOfWeek.SUNDAY) {
                    defaultHours.add(new PartnerBusinessHourIntegrationResponse(day, null, null, null, null, true));
                } else if (day == java.time.DayOfWeek.SATURDAY) {
                    defaultHours.add(new PartnerBusinessHourIntegrationResponse(day, "08:00", "13:00", null, null, false));
                } else {
                    defaultHours.add(new PartnerBusinessHourIntegrationResponse(day, "08:00", "18:00", "12:00", "13:00", false));
                }
            }
            return new PartnerIntegrationResponse(
                partnerId,
                "Parceiro EasyPet",
                defaultHours
            );
        }
    }
}
