package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SectionConfigKey extends ConfigKey<Map<? super String, ?>> {

  public SectionConfigKey(final @NotNull String key, final @NotNull Map<? super String, ?> fallback,
                          final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Map<? super String, ?> get(final @NotNull YamlConfigAdapter configAdapter) {
    final Map<? super String, ?> section = configAdapter.getSection(this.key);
    return section == null ? this.fallback : section;
  }
}
