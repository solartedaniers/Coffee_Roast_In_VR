package com.toastedvr.toastedvr.backend.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

// Punto único para leer textos de messages.properties. La aplicación solo
// habla español, así que el locale es fijo.
@Component
public class MessageResolver {

    private static final Locale LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LOCALE);
    }
}
