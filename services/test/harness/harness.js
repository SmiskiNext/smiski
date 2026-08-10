// Samples WebRTC transport statistics for TC-01 and TC-02.
//
// Reads getStats() from the peer connections the LiveKit client owns, once per
// second, and accumulates rows that the export writes verbatim. The fields are
// the ones the test plan names: inbound jitter, packetsLost and framesDropped,
// bitrate in both directions, and the selected candidate pair's round-trip time
// and candidate type.
//
// Two properties of this file are load bearing:
//
//   - Bitrate is DERIVED, not read. No RTCStats member reports a rate; both
//     bytesReceived and bytesSent are monotonic totals, so each sample divides
//     the delta by the elapsed time since the previous sample. Reporting the
//     raw counter would put a cumulative byte count where a rate belongs.
//
//   - Packet loss is reported as a percentage of expected packets, computed from
//     the deltas of packetsLost and packetsReceived. The cumulative counter
//     alone cannot be compared against a "< 2%" threshold.

(() => {
    'use strict';

    const SAMPLE_INTERVAL_MS = 1000;

    const {
        Room,
        RoomEvent,
        Track,
        ConnectionState,
    } = window.LivekitClient;

    const elements = {
        serverUrl: document.getElementById('serverUrl'),
        stunUrl: document.getElementById('stunUrl'),
        token: document.getElementById('token'),
        relayOnly: document.getElementById('relayOnly'),
        publishMedia: document.getElementById('publishMedia'),
        connect: document.getElementById('connect'),
        disconnect: document.getElementById('disconnect'),
        screenShare: document.getElementById('screenShare'),
        export: document.getElementById('export'),
        status: document.getElementById('status'),
        setupTime: document.getElementById('setupTime'),
        candidateType: document.getElementById('candidateType'),
        candidateMeaning: document.getElementById('candidateMeaning'),
        rtt: document.getElementById('rtt'),
        jitter: document.getElementById('jitter'),
        packetLoss: document.getElementById('packetLoss'),
        framesDropped: document.getElementById('framesDropped'),
        bitrate: document.getElementById('bitrate'),
        sampleCount: document.getElementById('sampleCount'),
        videoGrid: document.getElementById('videoGrid'),
        log: document.getElementById('log'),
    };

    const session = {
        room: null,
        samples: [],
        screenShareEvents: [],
        sampleTimer: null,
        connectStartedAt: null,
        setupTimeMs: null,
        relayRequested: false,
        previous: null,
    };

    function log(message) {
        const line = `${new Date().toISOString()}  ${message}`;
        elements.log.textContent = `${line}\n${elements.log.textContent}`;
    }

    function setStatus(message, kind) {
        elements.status.textContent = message;
        elements.status.className = kind ? `status ${kind}` : 'status';
    }

    function formatNumber(value, digits) {
        return typeof value === 'number' && Number.isFinite(value)
            ? value.toFixed(digits)
            : '';
    }

    /**
     * Reads the peer connections the client is currently using.
     *
     * Both transports are read, and both may be absent. LiveKit negotiates one
     * of three modes and only creates the transports that mode needs: verified
     * against livekit-client 2.20.1, a publishing client with no subscriptions
     * runs in `publisher-only` with `subscriber` left null, so indexing it
     * unconditionally yields nothing and every inbound field exports blank.
     *
     * The private `_pc` field is read as a fallback because `pc` is a getter that
     * throws once the transport is closed, while `_pc` is the backing field the
     * library itself keeps. Preferring the getter keeps this working if the
     * private name changes.
     */
    function collectPeerConnections() {
        const manager = session.room?.engine?.pcManager;
        if (!manager) {
            return [];
        }

        const connections = [];
        for (const transport of [manager.publisher, manager.subscriber]) {
            if (!transport) {
                continue;
            }

            let connection = null;
            try {
                connection = transport.pc ?? transport._pc ?? null;
            } catch {
                connection = transport._pc ?? null;
            }

            if (connection && typeof connection.getStats === 'function') {
                connections.push(connection);
            }
        }

        return connections;
    }

    /**
     * Reduces one getStats() report into the fields the export needs.
     *
     * The selected candidate pair is found by `selected` or by state `succeeded`,
     * because Chromium and Firefox disagree about which is authoritative. The
     * local candidate's type is what proves relay usage — the remote type is
     * recorded alongside it because a relayed local candidate paired with a host
     * remote candidate is still a relayed path.
     */
    function reduceReport(report) {
        const totals = {
            jitterSeconds: null,
            packetsLost: 0,
            packetsReceived: 0,
            framesDropped: 0,
            bytesReceived: 0,
            bytesSent: 0,
            roundTripSeconds: null,
            localCandidateType: '',
            remoteCandidateType: '',
        };

        const byId = new Map();
        report.forEach((entry) => byId.set(entry.id, entry));

        let jitterSum = 0;
        let jitterCount = 0;

        report.forEach((entry) => {
            if (entry.type === 'inbound-rtp') {
                if (typeof entry.jitter === 'number') {
                    jitterSum += entry.jitter;
                    jitterCount += 1;
                }
                totals.packetsLost += entry.packetsLost ?? 0;
                totals.packetsReceived += entry.packetsReceived ?? 0;
                totals.framesDropped += entry.framesDropped ?? 0;
                totals.bytesReceived += entry.bytesReceived ?? 0;
                return;
            }

            if (entry.type === 'outbound-rtp') {
                totals.bytesSent += entry.bytesSent ?? 0;
                return;
            }

            if (entry.type !== 'candidate-pair') {
                return;
            }

            const isSelected =
                entry.selected === true || entry.state === 'succeeded';
            if (!isSelected) {
                return;
            }

            if (typeof entry.currentRoundTripTime === 'number') {
                totals.roundTripSeconds = entry.currentRoundTripTime;
            }

            const local = byId.get(entry.localCandidateId);
            const remote = byId.get(entry.remoteCandidateId);

            if (local?.candidateType) {
                totals.localCandidateType = local.candidateType;
            }
            if (remote?.candidateType) {
                totals.remoteCandidateType = remote.candidateType;
            }
        });

        if (jitterCount > 0) {
            totals.jitterSeconds = jitterSum / jitterCount;
        }

        return totals;
    }

    function mergeTotals(first, second) {
        return {
            jitterSeconds:
                first.jitterSeconds ?? second.jitterSeconds ?? null,
            packetsLost: first.packetsLost + second.packetsLost,
            packetsReceived: first.packetsReceived + second.packetsReceived,
            framesDropped: first.framesDropped + second.framesDropped,
            bytesReceived: first.bytesReceived + second.bytesReceived,
            bytesSent: first.bytesSent + second.bytesSent,
            roundTripSeconds:
                first.roundTripSeconds ?? second.roundTripSeconds ?? null,
            localCandidateType:
                first.localCandidateType || second.localCandidateType,
            remoteCandidateType:
                first.remoteCandidateType || second.remoteCandidateType,
        };
    }

    async function takeSample() {
        const connections = collectPeerConnections();
        if (connections.length === 0) {
            return;
        }

        const reports = await Promise.all(
            connections.map((connection) =>
                connection.getStats().catch(() => null),
            ),
        );

        let totals = null;
        for (const report of reports) {
            if (report === null) {
                continue;
            }
            const reduced = reduceReport(report);
            totals = totals === null ? reduced : mergeTotals(totals, reduced);
        }

        if (totals === null) {
            return;
        }

        const now = Date.now();
        const previous = session.previous;
        const elapsedSeconds =
            previous === null ? null : (now - previous.at) / 1000;

        const downlinkBitsPerSecond =
            elapsedSeconds && elapsedSeconds > 0
                ? ((totals.bytesReceived - previous.bytesReceived) * 8)
                  / elapsedSeconds
                : null;
        const uplinkBitsPerSecond =
            elapsedSeconds && elapsedSeconds > 0
                ? ((totals.bytesSent - previous.bytesSent) * 8) / elapsedSeconds
                : null;

        const lostDelta =
            previous === null ? 0 : totals.packetsLost - previous.packetsLost;
        const receivedDelta =
            previous === null
                ? 0
                : totals.packetsReceived - previous.packetsReceived;
        const expectedDelta = lostDelta + receivedDelta;
        const packetLossPercent =
            expectedDelta > 0 ? (lostDelta / expectedDelta) * 100 : null;

        const sample = {
            timestamp: new Date(now).toISOString(),
            elapsedSeconds:
                session.connectStartedAt === null
                    ? null
                    : (now - session.connectStartedAt) / 1000,
            roundTripMs:
                totals.roundTripSeconds === null
                    ? null
                    : totals.roundTripSeconds * 1000,
            jitterMs:
                totals.jitterSeconds === null
                    ? null
                    : totals.jitterSeconds * 1000,
            packetLossPercent,
            packetsLostCumulative: totals.packetsLost,
            framesDropped: totals.framesDropped,
            downlinkKbps:
                downlinkBitsPerSecond === null
                    ? null
                    : downlinkBitsPerSecond / 1000,
            uplinkKbps:
                uplinkBitsPerSecond === null
                    ? null
                    : uplinkBitsPerSecond / 1000,
            localCandidateType: totals.localCandidateType,
            remoteCandidateType: totals.remoteCandidateType,
        };

        session.samples.push(sample);
        session.previous = {
            at: now,
            bytesReceived: totals.bytesReceived,
            bytesSent: totals.bytesSent,
            packetsLost: totals.packetsLost,
            packetsReceived: totals.packetsReceived,
        };

        renderSample(sample);
    }

    /**
     * Updates the status display and enables the export button once samples exist.
     *
     * The export button is enabled here rather than in `setConnectedUi` because the
     * latter runs before the first sample is taken. A check against sample count at
     * that earlier point would leave the button disabled for the whole session and
     * the export unreachable.
     *
     * The relay assertion is made against the LOCAL candidate type, which is what
     * "this client sent its media through the TURN server" means. A relay-only run
     * whose candidate type reads anything else is a relay failure and must not be
     * recorded as a pass.
     */
    function renderSample(sample) {
        elements.rtt.textContent =
            sample.roundTripMs === null
                ? '—'
                : `${formatNumber(sample.roundTripMs, 1)} ms`;
        elements.jitter.textContent =
            sample.jitterMs === null
                ? '—'
                : `${formatNumber(sample.jitterMs, 2)} ms`;
        elements.packetLoss.textContent =
            sample.packetLossPercent === null
                ? '—'
                : `${formatNumber(sample.packetLossPercent, 2)} %`;
        elements.framesDropped.textContent = String(sample.framesDropped);
        elements.bitrate.textContent =
            sample.downlinkKbps === null
                ? '—'
                : `${formatNumber(sample.downlinkKbps, 0)} / `
                  + `${formatNumber(sample.uplinkKbps, 0)} kbps`;
        elements.sampleCount.textContent = String(session.samples.length);

        elements.export.disabled = session.samples.length === 0;

        const local = sample.localCandidateType || '—';
        const remote = sample.remoteCandidateType || '—';
        elements.candidateType.textContent = `${local} / ${remote}`;
        elements.candidateMeaning.textContent = interpretCandidate(
            sample.localCandidateType,
        );

        if (sample.localCandidateType === '') {
            elements.candidateType.className = '';
            return;
        }

        const isRelay = sample.localCandidateType === 'relay';
        elements.candidateType.className = isRelay ? 'relay' : 'not-relay';

        if (session.relayRequested && !isRelay) {
            setStatus(
                `Relay was requested but the negotiated candidate type is `
                    + `"${sample.localCandidateType}". This run is NOT relay evidence.`,
                'error',
            );
        }
    }

    /**
     * Names what a local candidate type proves for TC-01.
     *
     * The two legs of TC-01 are read from this one field. `srflx` and `prflx`
     * are both a post-NAT address the media path reached without a relay, so
     * either proves Leg 1's direct traversal; `relay` proves Leg 2's fallback.
     * `host` means no NAT sat in the path — the run belongs to neither leg and
     * is almost always a host-browser session pointed at the wrong URL.
     */
    function interpretCandidate(localCandidateType) {
        switch (localCandidateType) {
            case 'srflx':
            case 'prflx':
                return 'Leg 1: NAT traversed directly, no relay';
            case 'relay':
                return 'Leg 2: relayed through TURN';
            case 'host':
                return 'No NAT in path — neither leg (wrong client or URL?)';
            default:
                return '—';
        }
    }

    function attachTrack(track, participantIdentity) {
        if (
            track.kind !== Track.Kind.Video
            && track.kind !== Track.Kind.Audio
        ) {
            return;
        }

        const element = track.attach();
        element.dataset.participant = participantIdentity;

        if (track.kind === Track.Kind.Video) {
            element.autoplay = true;
            element.playsInline = true;
            elements.videoGrid.appendChild(element);
            return;
        }

        element.autoplay = true;
        document.body.appendChild(element);
    }

    function registerRoomEvents(room) {
        room.on(RoomEvent.TrackSubscribed, (track, _publication, participant) => {
            log(`track subscribed: ${track.kind} from ${participant.identity}`);
            attachTrack(track, participant.identity);
        });

        room.on(RoomEvent.TrackUnsubscribed, (track) => {
            track.detach().forEach((element) => element.remove());
        });

        room.on(RoomEvent.LocalTrackPublished, (publication) => {
            log(`local track published: ${publication.source}`);
            if (publication.track) {
                attachTrack(publication.track, 'local');
            }
        });

        room.on(RoomEvent.ParticipantConnected, (participant) => {
            log(`participant connected: ${participant.identity}`);
        });

        room.on(RoomEvent.ParticipantDisconnected, (participant) => {
            log(`participant disconnected: ${participant.identity}`);
        });

        room.on(RoomEvent.ConnectionStateChanged, (state) => {
            log(`connection state: ${state}`);
            if (state === ConnectionState.Reconnecting) {
                setStatus('Reconnecting — recorded as a session interruption.', 'error');
            }
        });

        room.on(RoomEvent.Disconnected, (reason) => {
            log(`disconnected: ${reason ?? 'no reason given'}`);
            stopSampling();
            setConnectedUi(false);
        });
    }

    function startSampling() {
        stopSampling();
        session.sampleTimer = window.setInterval(() => {
            takeSample().catch((error) => log(`sample failed: ${error}`));
        }, SAMPLE_INTERVAL_MS);
    }

    function stopSampling() {
        if (session.sampleTimer !== null) {
            window.clearInterval(session.sampleTimer);
            session.sampleTimer = null;
        }
    }

    function setConnectedUi(connected) {
        elements.connect.disabled = connected;
        elements.disconnect.disabled = !connected;
        elements.screenShare.disabled = !connected;
        elements.export.disabled = session.samples.length === 0;
        elements.relayOnly.disabled = connected;
        elements.publishMedia.disabled = connected;
    }

    /**
     * Names the reason camera and microphone capture is unavailable, if it is.
     *
     * Browsers expose `navigator.mediaDevices` only in a secure context, and this
     * page is served over plain HTTP. `http://localhost` is treated as secure by
     * every current browser, but a LAN address or a container hostname is not —
     * so opening the page at http://<host-ip>:8090 silently loses the ability to
     * publish, and the only symptom is an uplink bitrate of zero.
     *
     * Returning the reason rather than throwing keeps the session usable:
     * subscribing and every inbound statistic still work, which is enough for a
     * two-party call where the other leg publishes.
     */
    function describeMediaCaptureBlocker() {
        if (navigator.mediaDevices !== undefined) {
            return null;
        }

        if (!window.isSecureContext) {
            return (
                `this page is not a secure context (origin ${location.origin}), `
                + 'so the browser withholds navigator.mediaDevices. Open it at '
                + 'http://localhost:8090 instead of a LAN address, or serve it over '
                + 'HTTPS.'
            );
        }

        return 'navigator.mediaDevices is unavailable in this browser.';
    }

    /**
     * Assembles the connect options from the relay toggle and the STUN field.
     *
     * The STUN server is added ADDITIVELY: livekit-client merges its own
     * server-supplied ICE servers with any supplied here, so this hands ICE an
     * extra reflexive path for Leg 1 without displacing the TURN servers LiveKit
     * provides — which Leg 2 needs when it falls back to a relay. Verified
     * against livekit-client 2.20.1, whose engine concatenates the two lists.
     *
     * When relay-only is requested, no STUN server is added and the transport
     * policy is pinned to relay: that control run deliberately excludes every
     * non-relay candidate, so offering a STUN server would only add noise.
     */
    function buildConnectOptions() {
        if (session.relayRequested) {
            return { rtcConfig: { iceTransportPolicy: 'relay' } };
        }

        const stunUrl = elements.stunUrl.value.trim();
        if (stunUrl === '') {
            return {};
        }

        return { rtcConfig: { iceServers: [{ urls: stunUrl }] } };
    }

    /**
     * Builds the `Room`, applies the relay-only policy and starts sampling.
     *
     * `adaptiveStream` is deliberately OFF, and it is the difference between a
     * populated export and an empty one. With it enabled, the client subscribes
     * only to tracks whose video element it considers visible, so a minimised
     * window, a background tab or an unsized element leaves `isSubscribed` false
     * — no inbound-rtp is produced, and jitter, packet loss and downlink bitrate
     * all export as blank while the connection looks perfectly healthy. Verified
     * against livekit-client 2.20.1.
     *
     * `dynacast` is off for the same class of reason: it lets the server stop
     * sending layers nobody is consuming, which makes the uplink bitrate a
     * function of who is watching rather than of what this client publishes.
     *
     * The `Room` and the collected samples are exposed on `window` deliberately.
     * This is a measurement harness, and an operator investigating an unexpected
     * reading needs them from the browser console without rebuilding the page.
     *
     * `rtcConfig` is a pass-through to the native RTCConfiguration, so it is the
     * entire relay-only mechanism. It must be supplied at connect time: the ICE
     * transport policy is fixed once the peer connections exist.
     */
    async function connect() {
        const serverUrl = elements.serverUrl.value.trim();
        const token = elements.token.value.trim();

        if (serverUrl === '' || token === '') {
            setStatus(
                'Both the WebSocket URL and a token are required.',
                'error',
            );
            return;
        }

        session.samples = [];
        session.screenShareEvents = [];
        session.previous = null;
        session.setupTimeMs = null;
        session.relayRequested = elements.relayOnly.checked;
        elements.sampleCount.textContent = '0';
        elements.setupTime.textContent = '—';

        const room = new Room({ adaptiveStream: false, dynacast: false });
        session.room = room;

        window.smiskiHarness = session;

        registerRoomEvents(room);

        const connectOptions = buildConnectOptions();

        setStatus('Connecting…');
        session.connectStartedAt = Date.now();

        try {
            await room.connect(serverUrl, token, connectOptions);
        } catch (error) {
            setStatus(`Connection failed: ${error}`, 'error');
            log(`connect failed: ${error}`);

            if (session.relayRequested) {
                log(
                    'Relay was requested. A failure here is a RELAY failure — the '
                        + 'TURN server is unreachable or its credential does not match '
                        + 'the media server. Record it as such, not as a harness fault.',
                );
            }

            session.room = null;
            return;
        }

        session.setupTimeMs = Date.now() - session.connectStartedAt;
        elements.setupTime.textContent = `${session.setupTimeMs} ms`;

        const withinBudget = session.setupTimeMs < 3000;
        setStatus(
            `Connected in ${session.setupTimeMs} ms `
                + `(TC-01 budget 3000 ms: ${withinBudget ? 'within' : 'EXCEEDED'}).`,
            withinBudget ? 'ok' : 'error',
        );
        log(`connected to ${room.name} in ${session.setupTimeMs} ms`);

        if (elements.publishMedia.checked) {
            const mediaBlocker = describeMediaCaptureBlocker();

            if (mediaBlocker !== null) {
                log(`local media unavailable: ${mediaBlocker}`);
                setStatus(
                    `Connected in ${session.setupTimeMs} ms, but local media is `
                        + `unavailable: ${mediaBlocker} Inbound statistics still `
                        + 'sample; uplink bitrate will read zero.',
                    'error',
                );
            } else {
                try {
                    await room.localParticipant.enableCameraAndMicrophone();
                } catch (error) {
                    log(`could not publish local media: ${error}`);
                    setStatus(
                        `Connected, but local media failed: ${error}. Inbound `
                            + 'statistics still sample; uplink bitrate will read zero.',
                        'error',
                    );
                }
            }
        }

        setConnectedUi(true);
        startSampling();
        await takeSample();
    }

    async function disconnect() {
        stopSampling();

        if (session.room !== null) {
            await session.room.disconnect();
            session.room = null;
        }

        setConnectedUi(false);
        elements.export.disabled = session.samples.length === 0;
        setStatus(
            `Disconnected. ${session.samples.length} samples retained — export before reloading.`,
        );
    }

    /**
     * Toggles the local screen share and timestamps the transition.
     *
     * Each start and stop is timestamped because `lk load-test` cannot publish a
     * screen share: it hardcodes a camera track source. The recorded interval is
     * the only record that lets the manual leg of TC-03 be aligned with
     * server-side metrics.
     */
    async function toggleScreenShare() {
        if (session.room === null) {
            return;
        }

        const participant = session.room.localParticipant;
        const enabled = participant.isScreenShareEnabled;

        try {
            await participant.setScreenShareEnabled(!enabled);
        } catch (error) {
            log(`screen share toggle failed: ${error}`);
            setStatus(`Screen share failed: ${error}`, 'error');
            return;
        }

        const event = {
            action: enabled ? 'stop' : 'start',
            timestamp: new Date().toISOString(),
        };
        session.screenShareEvents.push(event);
        log(`screen share ${event.action} at ${event.timestamp}`);

        elements.screenShare.textContent = enabled
            ? 'Start screen share'
            : 'Stop screen share';
        elements.screenShare.classList.toggle('active', !enabled);
    }

    function escapeCsv(value) {
        const text = value === null || value === undefined ? '' : String(value);
        return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
    }

    /**
     * Writes the export, conditions first.
     *
     * The relay request, the negotiated candidate type, the setup time and the
     * screen-share interval are all written as leading comment rows, because a
     * sample row read without them cannot be attributed to a test case: the same
     * numbers mean different things under relay-only and under direct
     * connectivity.
     *
     * The download anchor is attached to the document before it is clicked, and
     * the object URL is revoked on a later task rather than immediately.
     * Revoking synchronously after `click()` races the download and can abort
     * it, which loses a whole session's samples with no error — the click
     * appears to succeed and no file arrives.
     */
    function exportCsv() {
        if (session.samples.length === 0) {
            return;
        }

        const observedCandidateTypes = [
            ...new Set(
                session.samples
                    .map((sample) => sample.localCandidateType)
                    .filter((type) => type !== ''),
            ),
        ];

        const relayProven =
            observedCandidateTypes.length > 0
            && observedCandidateTypes.every((type) => type === 'relay');

        const traversalProven =
            observedCandidateTypes.length > 0
            && observedCandidateTypes.every(
                (type) => type === 'srflx' || type === 'prflx',
            );

        const lines = [];
        lines.push(`# harness,browser QoS harness (services/test/harness)`);
        lines.push(`# exported at,${new Date().toISOString()}`);
        lines.push(`# server url,${escapeCsv(elements.serverUrl.value.trim())}`);
        lines.push(`# relay requested,${session.relayRequested}`);
        lines.push(
            `# candidate types observed,${escapeCsv(observedCandidateTypes.join(' ') || 'none')}`,
        );
        lines.push(`# relay proven,${relayProven}`);
        lines.push(`# nat traversal proven,${traversalProven}`);
        lines.push(
            `# call setup ms,${session.setupTimeMs === null ? '' : session.setupTimeMs}`,
        );
        lines.push(
            `# call setup within 3000 ms budget,`
                + `${session.setupTimeMs === null ? '' : session.setupTimeMs < 3000}`,
        );
        lines.push(`# sample interval ms,${SAMPLE_INTERVAL_MS}`);
        lines.push(`# sample count,${session.samples.length}`);
        lines.push(
            `# session duration seconds,`
                + `${formatNumber(session.samples[session.samples.length - 1].elapsedSeconds, 1)}`,
        );

        if (session.relayRequested && !relayProven) {
            lines.push(
                '# WARNING,relay was requested but a non-relay candidate was '
                    + 'negotiated — this export is NOT evidence of relay traversal',
            );
        }

        if (!relayProven && !traversalProven) {
            lines.push(
                '# WARNING,neither a pure relay nor a pure srflx/prflx path was '
                    + 'observed — this run maps to neither leg of TC-01. A host '
                    + 'candidate means no NAT was in the path; a mixed set means '
                    + 'the condition changed mid-run. Re-run one leg cleanly.',
            );
        }

        if (session.screenShareEvents.length === 0) {
            lines.push('# screen share,none recorded');
        } else {
            for (const event of session.screenShareEvents) {
                lines.push(`# screen share ${event.action},${event.timestamp}`);
            }
        }

        lines.push(
            '# note,latency is the selected candidate pair round-trip time; '
                + 'jitter and packet loss are inbound-rtp',
        );

        lines.push(
            [
                'timestamp',
                'elapsed_seconds',
                'round_trip_ms',
                'jitter_ms',
                'packet_loss_percent',
                'packets_lost_cumulative',
                'frames_dropped',
                'downlink_kbps',
                'uplink_kbps',
                'local_candidate_type',
                'remote_candidate_type',
            ].join(','),
        );

        for (const sample of session.samples) {
            lines.push(
                [
                    sample.timestamp,
                    formatNumber(sample.elapsedSeconds, 1),
                    formatNumber(sample.roundTripMs, 2),
                    formatNumber(sample.jitterMs, 3),
                    formatNumber(sample.packetLossPercent, 3),
                    sample.packetsLostCumulative,
                    sample.framesDropped,
                    formatNumber(sample.downlinkKbps, 1),
                    formatNumber(sample.uplinkKbps, 1),
                    sample.localCandidateType,
                    sample.remoteCandidateType,
                ]
                    .map(escapeCsv)
                    .join(','),
            );
        }

        const blob = new Blob([`${lines.join('\n')}\n`], {
            type: 'text/csv',
        });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        const suffix = session.relayRequested ? 'relay' : 'direct';

        anchor.href = url;
        anchor.download = `qos-${suffix}-${Date.now()}.csv`;

        anchor.style.display = 'none';
        document.body.appendChild(anchor);
        anchor.click();

        window.setTimeout(() => {
            anchor.remove();
            URL.revokeObjectURL(url);
        }, 10000);

        log(`exported ${session.samples.length} samples`);
    }

    /**
     * Disables publishing and says why, when capture is unavailable.
     *
     * Surfaced at load rather than at connect time, because an operator who opens
     * this page at a LAN address loses publishing entirely and the only other
     * symptom is a zero uplink bitrate in the export.
     */
    function warnIfMediaCaptureUnavailable() {
        if (navigator.mediaDevices !== undefined) {
            return;
        }

        elements.publishMedia.checked = false;
        setStatus(
            `Camera and microphone are unavailable: ${describeMediaCaptureBlocker()}`,
            'error',
        );
        log('publishing disabled — see status');
    }

    /**
     * Asks for confirmation before a navigation discards an in-progress run.
     *
     * Sampling stops when the tab is closed, so an operator who reloads mid-run
     * loses everything collected. TC-02 runs for 15 minutes, which is long enough
     * for that to be a real risk.
     */
    function registerUnloadGuard() {
        window.addEventListener('beforeunload', (event) => {
            if (session.samples.length > 0 && session.sampleTimer !== null) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
    }

    elements.connect.addEventListener('click', () => {
        connect().catch((error) => {
            setStatus(`Connect failed: ${error}`, 'error');
            log(`connect threw: ${error}`);
        });
    });

    elements.disconnect.addEventListener('click', () => {
        disconnect().catch((error) => log(`disconnect threw: ${error}`));
    });

    elements.screenShare.addEventListener('click', () => {
        toggleScreenShare().catch((error) =>
            log(`screen share threw: ${error}`),
        );
    });

    elements.export.addEventListener('click', exportCsv);

    warnIfMediaCaptureUnavailable();
    registerUnloadGuard();

    log('harness ready');
})();
