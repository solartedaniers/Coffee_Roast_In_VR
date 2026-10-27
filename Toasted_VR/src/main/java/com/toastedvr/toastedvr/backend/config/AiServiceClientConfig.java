package com.toastedvr.toastedvr.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Un cliente HTTP por tipo de llamada al servicio IA, porque cada una tiene
// su propio tiempo de espera: la retroalimentación espera al modelo y la
// ingesta puede tardar varios minutos vectorizando PDFs.
@Configuration
public class AiServiceClientConfig {

    @Bean
    public RestClient aiFeedbackRestClient(AiServiceProperties properties) {
        return buildRestClient(properties, properties.getFeedbackReadTimeoutMillis());
    }

    @SuppressWarnings("null")
    private static RestClient buildRestClient(AiServiceProperties properties, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(readTimeoutMillis);

        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }
}
