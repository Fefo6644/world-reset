package com.github.fefo.worldreset.work;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ScheduledReset implements Serializable {

  private static final long serialVersionUID = 122278421932L;

  private final String worldName;
  private final Duration interval;
  private final Instant nextReset;

  public ScheduledReset(final @NotNull Duration interval, final @NotNull String worldName) {
    this(interval, Instant.now(), worldName);
  }

  public ScheduledReset(final @NotNull Duration interval, final @NotNull Instant from, final @NotNull String worldName) {
    this.worldName = Objects.requireNonNull(worldName, "worldName");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.nextReset = Objects.requireNonNull(from, "from").plus(interval);
  }

  public boolean auditReset() {
    return this.nextReset.isBefore(Instant.now());
  }

  public @NotNull String getWorldName() {
    return this.worldName;
  }

  public @NotNull Duration getInterval() {
    return this.interval;
  }

  public @NotNull Instant getNextReset() {
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
    return this.worldName.equals(that.worldName)
           && this.interval.equals(that.interval)
           && this.nextReset.equals(that.nextReset);
  }

  @Override
  public int hashCode() {
    int result = this.worldName.hashCode();
    result = 31 * result + this.interval.hashCode();
    result = 31 * result + this.nextReset.hashCode();
    return result;
  }
}
