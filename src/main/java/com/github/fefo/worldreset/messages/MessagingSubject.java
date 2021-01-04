package com.github.fefo.worldreset.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.identity.Identity;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MessagingSubject implements ForwardingAudience.Single, Identified {

  private final Identity identity;
  private final Audience audience;
  private final String name;
  private final Function<? super String, ? extends Boolean> hasPermission;
  private final Supplier<? extends World> worldSupplier;

  public MessagingSubject(
      final @NotNull Identity identity, final @NotNull Audience audience,
      final @NotNull String name,
      final @NotNull Function<? super String, ? extends Boolean> hasPermission,
      final @Nullable Supplier<? extends World> worldSupplier) {
    this.identity = identity;
    this.audience = audience;
    this.name = name;
    this.hasPermission = hasPermission;
    this.worldSupplier = worldSupplier;
  }

  @Override
  public @NotNull Audience audience() {
    return this.audience;
  }

  @Override
  public @NotNull Identity identity() {
    return this.identity;
  }

  public @NotNull UUID getUuid() {
    return this.identity.uuid();
  }

  public @NotNull String getName() {
    return this.name;
  }

  public boolean hasPermission(final String permission) {
    return this.hasPermission.apply(permission).booleanValue();
  }

  public boolean existsInWorld() {
    return this.worldSupplier != null;
  }

  public @NotNull World getWorld() {
    if (this.worldSupplier == null) {
      throw new UnsupportedOperationException("Unable to get world from " + getName() + getUuid());
    }
    return this.worldSupplier.get();
  }
}
