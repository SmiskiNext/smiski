package io.github.smiskinext.chatmanagement.infrastructure.config;

import io.livekit.server.RoomServiceClient;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the LiveKit server SDK client as a Spring bean. */
@Configuration
public class LiveKitConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(30);

    private static OkHttpClient liveKitHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .writeTimeout(WRITE_TIMEOUT)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Bean
    public RoomServiceClient roomServiceClient(LiveKitProperties props) {
        return RoomServiceClient.createClient(
                props.getUrl(),
                props.getApiKey(),
                props.getApiSecret(),
                LiveKitConfig::liveKitHttpClient);
    }
}
