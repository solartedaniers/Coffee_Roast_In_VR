package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// Las entidades guardan LocalDateTime en la zona de la JVM (UTC). La API las
// expone como Instant para que el JSON lleve la "Z" y el navegador las
// convierta a la hora local del usuario.
public final class ApiInstants {

    private ApiInstants() {
    }

    public static Instant from(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
