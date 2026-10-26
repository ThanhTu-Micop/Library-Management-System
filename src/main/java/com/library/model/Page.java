package com.library.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A paginated result used by search and list screens.
 *
 * <p>Generics keep one pagination wrapper reusable for books, members, and loans.
 *
 * @param <T> the element type in the page
 */
public class Page<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;

    public Page(List<T> content, long totalElements, int page, int size) {
        this.content = content != null ? Collections.unmodifiableList(new ArrayList<>(content)) : Collections.emptyList();
        this.totalElements = totalElements;
        this.page = Math.max(0, page);
        this.size = Math.max(1, size);
        this.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public List<T> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public boolean isFirst() {
        return page <= 0;
    }

    public boolean isLast() {
        return totalPages <= 0 || page >= totalPages - 1;
    }

    public boolean hasNext() {
        return page < totalPages - 1;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Page)) return false;
        Page<?> page1 = (Page<?>) o;
        return totalElements == page1.totalElements
                && totalPages == page1.totalPages
                && page == page1.page
                && size == page1.size
                && Objects.equals(content, page1.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, totalElements, totalPages, page, size);
    }

    @Override
    public String toString() {
        return "Page{content=" + content + ", totalElements=" + totalElements + ", totalPages=" + totalPages + ", page=" + page + ", size=" + size + "}";
    }
}
