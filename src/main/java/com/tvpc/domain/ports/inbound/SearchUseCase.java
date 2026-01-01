package com.tvpc.domain.ports.inbound;

import com.tvpc.domain.SearchCriteria;
import com.tvpc.domain.SearchResult;
import com.tvpc.domain.ExportResult;
import io.vertx.core.Future;

/**
 * Inbound port for search and query operations.
 */
public interface SearchUseCase {

    /**
     * Searches settlements based on criteria.
     *
     * @param criteria The search criteria
     * @return Search results with groups and settlements
     */
    Future<SearchResult> search(SearchCriteria criteria);

    /**
     * Exports search results to Excel.
     *
     * @param criteria The search criteria
     * @return Export result with file data
     */
    Future<ExportResult> exportToExcel(SearchCriteria criteria);
}
