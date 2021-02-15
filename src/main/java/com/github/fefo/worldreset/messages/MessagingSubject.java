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

package com.github.fefo.worldreset.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MessagingSubject implements ForwardingAudience.Single {

  private final UUID uuid;
  private final Audience audience;
  private final String name;
  private final Predicate<? super String> hasPermission;
  private final Supplier<? extends World> worldSupplier;

  MessagingSubject(
      final @NotNull UUID uuid, final @NotNull Audience audience,
      final @NotNull String name,
      final @NotNull Predicate<? super String> hasPermission,
      final @Nullable Supplier<? extends World> worldSupplier) {
    this.uuid = uuid;
    this.audience = audience;
    this.name = name;
    this.hasPermission = hasPermission;
    this.worldSupplier = worldSupplier;
  }

  @Override
  public @NotNull Audience audience() {
    return this.audience;
  }

  public @NotNull String getName() {
    return this.name;
  }

  public boolean hasPermission(final String permission) {
    return this.hasPermission.test(permission);
  }

  public boolean existsInWorld() {
    return this.worldSupplier != null;
  }

  public @NotNull World getWorld() {
    if (this.worldSupplier == null) {
      throw new UnsupportedOperationException("Unable to get world from "
                                              + getName() + ';' + this.uuid);
    }
    return this.worldSupplier.get();
  }
}
