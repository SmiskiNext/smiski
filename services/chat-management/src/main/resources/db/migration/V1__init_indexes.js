// V1: Create indexes for chat_messages and chat_rooms collections.
// All createIndex() calls are idempotent — safe to run on both fresh and
// existing databases (MongoDB silently skips if an identical index exists).

// ── chat_messages indexes ───────────────────────────────────────────────

db.chat_messages.createIndex(
    { 'roomId': 1, 'seqNum': 1 },
    { name: 'idx_room_seqnum', unique: false },
);

db.chat_messages.createIndex(
    { 'roomId': 1, 'createdAt': -1 },
    { name: 'idx_room_created_at' },
);

db.chat_messages.createIndex(
    { 'senderId': 1, 'createdAt': -1 },
    { name: 'idx_sender_created_at' },
);

// TTL index: expires documents 30 days after createdAt
db.chat_messages.createIndex(
    { 'createdAt': 1 },
    {
        name: 'idx_ttl_30d',
        expireAfterSeconds: 2592000,
    },
);

// ── chat_rooms indexes ──────────────────────────────────────────────────

db.chat_rooms.createIndex(
    { 'roomId': 1 },
    { name: 'idx_room_id', unique: true },
);

db.chat_rooms.createIndex({ 'meetingId': 1 }, { name: 'idx_meeting_id' });
