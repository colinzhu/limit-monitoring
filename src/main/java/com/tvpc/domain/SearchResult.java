package com.tvpc.domain;

import java.util.List;

/**
 * Search result containing settlement groups and individual settlements.
 */
public class SearchResult {
    private List<SettlementGroup> groups;
    private List<Settlement> settlements;
    private int totalCount;
    private int groupCount;

    public SearchResult(List<SettlementGroup> groups, List<Settlement> settlements, int totalCount, int groupCount) {
        this.groups = groups;
        this.settlements = settlements;
        this.totalCount = totalCount;
        this.groupCount = groupCount;
    }

    public List<SettlementGroup> getGroups() { return groups; }
    public List<Settlement> getSettlements() { return settlements; }
    public int getTotalCount() { return totalCount; }
    public int getGroupCount() { return groupCount; }

    @Override
    public String toString() {
        return "SearchResult{" +
                "groups=" + groups.size() +
                ", settlements=" + settlements.size() +
                ", totalCount=" + totalCount +
                ", groupCount=" + groupCount +
                '}';
    }
}
