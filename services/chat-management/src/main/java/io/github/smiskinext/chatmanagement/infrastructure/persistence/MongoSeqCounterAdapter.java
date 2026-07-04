package io.github.smiskinext.chatmanagement.infrastructure.persistence;

import io.github.smiskinext.chatmanagement.application.port.SeqCounter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** MongoDB implementation of {@link SeqCounter} using atomic findAndModify. */
@Component
public class MongoSeqCounterAdapter implements SeqCounter {

    private final MongoTemplate mongoTemplate;

    public MongoSeqCounterAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long nextSeq(String roomId) {
        Query query = Query.query(Criteria.where("_id").is(roomId));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options =
                FindAndModifyOptions.options().returnNew(true).upsert(true);

        SeqCounterDocument result =
                mongoTemplate.findAndModify(query, update, options, SeqCounterDocument.class);
        return result != null ? result.seq() : 1L;
    }

    /** MongoDB document for per-room sequence counters. */
    @org.springframework.data.mongodb.core.mapping.Document(collection = "seq_counters")
    private static class SeqCounterDocument {

        @org.springframework.data.annotation.Id
        private String id;

        private long seq;

        public SeqCounterDocument() {}

        public SeqCounterDocument(String id, long seq) {
            this.id = id;
            this.seq = seq;
        }

        public String id() {
            return id;
        }

        public long seq() {
            return seq;
        }
    }
}
