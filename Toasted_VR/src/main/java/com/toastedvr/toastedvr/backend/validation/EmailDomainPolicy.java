package com.toastedvr.toastedvr.backend.validation;

import java.util.Locale;
import java.util.Optional;

import com.toastedvr.toastedvr.backend.config.EmailDomainProperties;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import org.springframework.stereotype.Component;

// Rechaza correos con un dominio que no existe o que es un error de escritura
// de un proveedor conocido. Los dominios mal escritos a veces sí existen
// (gail.com, hotmial.com están registrados), por eso no basta con el DNS.
// Se aplica solo al registro; el formato lo revisa EmailPolicy.
@Component
public class EmailDomainPolicy {

    private final EmailDomainProperties properties;
    private final EmailDomainLookup lookup;
    private final MessageResolver messages;

    public EmailDomainPolicy(EmailDomainProperties properties, EmailDomainLookup lookup, MessageResolver messages) {
        this.properties = properties;
        this.lookup = lookup;
        this.messages = messages;
    }

    /** Mensaje para el usuario si el dominio no sirve; vacío si está bien. */
    public Optional<String> findProblem(String email) {
        int at = email.lastIndexOf('@');
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);

        Optional<String> suggestion = findTypoSuggestion(domain);
        if (suggestion.isPresent()) {
            return Optional.of(messages.get(
                "validation.email.domainTypo",
                domain,
                email.substring(0, at + 1) + suggestion.get()
            ));
        }

        if (properties.isDnsCheckEnabled() && !lookup.exists(domain)) {
            return Optional.of(messages.get("validation.email.domainNotFound", domain));
        }
        return Optional.empty();
    }

    Optional<String> findTypoSuggestion(String domain) {
        if (properties.getKnownProviders().contains(domain)) {
            return Optional.empty();
        }
        return properties.getKnownProviders()
            .stream()
            .filter(provider -> editDistance(domain, provider) <= properties.getMaxTypoDistance())
            .findFirst();
    }

    // Distancia de Damerau-Levenshtein (versión OSA): una letra de más, de
    // menos, cambiada o dos letras invertidas cuentan como un solo error.
    static int editDistance(String first, String second) {
        int[][] distance = new int[first.length() + 1][second.length() + 1];
        for (int i = 0; i <= first.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= second.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                    Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                    distance[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1
                    && first.charAt(i - 1) == second.charAt(j - 2)
                    && first.charAt(i - 2) == second.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[first.length()][second.length()];
    }
}
