package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.commands.DurationArgumentType;
import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class DurationConfigKey extends ConfigKey<Duration> {

  private static final DurationArgumentType DURATION_PARSER = DurationArgumentType.duration();

  public DurationConfigKey(final @NotNull String key, final @NotNull Duration fallback,
                           final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Duration get(final @NotNull YamlConfigAdapter configAdapter) {
    final String raw = configAdapter.getString(this.key);
    try {
      return raw == null ? this.fallback : DURATION_PARSER.parse(new StringReader(raw));
    } catch (final CommandSyntaxException exception) {
      exception.printStackTrace();
      return this.fallback;
    }
  }
}
