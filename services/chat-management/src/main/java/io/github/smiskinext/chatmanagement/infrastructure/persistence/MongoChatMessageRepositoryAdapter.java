package io.github.smiskinext.chatmanagement.infrastructure.persistence;

import io.github.smiskinext.chatmanagement.application.port.ChatMessageRepository;
import io.github.smiskinext.chatmanagement.domain.model.ChatMessage;
import io.github.phunguy65.zms.shared.domain.CursorPageResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** MongoDB adapter for {@link ChatMessageRepository}. */
@Repository
public class MongoChatMessageRepositoryAdapter implements ChatMessageRepository {

    private final MongoTemplate mongo;

    public MongoChatMessageRepositoryAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return mongo.save(message);
    }

    @Override
    public CursorPageResponse<ChatMessage> findByRoomId(
            String roomId, int pageSize, Optional<Long> beforeSeqNum) {
        int fetchLimit = pageSize + 1;

        Criteria baseCriteria = Criteria.where("roomId").is(roomId);

        if (beforeSeqNum.isPresent()) {
            baseCriteria = baseCriteria.and("seqNum").lt(beforeSeqNum.get());
        }

        Query query = Query.query(baseCriteria)
                .with(Sort.by(Sort.Direction.DESC, "seqNum"))
                .limit(fetchLimit);

        List<ChatMessage> rows = mongo.find(query, ChatMessage.class);

        boolean hasNext = rows.size() > pageSize;
        List<ChatMessage> items = hasNext ? rows.subList(0, pageSize) : rows;

        return CursorPageResponse.of(items, pageSize, hasNext);
    }
}
