package io.github.smiskinext.chatmanagement.application.port;

/**
 * Port for generating monotonic per-room sequence numbers.
 *
 * <p>Implementations use atomic distributed counters (e.g. MongoDB findAndModify).
 */
public interface SeqCounter {

    /**
     * Returns the next sequence number for the given room, atomically incrementing the counter.
     *
     * @param roomId the chat room identifier
     * @return the next sequence number (starts at 1 for a new room)
     */
    long nextSeq(String roomId);
}
