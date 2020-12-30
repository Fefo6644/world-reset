package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

public class StringConfigKey extends ConfigKey<String> {


  public StringConfigKey(final @NotNull String key, final @NotNull String fallback,
                         final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull String get(final @NotNull YamlConfigAdapter configAdapter) {
    final String value = configAdapter.getString(this.key);
    return value == null ? this.fallback : value;
  }
}
