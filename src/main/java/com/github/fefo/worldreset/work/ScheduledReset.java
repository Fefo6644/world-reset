//
// This file is part of WorldReset, licensed under the MIT License.
//
// Copyright (c) 2021 Fefo6644 <federico.lopez.1999@outlook.com>
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

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
