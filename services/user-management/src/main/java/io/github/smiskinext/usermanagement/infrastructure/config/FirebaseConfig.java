package io.github.smiskinext.usermanagement.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.FileInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initialises Firebase Admin SDK beans.
 * Only active when {@code app.firebase.project-id} is set to a non-empty value.
 * In tests, mock {@link FirebaseTokenVerifier} directly via {@code @MockBean}.
 */
@Configuration
@ConditionalOnExpression("!'${app.firebase.project-id:}'.isBlank()")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${app.firebase.project-id}")
    private String projectId;

    @Value("${app.firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials;
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.info("Initialising Firebase with service account file: {}", credentialsPath);
            try (var stream = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(stream);
            }
        } else {
            log.info("Initialising Firebase with Application Default Credentials");
            credentials = GoogleCredentials.getApplicationDefault();
        }

        var options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();

        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
