package br.com.easypet.booking.client;

import br.com.easypet.booking.client.dto.CreditConsumeRequest;
import br.com.easypet.booking.client.dto.CreditConsumeResponse;
import br.com.easypet.booking.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment-service.url:http://localhost:8085/api/v1}")
    private String paymentServiceUrl;

    public CreditConsumeResponse consumeCredit(UUID userId, UUID partnerId, UUID serviceId) {
        String url = paymentServiceUrl + "/payments/packages/consume";
        log.info("Efetuando chamada síncrona REST para debitar crédito no payment-service: {}", url);
        
        CreditConsumeRequest request = new CreditConsumeRequest(userId, partnerId, serviceId);

        try {
            return restTemplate.postForObject(url, request, CreditConsumeResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Falha de regra de negócio ao consumir crédito: {}", e.getResponseBodyAsString());
            // Extrair mensagem do JSON de erro se possível, ou passar a mensagem original
            throw new BusinessException("Falha no pagamento com créditos: Saldo insuficiente ou inválido.");
        } catch (Exception e) {
            log.error("Erro ao comunicar com o payment-service no endpoint {}: {}", url, e.getMessage(), e);
            throw new BusinessException("Erro de comunicação com o serviço de pagamentos para validar os créditos do pacote.");
        }
    }
}
