package io.github.smiskinext.meetingmanagement.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LiveKit connection properties loaded from environment variables or application.yaml.
 *
 * <p>Properties are resolved at startup. Changes require service restart (use K8s rolling restart
 * for zero-downtime updates).
 *
 * <p>Default values ensure the service starts safely in local development.
 */
@Component
@ConfigurationProperties(prefix = "app.livekit")
public class LiveKitProperties {

    /**
     * LiveKit server HTTP URL. Default: {@code http://localhost:7880}.
     */
    private String url = "http://localhost:7880";

    /**
     * LiveKit server WebSocket URL used by browser clients.
     */
    private String wsUrl = "ws://localhost:7880";

    /**
     * LiveKit API key.
     */
    private String apiKey;

    /**
     * LiveKit API secret.
     */
    private String apiSecret;

    /**
     * Public webhook URL that LiveKit should call for server-side events.
     */
    private String webhookUrl = "http://localhost:8080/webhook/livekit";

    /**
     * JWT token TTL in seconds. Default: 1800 (30 minutes).
     */
    private long tokenExpirySeconds = 1800;

    private Recording recording = new Recording();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public long getTokenExpirySeconds() {
        return tokenExpirySeconds;
    }

    public void setTokenExpirySeconds(long tokenExpirySeconds) {
        this.tokenExpirySeconds = tokenExpirySeconds;
    }

    public Recording getRecording() {
        return recording;
    }

    public void setRecording(Recording recording) {
        this.recording = recording;
    }

    public static class Recording {

        private String layout = "speaker";
        private String bucket = "recordings";
        private String region = "us-east-1";
        private String endpoint = "http://localhost:9000";
        private String egressEndpoint = "http://rustfs:9000";
        private String accessKey = "";
        private String secretKey = "";
        private boolean forcePathStyle = true;
        private Duration pendingMaxAge = Duration.ofMinutes(7);

        public String getLayout() {
            return layout;
        }

        public void setLayout(String layout) {
            this.layout = layout;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEgressEndpoint() {
            return egressEndpoint;
        }

        public void setEgressEndpoint(String egressEndpoint) {
            this.egressEndpoint = egressEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isForcePathStyle() {
            return forcePathStyle;
        }

        public void setForcePathStyle(boolean forcePathStyle) {
            this.forcePathStyle = forcePathStyle;
        }

        public Duration getPendingMaxAge() {
            return pendingMaxAge;
        }

        public void setPendingMaxAge(Duration pendingMaxAge) {
            this.pendingMaxAge = pendingMaxAge;
        }
    }
}
