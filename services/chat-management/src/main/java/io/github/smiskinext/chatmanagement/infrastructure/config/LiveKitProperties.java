package io.github.smiskinext.chatmanagement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LiveKit connection properties for chat-management. */
@Component
@ConfigurationProperties(prefix = "app.livekit")
public class LiveKitProperties {

    private String url = "http://localhost:7880";
    private String apiKey;
    private String apiSecret;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
}
