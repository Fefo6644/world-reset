package com.github.fefo.worldreset.work;

import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.util.Utils;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WorldsDataHandler {

  private static final Gson GSON = new Gson();
  private static final Type SCHEDULED_RESET_MAP_TYPE =
      TypeToken.getParameterized(Map.class, String.class, ScheduledReset.class).getType();
  private static final Path WORLDS_FOLDER = Bukkit.getWorldContainer().toPath();

  private static final PathMatcher REGION_FILE_MATCHER =
      FileSystems.getDefault().getPathMatcher("regex:r(?:\\.-?\\d){2}\\.mca");
  private static final Predicate<Path> IS_INNER_REGION = path -> {
    return path.endsWith("r.0.0.mca") || path.endsWith("r.0.-1.mca")
           || path.endsWith("r.-1.0.mca") || path.endsWith("r.-1.-1.mca");
  };
  private static final Predicate<Path> IS_OUTER_REGION =
      IS_INNER_REGION.negate().and(path -> REGION_FILE_MATCHER.matches(Iterables.getLast(path)));

  private final WorldResetPlugin plugin;
  private final SubjectFactory subjectFactory;
  private final YamlConfigAdapter configAdapter;
  private final Path worldsJson;

  private final Set<Duration> broadcastMoments = new HashSet<>();
  private final Map<String, ScheduledReset> scheduledResets = new ConcurrentHashMap<>();

  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setPriority(Thread.NORM_PRIORITY)
                                .setDaemon(false)
                                .setNameFormat("worldreset-worker-pool-thread-%d")
                                .build());

  public WorldsDataHandler(final WorldResetPlugin plugin) {
    this.plugin = plugin;
    this.subjectFactory = plugin.getSubjectFactory();
    this.configAdapter = plugin.getConfigAdapter();
    this.worldsJson = plugin.getDataFolder().toPath().resolve("worlds.json");
  }

  public void load() throws IOException {
    if (Files.notExists(this.worldsJson)) {
      try (final InputStream inputStream = this.plugin.getResource("worlds.json")) {
        if (inputStream == null) {
          throw new NullPointerException("data input stream is null");
        }
        Files.copy(inputStream, this.worldsJson);
      }
    }

    try (final Reader reader = Files.newBufferedReader(this.worldsJson, StandardCharsets.UTF_8)) {
      this.scheduledResets.putAll(GSON.fromJson(reader, SCHEDULED_RESET_MAP_TYPE));
    }

    this.broadcastMoments.addAll(this.configAdapter.get(ConfigKeys.BROADCAST_PRIOR_RESET)
                                                   .parallelStream()
                                                   .map(Utils::parseDuration)
                                                   .collect(Collectors.toSet()));
    this.scheduler.scheduleAtFixedRate(this::auditResets, 5L, 5L, TimeUnit.SECONDS);
  }

  public void deleteAny() {
    final Instant now = Instant.now();
    getScheduledResets().values().parallelStream()
                        .filter(ScheduledReset::auditReset).forEach(reset -> {
      deleteRegionsRecursively(WORLDS_FOLDER.resolve(reset.getWorldName()));

      Instant nextResetFrom = reset.getNextReset();
      while (nextResetFrom.plus(reset.getInterval()).isBefore(now)) {
        nextResetFrom = nextResetFrom.plus(reset.getInterval());
      }

      this.scheduledResets.put(reset.getWorldName(),
                               new ScheduledReset(reset.getInterval(), nextResetFrom,
                                                  reset.getWorldName()));
    });
  }

  public void save() throws IOException {
    try (final Writer writer = Files.newBufferedWriter(this.worldsJson, StandardCharsets.UTF_8)) {
      GSON.toJson(this.scheduledResets, SCHEDULED_RESET_MAP_TYPE, writer);
    }
  }

  public void shutdown() {
    try {
      this.isShuttingDown.set(true);
      this.scheduler.shutdown();
      this.scheduler.awaitTermination(15L, TimeUnit.SECONDS);
      this.scheduledResets.clear();
      this.broadcastMoments.clear();
    } catch (final InterruptedException exception) {
      exception.printStackTrace();
    }
  }

  public Map<String, ScheduledReset> getScheduledResets() {
    return new HashMap<>(this.scheduledResets);
  }

  public WorldOperationResult schedule(final String worldName, final Duration interval) {
    final ScheduledReset previous =
        this.scheduledResets.put(worldName, new ScheduledReset(interval, worldName));
    return previous == null ? WorldOperationResult.SUCCESS_OTHER
                            : WorldOperationResult.SUCCESS_RESCHEDULED;
  }

  public ScheduledReset unschedule(final String worldName) {
    return this.scheduledResets.remove(worldName);
  }

  public void auditResets() {
    if (this.isShuttingDown.get()) {
      return;
    }

    for (final ScheduledReset scheduledReset : getScheduledResets().values()) {
      if (scheduledReset.auditReset()) {
        continue;
      }

      final Duration timeLeft = Duration.between(Instant.now(), scheduledReset.getNextReset());
      if (timeLeft.getSeconds() < 5L) {
        broadcast(result -> "the next restart",
                  result -> "the next restart",
                  result -> scheduledReset.getWorldName());
      }

      for (final Duration moment : this.broadcastMoments) {
        final long diff = Math.abs(timeLeft.getSeconds() - moment.getSeconds());
        if (diff < 3L) {
          broadcast(result -> Utils.shortDuration(timeLeft),
                    result -> Utils.longDuration(timeLeft),
                    result -> scheduledReset.getWorldName());
          break;
        }
      }
    }
  }

  private void broadcast(final Function<? super MatchResult, ? extends String> timeLeftShort,
                         final Function<? super MatchResult, ? extends String> timeLeftLong,
                         final Function<? super MatchResult, ? extends String> world) {
    String message = this.configAdapter.get(ConfigKeys.BROADCAST_MESSAGE);
    message = Utils.replaceAll(Utils.TIME_LEFT_PATTERN, message, timeLeftShort);
    message = Utils.replaceAll(Utils.TIME_LEFT_LONG_PATTERN, message, timeLeftLong);
    message = Utils.replaceAll(Utils.WORLD_NAME_PATTERN, message, world);
    this.subjectFactory.permission("worldreset.receivebroadcast")
                       .sendMessage(Utils.fromLegacy(message));
  }

  private void deleteRegionsRecursively(final Path folder) {
    if (Files.notExists(folder) || !Files.isDirectory(folder)) {
      return;
    }

    try (final Stream<Path> stream = Files.walk(folder)) {
      stream.filter(IS_OUTER_REGION).forEach(region -> {
        try {
          Files.delete(region);
        } catch (final IOException exception) {
          exception.printStackTrace();
        }
      });
    } catch (final IOException exception) {
      exception.printStackTrace();
    }
  }
}
