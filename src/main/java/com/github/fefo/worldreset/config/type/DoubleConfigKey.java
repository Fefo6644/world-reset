package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

public class DoubleConfigKey extends ConfigKey<Double> {

  public DoubleConfigKey(final @NotNull String key, final double fallback,
                         final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Double get(final @NotNull YamlConfigAdapter configAdapter) {
    final Double value = configAdapter.getDouble(this.key);
    return value == null ? this.fallback : value;
  }
}
