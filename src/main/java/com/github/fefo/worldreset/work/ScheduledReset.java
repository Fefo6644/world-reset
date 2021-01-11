package com.github.fefo.worldreset.work;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

public final class ScheduledReset implements Serializable {

  private static final long serialVersionUID = 122278421932L;

  private final String worldName;
  private final Duration interval;
  private final Instant nextReset;

  public ScheduledReset(final Duration interval, final String worldName) {
    this(interval, Instant.now(), worldName);
  }

  public ScheduledReset(final Duration interval, final Instant from, final String worldName) {
    this.worldName = worldName;
    this.interval = interval;
    this.nextReset = from.plus(interval);
  }

  public boolean auditReset() {
    return this.nextReset.isBefore(Instant.now());
  }

  public String getWorldName() {
    return this.worldName;
  }

  public Duration getInterval() {
    return this.interval;
  }

  public Instant getNextReset() {
    return this.nextReset;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ScheduledReset)) {
      return false;
    }

    final ScheduledReset that = (ScheduledReset) other;
    return this.worldName.equals(that.worldName);
  }

  @Override
  public int hashCode() {
    return this.worldName.hashCode();
  }
}
