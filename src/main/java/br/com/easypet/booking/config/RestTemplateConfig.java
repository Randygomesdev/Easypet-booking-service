package br.com.easypet.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (interceptors == null) {
            interceptors = new ArrayList<>();
        }
        
        interceptors.add((request, body, execution) -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String authHeader = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && !authHeader.isEmpty()) {
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, authHeader);
                }
            }
            return execution.execute(request, body);
        });
        
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
