package io.github.smiskinext.meetingmanagement.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.util.JsonFormat;
import io.github.smiskinext.meetingmanagement.application.command.ActivateRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.command.FinalizeRecordingCommand;
import io.github.smiskinext.meetingmanagement.application.usecase.ActivateRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.AssignSidUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.CloseStaleMeetingLogsUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.FinalizeRecordingUseCase;
import io.github.smiskinext.meetingmanagement.application.usecase.LeaveMeetingUseCase;
import io.github.smiskinext.meetingmanagement.domain.port.EventPublisher;
import io.github.smiskinext.meetingmanagement.domain.port.ParticipationLogRepository;
import io.github.smiskinext.meetingmanagement.infrastructure.config.LiveKitProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import livekit.LivekitEgress;
import livekit.LivekitWebhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

@ExtendWith(MockitoExtension.class)
class LiveKitWebhookControllerTest {

    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret-which-is-long-enough-for-hs256";
    private static final String EGRESS_ID = "EG_WEBHOOK_123";

    @Mock
    AssignSidUseCase assignSidUseCase;

    @Mock
    LeaveMeetingUseCase leaveMeetingUseCase;

    @Mock
    CloseStaleMeetingLogsUseCase closeStaleMeetingLogsUseCase;

    @Mock
    ActivateRecordingUseCase activateRecordingUseCase;

    @Mock
    FinalizeRecordingUseCase finalizeRecordingUseCase;

    @Mock
    EventPublisher eventPublisher;

    @Mock
    ParticipationLogRepository participationLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setApiKey(API_KEY);
        properties.setApiSecret(API_SECRET);
        var versionStrategy = new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(0)),
                new SemanticApiVersionParser(),
                null,
                "1.0",
                true,
                null,
                null);

        mockMvc = MockMvcBuilders.standaloneSetup(new LiveKitWebhookController(
                        properties,
                        assignSidUseCase,
                        leaveMeetingUseCase,
                        closeStaleMeetingLogsUseCase,
                        activateRecordingUseCase,
                        finalizeRecordingUseCase,
                        eventPublisher,
                        participationLogRepository))
                .setApiVersionStrategy(versionStrategy)
                .build();
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() throws Exception {
        String rawBody = rawJson(egressStartedEvent());

        mockMvc.perform(MockMvcRequestBuilders.post("/1.0/webhook/livekit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "invalid-token")
                        .content(rawBody))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());

        verifyNoInteractions(activateRecordingUseCase, finalizeRecordingUseCase);
    }

    @Test
    void handleWebhook_egressStartedDelegatesAfterSignatureVerification() throws Exception {
        String rawBody = rawJson(egressStartedEvent());

        mockMvc.perform(MockMvcRequestBuilders.post("/1.0/webhook/livekit")
                        .contentType(MediaType.valueOf("application/webhook+json"))
                        .header("Authorization", signedWebhookToken(rawBody))
                        .content(rawBody))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());

        ArgumentCaptor<ActivateRecordingCommand> commandCaptor =
                ArgumentCaptor.forClass(ActivateRecordingCommand.class);
        verify(activateRecordingUseCase).execute(commandCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(commandCaptor.getValue().livekitEgressId())
                .isEqualTo(EGRESS_ID);
    }

    @Test
    void handleWebhook_egressEndedSuccessMapsFileOutput() throws Exception {
        String rawBody = rawJson(egressEndedSuccessEvent());

        mockMvc.perform(MockMvcRequestBuilders.post("/1.0/webhook/livekit")
                        .contentType(MediaType.valueOf("application/webhook+json"))
                        .header("Authorization", signedWebhookToken(rawBody))
                        .content(rawBody))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());

        ArgumentCaptor<FinalizeRecordingCommand> commandCaptor =
                ArgumentCaptor.forClass(FinalizeRecordingCommand.class);
        verify(finalizeRecordingUseCase).execute(commandCaptor.capture());
        var command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.successful()).isTrue();
        org.assertj.core.api.Assertions.assertThat(command.fileUrl())
                .isEqualTo("s3://recordings/meeting.mp4");
        org.assertj.core.api.Assertions.assertThat(command.storagePath())
                .isEqualTo("meetings/abc/egress.mp4");
        org.assertj.core.api.Assertions.assertThat(command.durationSeconds()).isEqualTo(12);
        org.assertj.core.api.Assertions.assertThat(command.fileSizeBytes()).isEqualTo(4096L);
    }

    @Test
    void handleWebhook_egressEndedFailureMapsErrorPayload() throws Exception {
        String rawBody = rawJson(egressEndedFailureEvent());

        mockMvc.perform(MockMvcRequestBuilders.post("/1.0/webhook/livekit")
                        .contentType(MediaType.valueOf("application/webhook+json"))
                        .header("Authorization", signedWebhookToken(rawBody))
                        .content(rawBody))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());

        ArgumentCaptor<FinalizeRecordingCommand> commandCaptor =
                ArgumentCaptor.forClass(FinalizeRecordingCommand.class);
        verify(finalizeRecordingUseCase).execute(commandCaptor.capture());
        var command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.successful()).isFalse();
        org.assertj.core.api.Assertions.assertThat(command.errorMessage())
                .isEqualTo("egress crashed");
    }

    private static LivekitWebhook.WebhookEvent egressStartedEvent() {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setEvent("egress_started")
                .setEgressInfo(LivekitEgress.EgressInfo.newBuilder()
                        .setEgressId(EGRESS_ID)
                        .build())
                .build();
    }

    private static LivekitWebhook.WebhookEvent egressEndedSuccessEvent() {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setEvent("egress_ended")
                .setEgressInfo(LivekitEgress.EgressInfo.newBuilder()
                        .setEgressId(EGRESS_ID)
                        .addFileResults(LivekitEgress.FileInfo.newBuilder()
                                .setLocation("s3://recordings/meeting.mp4")
                                .setFilename("meetings/abc/egress.mp4")
                                .setDuration(12_000_000_000L)
                                .setSize(4096L)
                                .build())
                        .build())
                .build();
    }

    private static LivekitWebhook.WebhookEvent egressEndedFailureEvent() {
        return LivekitWebhook.WebhookEvent.newBuilder()
                .setEvent("egress_ended")
                .setEgressInfo(LivekitEgress.EgressInfo.newBuilder()
                        .setEgressId(EGRESS_ID)
                        .setError("egress crashed")
                        .build())
                .build();
    }

    private static String rawJson(LivekitWebhook.WebhookEvent event) throws Exception {
        return JsonFormat.printer().omittingInsignificantWhitespace().print(event);
    }

    private static String signedWebhookToken(String rawBody) throws Exception {
        String sha256 = java.util.Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(rawBody.getBytes(StandardCharsets.UTF_8)));

        return Jwts.builder()
                .issuer(API_KEY)
                .claim("sha256", sha256)
                .expiration(java.util.Date.from(Instant.now().plusSeconds(60)))
                .signWith(
                        Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }
}
