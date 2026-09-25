package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;

// Nombre en español de un resultado para los mensajes de rechazo; los
// nombres internos (RAW, PERFECT...) nunca llegan al jugador.
final class RoastResultLabel {

    private RoastResultLabel() {
    }

    static String of(RoastingResult result, MessageResolver messages) {
        return messages.get("roasting.result." + result.name());
    }
}
