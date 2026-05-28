package analytics.model;

public record ServerStatus(String name, boolean online, long latencyMs,
                           int requestCount, int errorCount, long uptimeSeconds) {}
