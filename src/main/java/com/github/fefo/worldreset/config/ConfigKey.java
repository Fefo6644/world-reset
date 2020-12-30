package com.github.fefo.worldreset.config;

import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public abstract class ConfigKey<T> {

  private static final Pattern VALID_KEY = Pattern.compile("^[a-zA-Z-]{4,32}$");

  protected final String key;
  protected final T fallback;
  protected final boolean reloadable;
  protected final int bakedHashCode;

  public ConfigKey(final @NotNull String key, final @NotNull T fallback, final boolean reloadable) {
    Validate.notNull(key);
    Validate.isTrue(VALID_KEY.asPredicate().test(key));
    Validate.notNull(fallback);

    this.key = key;
    this.fallback = fallback;
    this.reloadable = reloadable;
    this.bakedHashCode = hashCodeBakery();
  }

  public abstract @NotNull T get(final @NotNull YamlConfigAdapter configAdapter);

  public @NotNull String getKey() {
    return this.key;
  }

  public @NotNull T getFallback() {
    return this.fallback;
  }

  public boolean isReloadable() {
    return this.reloadable;
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ConfigKey)) {
      return false;
    }

    final ConfigKey<?> that = (ConfigKey<?>) other;
    return this.bakedHashCode == that.bakedHashCode;
  }

  @Override
  public int hashCode() {
    return this.bakedHashCode;
  }

  private int hashCodeBakery() {
    int result = this.key.hashCode();
    result = 31 * result + (this.reloadable ? 1 : 0);
    return result;
  }
}
