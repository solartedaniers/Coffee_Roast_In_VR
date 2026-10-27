package com.toastedvr.toastedvr.backend.validation;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

// Única regla de formato de correo. Más estricta que @Email de Jakarta: exige
// un dominio con al menos un punto y una extensión de 2 o más letras, para no
// aceptar correos como "usuario@gmailcom" que después rebotan.
@Component
public class EmailPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$"
    );

    public boolean isSatisfiedBy(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
