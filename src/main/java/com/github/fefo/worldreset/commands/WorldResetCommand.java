package com.github.fefo.worldreset.commands;

import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.Message;
import com.github.fefo.worldreset.messages.MessagingSubject;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.work.ScheduledReset;
import com.github.fefo.worldreset.work.WorldsDataHandler;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.fefo.worldreset.commands.DurationArgumentType.duration;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public final class WorldResetCommand implements TabExecutor {

  private final WorldResetPlugin plugin;
  private final WorldsDataHandler worldsDataHandler;
  private final YamlConfigAdapter configAdapter;
  private final SubjectFactory subjectFactory;

  private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
      5, new ThreadFactoryBuilder().setPriority(Thread.NORM_PRIORITY)
                                   .setDaemon(false)
                                   .setNameFormat("worldreset-command-pool-thread-%d")
                                   .build());

  private final CommandDispatcher<MessagingSubject> dispatcher = new CommandDispatcher<>();
  private final RootCommandNode<MessagingSubject> rootNode = this.dispatcher.getRoot();
  private final Predicate<? super String> isScheduled;

  public WorldResetCommand(final WorldResetPlugin plugin) {
    this.plugin = plugin;
    this.configAdapter = plugin.getConfigAdapter();
    this.subjectFactory = plugin.getSubjectFactory();
    this.worldsDataHandler = plugin.getWorldsDataHandler();
    this.isScheduled = world -> {
      return this.worldsDataHandler.getScheduledResets().keySet().parallelStream()
                                   .anyMatch(world::equals);
    };

    final PluginCommand command = plugin.getCommand("worldreset");
    if (command == null) {
      throw new IllegalStateException();
    }
    command.setTabCompleter(this);
    command.setExecutor(this);
    command.setPermissionMessage(Message.NO_PERMISSION.legacy());

    final LiteralArgumentBuilder<MessagingSubject> builder = literal(command.getLabel());
    builder
        .requires(subject -> subject.hasPermission("worldreset.command"))
        .then(literal("schedule")
                  .executes(this::scheduleDefault)
                  .then(argument("world", string())
                            .suggests(this::suggestUnscheduledWorlds)
                            .executes(this::scheduleWorld)
                            .then(argument("interval", duration(Duration.ofSeconds(10L)))
                                      .executes(this::scheduleWorldWithInterval))))
        .then(literal("unschedule")
                  .executes(this::unscheduleCurrent)
                  .then(argument("world", string())
                            .suggests(this::suggestScheduledWorlds)
                            .executes(this::unscheduleWorld)))
        .then(literal("now")
                  .then(argument("world", string())
                            .suggests(this::suggestWorlds)
                            .executes(this::resetNow)))
        .then(literal("list")
                  .executes(this::list))
        .then(literal("help")
                  .executes(this::help));

    this.rootNode.addChild(builder.build());
  }

  public void shutdown() {
    try {
      this.asyncExecutor.shutdown();
      this.asyncExecutor.awaitTermination(15L, TimeUnit.SECONDS);
    } catch (final InterruptedException exception) {
      exception.printStackTrace();
    }
  }

  private int list(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();
    Message.LIST_SCHEDULED_RESETS_TITLE.sendMessage(subject);

    final Iterator<? extends ScheduledReset> iterator =
        this.worldsDataHandler.getScheduledResets().values().iterator();
    if (!iterator.hasNext()) {
      Message.LIST_SCHEDULED_RESETS_NO_ELEMENT.sendMessage(subject);
      return 1;
    }

    final Instant now = Instant.now();
    while (iterator.hasNext()) {
      final ScheduledReset scheduledReset = iterator.next();
      Message.LIST_SCHEDULED_RESETS_ELEMENT.sendMessage(
          subject, scheduledReset.getWorldName(),
          Duration.between(now, scheduledReset.getResetInstant()),
          scheduledReset.getInterval());
    }
    return 1;
  }

  private void schedule(final String worldName, final Duration interval,
                        final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.sendMessage(subject, worldName);
      return;
    }

    this.worldsDataHandler.schedule(worldName, interval);
    Message.SCHEDULED_SUCCESSFULLY.sendMessage(subject, worldName, interval);
    try {
      this.worldsDataHandler.save();
    } catch (final IOException exception) {
      exception.printStackTrace();
      Message.ERROR_WHILE_SAVING.sendMessage(subject);
    }
  }

  private void unschedule(final String worldName, final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.sendMessage(subject, worldName);
      return;
    }

    if (this.worldsDataHandler.unschedule(worldName) != null) {
      Message.UNSCHEDULED_SUCCESSFULLY.sendMessage(subject, worldName);
      try {
        this.worldsDataHandler.save();
      } catch (final IOException exception) {
        exception.printStackTrace();
        Message.ERROR_WHILE_SAVING.sendMessage(subject);
      }
    } else {
      Message.WASNT_SCHEDULED.sendMessage(subject, worldName);
    }
  }

  private int resetNow(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();

    final String worldName = StringArgumentType.getString(context, "world");
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.sendMessage(subject, worldName);
      return 0;
    }

    Message.RESETTING_NOW.sendMessage(subject, worldName);
    if (this.worldsDataHandler.resetNow(worldName).join()) {
      try {
        this.worldsDataHandler.save();
      } catch (final IOException exception) {
        exception.printStackTrace();
        Message.ERROR_WHILE_SAVING.sendMessage(subject);
      }
    }
    return 1;
  }

  private int unscheduleCurrent(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();
    if (subject.existsInWorld()) {
      unschedule(subject.getWorld().getName(), subject);
      return 1;
    }

    Message.CONSOLE_INCOMPLETE_COMMAND.sendMessage(subject);
    return 0;
  }

  private int unscheduleWorld(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();

    final String worldName = StringArgumentType.getString(context, "world");
    unschedule(worldName, subject);
    return 1;
  }

  private int scheduleDefault(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();
    if (subject.existsInWorld()) {
      final Duration interval = this.configAdapter.get(ConfigKeys.DEFAULT_RESET_INTERVAL);
      schedule(subject.getWorld().getName(), interval, subject);
      return 1;
    }

    Message.CONSOLE_INCOMPLETE_COMMAND.sendMessage(subject);
    return 0;
  }

  private int scheduleWorld(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();

    final String worldName = StringArgumentType.getString(context, "world");
    final Duration interval = this.configAdapter.get(ConfigKeys.DEFAULT_RESET_INTERVAL);
    schedule(worldName, interval, subject);
    return 1;
  }

  private int scheduleWorldWithInterval(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();

    final String worldName = StringArgumentType.getString(context, "world");
    final Duration interval = DurationArgumentType.getDuration(context, "interval");
    schedule(worldName, interval, subject);
    return 1;
  }

  private int help(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();
    Message.PLUGIN_INFO.sendMessage(subject, this.plugin);
    usages(subject);
    return 1;
  }

  private CompletableFuture<Suggestions> suggestWorlds(
      final CommandContext<MessagingSubject> context, final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    Bukkit.getWorlds().parallelStream().map(World::getName).filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestScheduledWorlds(
      final CommandContext<MessagingSubject> context, final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    this.worldsDataHandler.getScheduledResets().keySet().parallelStream().filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestUnscheduledWorlds(
      final CommandContext<MessagingSubject> context, final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    Bukkit.getWorlds().parallelStream()
          .map(World::getName).filter(this.isScheduled.negate()).filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private void usages(final MessagingSubject subject) {
    Message.USAGE_TITLE.sendMessage(subject);
    for (final String usage : this.dispatcher.getAllUsage(this.rootNode, subject, true)) {
      Message.USAGES_COMMAND.sendMessage(subject, usage);
    }
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final @NotNull String label,
                           final @NotNull String @NotNull [] args) {
    this.asyncExecutor.submit(() -> {
      final String input = command.getLabel() + ' ' + String.join(" ", args);
      final MessagingSubject subject = this.subjectFactory.from(sender);
      final ParseResults<MessagingSubject> results = this.dispatcher.parse(input.trim(), subject);

      if (!Iterables.getLast(results.getContext().getNodes()).getNode().canUse(subject)) {
        Message.NO_PERMISSION.sendMessage(subject);
        return;
      }

      final Map<CommandNode<MessagingSubject>, CommandSyntaxException> map =
          results.getExceptions();
      if (!map.isEmpty()) {
        map.values().forEach(exception -> {
          Message.COMMAND_ERROR.sendMessage(subject, exception.getMessage());
        });
        return;
      }

      try {
        this.dispatcher.execute(results);
      } catch (final CommandSyntaxException exception) {
        usages(subject);
      }
    });

    return true;
  }

  @Override
  public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender,
                                             final @NotNull Command command,
                                             final @NotNull String alias,
                                             final @NotNull String @NotNull [] args) {
    final String input = command.getLabel() + ' ' + String.join(" ", args);
    final MessagingSubject subject = this.subjectFactory.from(sender);
    return this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(input, subject)).join()
                          .getList().parallelStream().map(Suggestion::getText)
                          .collect(Collectors.toList());
  }

  private LiteralArgumentBuilder<MessagingSubject> literal(final String name) {
    return LiteralArgumentBuilder.literal(name);
  }

  private <T> RequiredArgumentBuilder<MessagingSubject, T> argument(final String name,
                                                                    final ArgumentType<T> type) {
    return RequiredArgumentBuilder.argument(name, type);
  }
}
