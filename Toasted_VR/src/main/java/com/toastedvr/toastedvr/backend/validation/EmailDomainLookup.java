package com.toastedvr.toastedvr.backend.validation;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import com.toastedvr.toastedvr.backend.config.EmailDomainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Consulta DNS con el cliente que trae Java (JNDI), sin dependencias nuevas.
@Component
public class EmailDomainLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDomainLookup.class);
    // Se consultan de a uno: pedirlos juntos falla con ServiceUnavailable en
    // dominios sin AAAA (outlook.com, hotmail.com).
    private static final String[] MAIL_RECORDS = {"MX", "A"};

    private final EmailDomainProperties properties;

    public EmailDomainLookup(EmailDomainProperties properties) {
        this.properties = properties;
    }

    /**
     * true si el dominio tiene dónde recibir correo (MX, o A como respaldo).
     * Si el DNS no responde se devuelve true: una caída de red no debe impedir
     * registrarse, y el código de verificación igual confirma el correo.
     */
    public boolean exists(String domain) {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", String.valueOf(properties.getDnsTimeoutMillis()));
        environment.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
            for (String recordType : MAIL_RECORDS) {
                // El punto final evita que se le agregue el dominio de búsqueda local.
                Attributes records = context.getAttributes(domain + ".", new String[] {recordType});
                if (records.size() > 0) {
                    return true;
                }
            }
            return false;
        } catch (NameNotFoundException exception) {
            return false;
        } catch (NamingException exception) {
            LOGGER.warn("DNS lookup failed domain={} cause={}", domain, exception.getClass().getSimpleName());
            return true;
        } finally {
            closeQuietly(context);
        }
    }

    private void closeQuietly(DirContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (NamingException exception) {
            LOGGER.debug("Could not close DNS context", exception);
        }
    }
}
