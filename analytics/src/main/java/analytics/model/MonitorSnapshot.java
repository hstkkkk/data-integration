package analytics.model;

import java.time.Instant;
import java.util.List;

public record MonitorSnapshot(Instant timestamp, List<ServerStatus> servers) {
  public MonitorSnapshot(List<ServerStatus> servers) {
    this(Instant.now(), List.copyOf(servers));
  }
}
