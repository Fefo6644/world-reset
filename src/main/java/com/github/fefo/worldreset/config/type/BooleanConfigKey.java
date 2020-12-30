package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

public class BooleanConfigKey extends ConfigKey<Boolean> {

  public BooleanConfigKey(final @NotNull String key, final boolean fallback,
                          final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Boolean get(final @NotNull YamlConfigAdapter configAdapter) {
    final Boolean value = configAdapter.getBoolean(this.key);
    return value == null ? this.fallback : value;
  }
}
