package io.github.smiskinext.chatmanagement.infrastructure.persistence;

import io.github.smiskinext.chatmanagement.application.port.ChatRoomRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatRoom;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** MongoDB adapter for {@link ChatRoomRepository}. */
@Repository
public class MongoChatRoomRepositoryAdapter implements ChatRoomRepository {

    private final MongoTemplate mongo;

    public MongoChatRoomRepositoryAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public ChatRoom save(ChatRoom room) {
        return mongo.save(room);
    }

    @Override
    public java.util.Optional<ChatRoom> findByRoomId(String roomId) {
        Query query = Query.query(Criteria.where("roomId").is(roomId));
        ChatRoom found = mongo.findOne(query, ChatRoom.class);
        return java.util.Optional.ofNullable(found);
    }

    @Override
    public boolean existsByRoomId(String roomId) {
        Query query = Query.query(Criteria.where("roomId").is(roomId));
        return mongo.exists(query, ChatRoom.class);
    }
}
