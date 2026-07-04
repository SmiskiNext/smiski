package io.github.smiskinext.usermanagement;

import io.github.smiskinext.usermanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.usermanagement.infrastructure.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserManagementApplicationTests {

    @MockitoBean
    FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void contextLoads() {}
}
