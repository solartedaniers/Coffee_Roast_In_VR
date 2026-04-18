package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Mail mail = new Mail();
    private final Verification verification = new Verification();

    public Cors getCors() {
        return cors;
    }

    public Mail getMail() {
        return mail;
    }

    public Verification getVerification() {
        return verification;
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:3000";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Mail {
        private boolean enabled;
        private String from = "no-reply@toastedvr.local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    public static class Verification {
        private int codeLength = 6;
        private int codeExpirationMinutes = 15;

        public int getCodeLength() {
            return codeLength;
        }

        public void setCodeLength(int codeLength) {
            this.codeLength = codeLength;
        }

        public int getCodeExpirationMinutes() {
            return codeExpirationMinutes;
        }

        public void setCodeExpirationMinutes(int codeExpirationMinutes) {
            this.codeExpirationMinutes = codeExpirationMinutes;
        }
    }
}
