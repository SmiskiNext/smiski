package io.github.smiskinext.meetingmanagement.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "participation_logs")
public class ParticipationLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false, columnDefinition = "uuid")
    private UUID meetingId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private @Nullable UUID userId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "livekit_identity", nullable = false, length = 255)
    private String livekitIdentity;

    @Column(name = "livekit_participant_sid", length = 50)
    private @Nullable String livekitParticipantSid;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private @Nullable Instant leftAt;

    protected ParticipationLogJpaEntity() {}

    public ParticipationLogJpaEntity(
            UUID meetingId,
            @Nullable UUID userId,
            String displayName,
            String role,
            String livekitIdentity,
            @Nullable String livekitParticipantSid,
            Instant joinedAt,
            @Nullable Instant leftAt) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.livekitIdentity = livekitIdentity;
        this.livekitParticipantSid = livekitParticipantSid;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getMeetingId() {
        return meetingId;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public String getLivekitIdentity() {
        return livekitIdentity;
    }

    public @Nullable String getLivekitParticipantSid() {
        return livekitParticipantSid;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public @Nullable Instant getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(Instant leftAt) {
        this.leftAt = leftAt;
    }

    public void setLivekitParticipantSid(String sid) {
        this.livekitParticipantSid = sid;
    }
}
