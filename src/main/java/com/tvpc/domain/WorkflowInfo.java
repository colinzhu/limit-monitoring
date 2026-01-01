package com.tvpc.domain;

import java.util.List;

/**
 * Value object containing workflow information for a settlement.
 */
public class WorkflowInfo {
    private final boolean hasUserRequested;
    private final boolean isAuthorized;
    private final List<String> requesters;  // User IDs who requested
    private final List<String> authorizers; // User IDs who authorized

    public WorkflowInfo(boolean hasUserRequested, boolean isAuthorized, List<String> requesters, List<String> authorizers) {
        this.hasUserRequested = hasUserRequested;
        this.isAuthorized = isAuthorized;
        this.requesters = requesters;
        this.authorizers = authorizers;
    }

    public boolean hasUserRequested() {
        return hasUserRequested;
    }

    public boolean isAuthorized() {
        return isAuthorized;
    }

    public List<String> getRequesters() {
        return requesters;
    }

    public List<String> getAuthorizers() {
        return authorizers;
    }

    public boolean isPendingAuthorise() {
        return hasUserRequested && !isAuthorized;
    }

    @Override
    public String toString() {
        return "WorkflowInfo{" +
                "hasUserRequested=" + hasUserRequested +
                ", isAuthorized=" + isAuthorized +
                ", requesters=" + requesters +
                ", authorizers=" + authorizers +
                '}';
    }
}
