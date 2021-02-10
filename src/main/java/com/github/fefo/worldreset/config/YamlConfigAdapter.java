package com.github.fefo.worldreset.config;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.Validate;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public final class YamlConfigAdapter {

  private static final Yaml YAML;
  private static final Set<ConfigKey<?>> CONFIG_KEYS =
      ImmutableSet.of(ConfigKeys.DEFAULT_RESET_INTERVAL,
                      ConfigKeys.BROADCAST_MESSAGE,
                      ConfigKeys.BROADCAST_PRIOR_RESET);

  static {
    final LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setAllowRecursiveKeys(false);
    loaderOptions.setAllowDuplicateKeys(false);
    YAML = new Yaml(loaderOptions);
  }

  private static String unexpectedKeyType(final String key, final Class<?> expected,
                                          final Class<?> actual) {
    return "Config key \"" + key + "\" expected to be of type " + expected.getSimpleName() +
           " but got " + actual.getSimpleName() + " instead";
  }

  private static String noValueForKey(final String key, final Class<?> expected) {
    return "No value for config key \"" + key + "\"" +
           " (expected to be of type " + expected.getSimpleName() + ")";
  }

  private final Plugin plugin;
  private final Path dataFolder;
  private final Path configFile;
  private final String separator = ".";
  private final String separatorPattern = Pattern.quote(this.separator);
  private final Map<String, Object> rootRaw = new LinkedHashMap<>();
  private final Map<String, Object> unwind = new LinkedHashMap<>(CONFIG_KEYS.size());

  public YamlConfigAdapter(final Plugin plugin) {
    this.plugin = plugin;
    this.dataFolder = plugin.getDataFolder().toPath();
    this.configFile = this.dataFolder.resolve("config.yml");
  }

  public void load() throws IOException {
    if (Files.notExists(this.configFile)) {
      Files.createDirectories(this.dataFolder);
      try (final InputStream inputStream = this.plugin.getResource("config.yml")) {
        if (inputStream == null) {
          throw new NullPointerException("config.yml inputStream");
        }
        Files.copy(inputStream, this.configFile);
      }
    }

    reload(true);
  }

  public void reload() throws IOException {
    reload(false);
  }

  private void reload(final boolean force) throws IOException {
    try (final Reader reader = Files.newBufferedReader(this.configFile)) {
      final Object read = YAML.load(reader);
      this.rootRaw.putAll(read instanceof Map ? (Map<String, ?>) read : ImmutableMap.of());
      CONFIG_KEYS.forEach(configKey -> {
        if (configKey.isReloadable() || force) {
          this.unwind.put(configKey.getKey(), configKey.get(this));
        }
      });
    }
  }

  public @Nullable Boolean getBoolean(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), Boolean.class);
  }

  public @Nullable Integer getInt(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), Integer.class);
  }

  public @Nullable Double getDouble(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), Double.class);
  }

  public @Nullable String getString(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), String.class);
  }

  @SuppressWarnings("unchecked")
  public <T> @Nullable List<T> getList(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), List.class);
  }

  @SuppressWarnings("unchecked")
  public @Nullable Map<? super String, ?> getSection(final @NotNull String key) {
    Validate.notNull(key);
    return validate(key, get(key), Map.class);
  }

  @SuppressWarnings("unchecked")
  public <T> @NotNull T get(final @NotNull ConfigKey<T> configKey) {
    Validate.notNull(configKey);
    T value = (T) this.unwind.get(configKey.getKey());
    if (value == null) {
      value = configKey.get(this);
      this.unwind.put(configKey.getKey(), value);
    }
    return value;
  }

  private Object get(final String path) {
    return get(path, this.rootRaw);
  }

  private Object get(final String path, final Map<? super String, ?> map) {
    final String[] pathComponents = path.split(this.separatorPattern);

    if (pathComponents.length == 1) {
      return map.get(path);
    }

    final StringJoiner joiner = new StringJoiner(this.separator);
    for (int i = 1; i < pathComponents.length; ++i) {
      joiner.add(pathComponents[i]);
    }

    final String nested = pathComponents[0];
    return get(joiner.toString(), validate(nested, map.get(nested), Map.class));
  }

  private <T> T validate(final String key, final Object value, final Class<T> type) {
    if (value == null) {
      this.plugin.getLogger().warning(noValueForKey(key, type));
      return null;
    }

    if (!type.isInstance(value)) {
      this.plugin.getLogger().warning(unexpectedKeyType(key, type, value.getClass()));
      return null;
    }

    return type.cast(value);
  }
}
