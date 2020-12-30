package com.github.fefo.worldreset.work;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

public final class ScheduledReset implements Serializable {

  private static final long serialVersionUID = 122278421932L;

  private final String worldName;
  private final Duration interval;
  private final Instant resetInstant;

  public ScheduledReset(final Duration interval, final String worldName) {

    this.interval = interval;
    this.resetInstant = Instant.now().plus(interval);
    this.worldName = worldName;
  }

  public boolean auditReset() {
    return this.resetInstant.isBefore(Instant.now());
  }

  public String getWorldName() {
    return this.worldName;
  }

  public Duration getInterval() {
    return this.interval;
  }

  public Instant getResetInstant() {
    return this.resetInstant;
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
