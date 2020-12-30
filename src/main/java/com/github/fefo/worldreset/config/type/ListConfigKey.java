package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ListConfigKey<T> extends ConfigKey<List<T>> {

  public ListConfigKey(final @NotNull String key, final @NotNull List<T> fallback,
                       final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull List<T> get(final @NotNull YamlConfigAdapter configAdapter) {
    final List<T> list = configAdapter.getList(this.key);
    return list == null ? this.fallback : list;
  }
}
