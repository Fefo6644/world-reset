package com.github.fefo.worldreset.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.identity.Identity;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class MessagingSubject implements ForwardingAudience.Single, Identified {

  private final Identity identity;
  private final Audience audience;
  private final String name;

  public MessagingSubject(final @NotNull Identity identity,
                          final @NotNull Audience audience,
                          final @NotNull String name) {
    this.identity = identity;
    this.audience = audience;
    this.name = name;
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
}
