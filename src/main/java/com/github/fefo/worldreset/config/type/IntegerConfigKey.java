package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

public class IntegerConfigKey extends ConfigKey<Integer> {

  public IntegerConfigKey(final @NotNull String key, final int fallback, final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Integer get(final @NotNull YamlConfigAdapter configAdapter) {
    final Integer value = configAdapter.getInt(this.key);
    return value == null ? this.fallback : value;
  }
}
