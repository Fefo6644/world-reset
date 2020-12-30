package com.github.fefo.worldreset.work;

import com.github.fefo.worldreset.Utils;
import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.identity.Identity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class WorldsDataHandler {

  private static final Gson GSON = new Gson();
  private static final Type SCHEDULED_RESET_SET_TYPE =
      TypeToken.getParameterized(Set.class, ScheduledReset.class).getType();
  private static final Predicate<? super Path> IS_INNER_REGION = path -> {
    return path.endsWith("r.0.0.mca") || path.endsWith("r.0.-1.mca")
           || path.endsWith("r.-1.0.mca") || path.endsWith("r.-1.-1.mca");
  };
  private static final Predicate<? super Path> IS_OUTER_REGION = IS_INNER_REGION.negate();
  private static final Map<? super World.Environment, ? extends String> DIMENSION_FOLDERS;

  static {
    final Map<? super World.Environment, String> map = new EnumMap<>(World.Environment.class);
    map.put(World.Environment.NORMAL, ".");
    map.put(World.Environment.NETHER, "DIM-1");
    map.put(World.Environment.THE_END, "DIM1");
    DIMENSION_FOLDERS = Collections.unmodifiableMap(map);
  }

  private final WorldResetPlugin plugin;
  private final SubjectFactory subjectFactory;
  private final YamlConfigAdapter configAdapter;
  private final Path worldsJson;
  private final Executor mainThread;
  private final Set<Duration> broadcastMoments = new HashSet<>();
  private final Set<ScheduledReset> scheduledResets = Collections.synchronizedSet(new HashSet<>());
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  public WorldsDataHandler(final WorldResetPlugin plugin) {
    this.plugin = plugin;
    this.mainThread = task -> Bukkit.getScheduler().runTask(this.plugin, task);
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
      this.scheduledResets.addAll(GSON.fromJson(reader, SCHEDULED_RESET_SET_TYPE));
    }

    this.broadcastMoments.addAll(this.configAdapter.get(ConfigKeys.BROADCAST_PRIOR_RESET)
                                                   .parallelStream()
                                                   .map(Utils::parseDuration)
                                                   .collect(Collectors.toSet()));
    this.scheduler.scheduleAtFixedRate(this::auditResets, 5L, 5L, TimeUnit.SECONDS);
  }

  public void save() throws IOException {
    try (final Writer writer = Files.newBufferedWriter(this.worldsJson, StandardCharsets.UTF_8)) {
      GSON.toJson(this.scheduledResets, SCHEDULED_RESET_SET_TYPE, writer);
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

  public Iterable<? extends ScheduledReset> getScheduledResets() {
    return Collections.unmodifiableCollection(this.scheduledResets);
  }

  public void schedule(final String worldName, final Duration interval) {
    this.scheduledResets.remove(new ScheduledReset(Duration.ZERO, worldName));
    this.scheduledResets.add(new ScheduledReset(interval, worldName));
    try {
      save();
    } catch (final IOException exception) {
      exception.printStackTrace();
    }
  }

  public boolean unschedule(final String worldName) {
    final boolean result =
        this.scheduledResets.remove(new ScheduledReset(Duration.ZERO, worldName));
    try {
      save();
    } catch (final IOException exception) {
      exception.printStackTrace();
    }
    return result;
  }

  public CompletableFuture<? super Void> resetNow(final String worldName) {
    if (this.isShuttingDown.get()) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(() -> {
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
        return;
      }

      String kickMessageTemp = this.configAdapter.get(ConfigKeys.KICK_MESSAGE);
      kickMessageTemp = Utils.replaceAll(Utils.WORLD_NAME_PATTERN, kickMessageTemp, matchResult -> {
        return worldName;
      });
      final String kickMessage = Utils.toLegacy(Utils.fromLegacy(kickMessageTemp));

      world.getPlayers().forEach(player -> {
        player.kickPlayer(kickMessage);
      });

      final WorldCreator worldCreator = WorldCreator.name(worldName);
      worldCreator.copy(world);
      final Path regionFolder = world.getWorldFolder().toPath()
                                     .resolve(DIMENSION_FOLDERS.get(world.getEnvironment()))
                                     .resolve("region");
      Bukkit.unloadWorld(world, true);
      world = null; // memory management :^)

      try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(regionFolder)) {
        for (final Path region : directoryStream) {
          if (IS_OUTER_REGION.test(region)) {
            try {
              Files.delete(region);
            } catch (final IOException exception) {
              exception.printStackTrace();
            }
          }
        }
      } catch (final IOException exception) {
        exception.printStackTrace();
      }

      worldCreator.createWorld();
    }, this.mainThread);
  }

  public void auditResets() {
    if (this.isShuttingDown.get()) {
      return;
    }

    for (final Iterator<ScheduledReset> iterator = this.scheduledResets.iterator();
         iterator.hasNext(); ) {
      final ScheduledReset scheduledReset = iterator.next();
      if (scheduledReset.auditReset()) {
        resetNow(scheduledReset.getWorldName()).join();
        iterator.remove();
        this.scheduledResets.add(new ScheduledReset(scheduledReset.getInterval(),
                                                    scheduledReset.getWorldName()));
        return;
      }

      final Duration timeLeft =
          Duration.between(Instant.now(), scheduledReset.getResetInstant());
      for (final Duration moment : this.broadcastMoments) {
        final long diff = Math.abs(timeLeft.getSeconds() - moment.getSeconds());
        if (diff < 4L) {
          String broadcastMessage = this.configAdapter.get(ConfigKeys.BROADCAST_MESSAGE);
          broadcastMessage =
              Utils.replaceAll(Utils.TIME_LEFT_PATTERN, broadcastMessage, matchResult -> {
                return Utils.shortDuration(timeLeft);
              });
          broadcastMessage =
              Utils.replaceAll(Utils.TIME_LEFT_LONG_PATTERN, broadcastMessage, matchResult -> {
                return Utils.longDuration(timeLeft);
              });
          broadcastMessage =
              Utils.replaceAll(Utils.WORLD_NAME_PATTERN, broadcastMessage, matchResult -> {
                return scheduledReset.getWorldName();
              });
          this.subjectFactory.permission("worldreset.receivebroadcast")
                             .sendMessage(Identity.nil(), Utils.fromLegacy(broadcastMessage));
          break;
        }
      }
    }
  }
}
