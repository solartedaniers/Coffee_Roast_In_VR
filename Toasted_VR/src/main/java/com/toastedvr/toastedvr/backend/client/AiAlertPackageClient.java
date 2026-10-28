package com.toastedvr.toastedvr.backend.client;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Pide al servicio IA (carpeta IA/) el paquete de alertas proactivas de un nivel.
@Component
public class AiAlertPackageClient {

    private static final String PACKAGE_ENDPOINT = "/api/v1/alerts/package";
    private static final String LEVEL_PARAM = "level";

    private final RestClient restClient;

    public AiAlertPackageClient(@Qualifier("aiAlertsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // Sin nivel, el servicio IA usa el nivel por defecto de su catálogo.
    @SuppressWarnings("null")
    public AiAlertPackageReply fetch(KnowledgeLevel level, String ifNoneMatch) {
        try {
            return restClient.get()
                .uri(builder -> {
                    builder.path(PACKAGE_ENDPOINT);
                    if (level != null) {
                        builder.queryParam(LEVEL_PARAM, level.name());
                    }
                    return builder.build();
                })
                .headers(headers -> {
                    if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
                        headers.set(HttpHeaders.IF_NONE_MATCH, ifNoneMatch);
                    }
                })
                .exchange((request, response) -> new AiAlertPackageReply(
                    response.getStatusCode().value(),
                    response.getHeaders().getETag(),
                    response.getBody().readAllBytes()
                ));
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("El servicio IA no entregó el paquete de alertas", e);
        }
    }
}
