package io.github.smiskinext.usermanagement.presentation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import io.github.smiskinext.shared.domain.Result;
import io.github.smiskinext.usermanagement.config.TestcontainersConfiguration;
import io.github.smiskinext.usermanagement.domain.AuthError;
import io.github.smiskinext.usermanagement.domain.model.valueobject.GoogleAuthClaims;
import io.github.smiskinext.usermanagement.infrastructure.security.FirebaseTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GoogleAuthAndPreferencesIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @MockitoBean
    FirebaseTokenVerifier firebaseTokenVerifier;

    ObjectMapper objectMapper = new ObjectMapper();

    MockMvc mockMvc;

    private static final String VALID_ID_TOKEN = "valid.firebase.token";
    private static final String INVALID_ID_TOKEN = "invalid.token";
    private static final GoogleAuthClaims CLAIMS =
            new GoogleAuthClaims("uid-123", "google@example.com", "Google User", null);

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void googleLogin_newUser_returns200WithTokens() throws Exception {
        when(firebaseTokenVerifier.verify(VALID_ID_TOKEN)).thenReturn(Result.success(CLAIMS));

        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + VALID_ID_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.preferences").exists());
    }

    @Test
    void googleLogin_existingUser_returns200() throws Exception {
        when(firebaseTokenVerifier.verify(VALID_ID_TOKEN)).thenReturn(Result.success(CLAIMS));

        // First login creates the user
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + VALID_ID_TOKEN + "\"}"))
                .andExpect(status().isOk());

        // Second login returns the same user
        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + VALID_ID_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void googleLogin_invalidToken_returns401() throws Exception {
        when(firebaseTokenVerifier.verify(INVALID_ID_TOKEN))
                .thenReturn(Result.failure(new AuthError.InvalidFirebaseToken()));

        mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + INVALID_ID_TOKEN + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data.code").value("INVALID_FIREBASE_TOKEN"));
    }

    @Test
    void preferences_getAndUpdate_happyPath() throws Exception {
        when(firebaseTokenVerifier.verify(anyString())).thenReturn(Result.success(CLAIMS));

        // Login to get access token
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + VALID_ID_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();

        // GET preferences — should return empty settings for new user
        mockMvc.perform(get("/api/v1/me/preferences")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings").isMap());

        // PUT preferences with arbitrary keys
        mockMvc.perform(put("/api/v1/me/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\",\"fontSize\":16,\"lang\":\"vi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.theme").value("dark"))
                .andExpect(jsonPath("$.data.settings.fontSize").value(16))
                .andExpect(jsonPath("$.data.settings.lang").value("vi"));

        // GET again — should return updated prefs
        mockMvc.perform(get("/api/v1/me/preferences")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.theme").value("dark"));
    }

    @Test
    void preferences_anyJsonKeysAccepted() throws Exception {
        when(firebaseTokenVerifier.verify(anyString())).thenReturn(Result.success(CLAIMS));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + VALID_ID_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();

        // Any JSON keys should be accepted without validation errors
        mockMvc.perform(put("/api/v1/me/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customKey\":\"customValue\",\"anotherKey\":42,\"flag\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.customKey").value("customValue"))
                .andExpect(jsonPath("$.data.settings.anotherKey").value(42))
                .andExpect(jsonPath("$.data.settings.flag").value(true));
    }

    @Test
    void preferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences")).andExpect(status().isUnauthorized());
    }
}
