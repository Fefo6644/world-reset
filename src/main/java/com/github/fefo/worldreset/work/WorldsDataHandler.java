//
// This file is part of WorldReset, licensed under the MIT License.
//
// Copyright (c) 2021 Fefo6644 <federico.lopez.1999@outlook.com>
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package com.github.fefo.worldreset.work;

import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.util.Utils;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public final class WorldsDataHandler {

  @Deprecated
  private static final Type SCHEDULED_RESET_MAP_TYPE = TypeToken.getParameterized(Map.class, String.class, ScheduledReset.class).getType();
  private static final Type SCHEDULED_RESET_SET_TYPE = TypeToken.getParameterized(Set.class, ScheduledReset.class).getType();
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Path WORLDS_FOLDER = Bukkit.getWorldContainer().toPath();

  private static final PathMatcher REGION_FILE_MATCHER =
      FileSystems.getDefault().getPathMatcher("regex:r(?:\\.-?\\d){2}\\.mca");
  private static final Predicate<Path> IS_INNER_REGION = path -> {
    return path.endsWith("r.0.0.mca")
           || path.endsWith("r.0.-1.mca")
           || path.endsWith("r.-1.0.mca")
           || path.endsWith("r.-1.-1.mca");
  };
  private static final Predicate<Path> IS_OUTER_REGION =
      IS_INNER_REGION.negate().and(path -> REGION_FILE_MATCHER.matches(path.getFileName()));

  private final SubjectFactory subjectFactory;
  private final YamlConfigAdapter configAdapter;
  private final Path worldsJson;
  private final Set<Duration> broadcastMoments = new HashSet<>();
  private final Set<ScheduledReset> scheduledResets = new HashSet<>();
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                                                     .setPriority(Thread.NORM_PRIORITY)
                                                     .setDaemon(false)
                                                     .setNameFormat("worldreset-worker-pool-thread-%d")
                                                     .build());

  public WorldsDataHandler(final WorldResetPlugin plugin) {
    this.subjectFactory = plugin.getSubjectFactory();
    this.configAdapter = plugin.getConfigAdapter();
    this.worldsJson = plugin.getPluginDataFolder().resolve("worlds.json");
  }

  public void load() throws IOException {
    if (Files.notExists(this.worldsJson)) {
      Files.createFile(this.worldsJson);
      try (final BufferedWriter writer = Files.newBufferedWriter(this.worldsJson, WRITE)) {
        writer.write("[]");
        writer.newLine();
      }
    }

    JsonElement read;
    try (final Reader reader = Files.newBufferedReader(this.worldsJson, UTF_8)) {
      read = GSON.fromJson(reader, JsonElement.class);
    } catch (final JsonParseException exception) {
      final String message = String.format("There was an error reading %s, making backup and generating an empty JSON file. "
                                           + "Please send the faulty file to the plugin author!",
                                           this.worldsJson.toString());
      WorldResetPlugin.LOGGER.warn(message, exception);

      final String backup = String.format("world.%s.err.json", DATE_TIME_FORMATTER.format(Instant.now()));
      Files.move(this.worldsJson, this.worldsJson.resolveSibling(backup));
      Files.createFile(this.worldsJson);
      try (final BufferedWriter writer = Files.newBufferedWriter(this.worldsJson, WRITE)) {
        writer.write("[]");
        writer.newLine();
      }

      read = new JsonObject();
    }

    final Set<ScheduledReset> set;
    if (read.isJsonObject()) {
      final Map<String, ScheduledReset> map = GSON.fromJson(read, SCHEDULED_RESET_MAP_TYPE);
      set = new HashSet<>(map != null ? map.values() : ImmutableSet.of());
    } else if (read.isJsonArray()) {
      set = GSON.fromJson(read, SCHEDULED_RESET_SET_TYPE);
    } else {
      set = null;
    }
    this.scheduledResets.addAll(set != null ? set : ImmutableSet.of());

    this.broadcastMoments.addAll(this.configAdapter.get(ConfigKeys.BROADCAST_PRIOR_RESET).stream()
                                                   .map(Utils::parseDuration)
                                                   .collect(Collectors.toSet()));
    this.scheduler.scheduleWithFixedDelay(this::auditResets, 5L, 5L, TimeUnit.SECONDS);
  }

  public void deleteAny() {
    final Instant now = Instant.now();
    getScheduledResets().stream().filter(ScheduledReset::auditReset).forEach(reset -> {
      deleteRegionsRecursively(WORLDS_FOLDER.resolve(reset.getWorldName()));

      Instant nextResetFrom = reset.getNextReset();
      while (nextResetFrom.plus(reset.getInterval()).isBefore(now)) {
        nextResetFrom = nextResetFrom.plus(reset.getInterval());
      }

      this.scheduledResets.add(new ScheduledReset(reset.getInterval(), nextResetFrom, reset.getWorldName()));
    });
  }

  public void save() throws IOException {
    try (final Writer writer = Files.newBufferedWriter(this.worldsJson, WRITE, TRUNCATE_EXISTING)) {
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

  public Set<ScheduledReset> getScheduledResets() {
    return new HashSet<>(this.scheduledResets);
  }

  public WorldOperationResult schedule(final String worldName, final Duration interval) {
    final boolean removed = this.scheduledResets.removeIf(reset -> reset.getWorldName().equalsIgnoreCase(worldName));
    this.scheduledResets.add(new ScheduledReset(interval, worldName));
    return removed ? WorldOperationResult.SUCCESS_OTHER : WorldOperationResult.SUCCESS_RESCHEDULED;
  }

  public boolean unschedule(final String worldName) {
    return this.scheduledResets.removeIf(reset -> reset.getWorldName().equalsIgnoreCase(worldName));
  }

  public void auditResets() {
    if (this.isShuttingDown.get()) {
      return;
    }

    for (final ScheduledReset scheduledReset : getScheduledResets()) {
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
