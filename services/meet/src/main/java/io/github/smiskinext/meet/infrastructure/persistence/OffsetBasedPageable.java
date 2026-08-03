package io.github.smiskinext.meet.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} implementation that treats offset as a raw row index rather than deriving it
 * from page number × page size. This allows offset-based pagination where the offset is an
 * arbitrary row position.
 */
final class OffsetBasedPageable implements Pageable {

    private final long offset;
    private final int pageSize;
    private final Sort sort;

    OffsetBasedPageable(long offset, int pageSize, Sort sort) {
        this.offset = offset;
        this.pageSize = pageSize;
        this.sort = sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / pageSize);
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetBasedPageable(offset + pageSize, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        long newOffset = offset - pageSize;
        return new OffsetBasedPageable(Math.max(newOffset, 0), pageSize, sort);
    }

    @Override
    public Pageable first() {
        return new OffsetBasedPageable(0, pageSize, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetBasedPageable((long) pageNumber * pageSize, pageSize, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
