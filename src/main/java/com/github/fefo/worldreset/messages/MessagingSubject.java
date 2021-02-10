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
