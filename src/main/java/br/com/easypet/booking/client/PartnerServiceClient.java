package br.com.easypet.booking.client;

import br.com.easypet.booking.client.dto.PartnerResponseDto;
import br.com.easypet.booking.exception.BusinessException;
import br.com.easypet.booking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.partner-service.url:http://localhost:8083/api/v1}")
    private String partnerServiceUrl;

    public PartnerResponseDto getPartnerById(UUID partnerId) {
        String url = partnerServiceUrl + "/partners/" + partnerId;
        log.info("Buscando dados do parceiro síncrono no partner-service: {}", url);
        try {
            return restTemplate.getForObject(url, PartnerResponseDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Parceiro ID {} não encontrado no partner-service", partnerId);
            throw new ResourceNotFoundException("Parceiro não encontrado com o ID: " + partnerId);
        } catch (Exception e) {
            log.error("Erro ao chamar partner-service no endpoint {}: {}", url, e.getMessage(), e);
            throw new BusinessException("Erro de comunicação com o serviço de parceiros.");
        }
    }

    public List<br.com.easypet.booking.client.dto.StaffResponseDto> getStaffByPartnerId(UUID partnerId, UUID serviceId) {
        String url = partnerServiceUrl + "/partners/" + partnerId + "/staff?status=ACTIVE";
        if (serviceId != null) {
            url += "&serviceId=" + serviceId;
        }
        log.info("Buscando profissionais síncronos no partner-service: {}", url);
        try {
            br.com.easypet.booking.client.dto.StaffResponseDto[] response = restTemplate.getForObject(url, br.com.easypet.booking.client.dto.StaffResponseDto[].class);
            return response != null ? List.of(response) : List.of();
        } catch (Exception e) {
            log.error("Erro ao chamar partner-service no endpoint {}: {}", url, e.getMessage(), e);
            throw new BusinessException("Erro de comunicação com o serviço de parceiros.");
        }
    }

    public List<br.com.easypet.booking.client.dto.StaffScheduleResponseDto> getStaffSchedule(UUID staffId) {
        String url = partnerServiceUrl + "/partners/staff/" + staffId + "/schedule";
        log.info("Buscando grade horária do profissional no partner-service: {}", url);
        try {
            br.com.easypet.booking.client.dto.StaffScheduleResponseDto[] response = restTemplate.getForObject(url, br.com.easypet.booking.client.dto.StaffScheduleResponseDto[].class);
            return response != null ? List.of(response) : List.of();
        } catch (Exception e) {
            log.error("Erro ao obter grade horária no endpoint {}: {}", url, e.getMessage(), e);
            throw new BusinessException("Erro de comunicação com o serviço de parceiros.");
        }
    }

    public List<br.com.easypet.booking.client.dto.StaffAbsenceResponseDto> getStaffAbsences(UUID staffId) {
        String url = partnerServiceUrl + "/partners/staff/" + staffId + "/absences";
        log.info("Buscando ausências do profissional no partner-service: {}", url);
        try {
            br.com.easypet.booking.client.dto.StaffAbsenceResponseDto[] response = restTemplate.getForObject(url, br.com.easypet.booking.client.dto.StaffAbsenceResponseDto[].class);
            return response != null ? List.of(response) : List.of();
        } catch (Exception e) {
            log.error("Erro ao obter ausências no endpoint {}: {}", url, e.getMessage(), e);
            throw new BusinessException("Erro de comunicação com o serviço de parceiros.");
        }
    }
}
