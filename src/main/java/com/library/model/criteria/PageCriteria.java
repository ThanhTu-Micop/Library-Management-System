package com.library.model.criteria;

import com.library.config.AppConfig;

/**
 * Shared zero-based pagination state for search criteria beans.
 *
 * <p>Every criteria class inherits the same defaults and clamping rules, so pagination does
 * not have to be duplicated per entity. Subclasses only add their own filters.
 */
public abstract class PageCriteria {

    private Integer page = AppConfig.DEFAULT_PAGE;
    private Integer size = AppConfig.DEFAULT_PAGE_SIZE;

    public Integer getPage() {
        return page != null ? Math.max(0, page) : AppConfig.DEFAULT_PAGE;
    }

    public void setPage(Integer page) {
        this.page = page != null ? Math.max(0, page) : AppConfig.DEFAULT_PAGE;
    }

    public Integer getSize() {
        return size != null ? Math.min(Math.max(1, size), AppConfig.MAX_PAGE_SIZE)
                : AppConfig.DEFAULT_PAGE_SIZE;
    }

    public void setSize(Integer size) {
        this.size = size != null ? Math.min(Math.max(1, size), AppConfig.MAX_PAGE_SIZE)
                : AppConfig.DEFAULT_PAGE_SIZE;
    }
}
