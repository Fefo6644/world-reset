package com.github.fefo.worldreset.commands;

import com.github.fefo.worldreset.Utils;
import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.Message;
import com.github.fefo.worldreset.messages.MessagingSubject;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.work.ScheduledReset;
import com.github.fefo.worldreset.work.WorldsDataHandler;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
import java.util.stream.Collectors;

import static com.github.fefo.worldreset.commands.DurationArgumentType.duration;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public final class WorldResetCommand implements TabExecutor {

  private final WorldResetPlugin plugin;
  private final WorldsDataHandler worldsDataHandler;
  private final YamlConfigAdapter configAdapter;
  private final SubjectFactory subjectFactory;
  private final ExecutorService executorService = Executors.newFixedThreadPool(2);
  private final CommandDispatcher<CommandSender> dispatcher = new CommandDispatcher<>();
  private final RootCommandNode<CommandSender> rootNode = this.dispatcher.getRoot();

  public WorldResetCommand(final WorldResetPlugin plugin) {
    this.plugin = plugin;
    this.configAdapter = plugin.getConfigAdapter();
    this.subjectFactory = plugin.getSubjectFactory();
    this.worldsDataHandler = plugin.getWorldsDataHandler();

    final PluginCommand command = plugin.getCommand("worldreset");
    if (command == null) {
      throw new IllegalStateException();
    }
    command.setTabCompleter(this);
    command.setExecutor(this);
    command.setPermissionMessage(Message.NO_PERMISSION.legacy());

    final LiteralArgumentBuilder<CommandSender> builder = literal(command.getLabel());
    builder
        .requires(sender -> sender.hasPermission("worldreset.command"))
        .then(literal("schedule")
                  .executes(this::scheduleDefault)
                  .then(argument("world", string())
                            .suggests(this::suggestWorlds)
                            .executes(this::scheduleWorld)
                            .then(argument("interval", duration(Duration.ofSeconds(10L)))
                                      .executes(this::scheduleWorldWithInterval))))
        .then(literal("unschedule")
                  .executes(this::unscheduleCurrent)
                  .then(argument("world", string())
                            .suggests(this::suggestWorlds)
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
      this.executorService.shutdown();
      this.executorService.awaitTermination(15L, TimeUnit.SECONDS);
    } catch (final InterruptedException exception) {
      exception.printStackTrace();
    }
  }

  private int list(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);
    Message.LIST_SCHEDULED_RESETS_TITLE.send(subject);

    final Iterator<? extends ScheduledReset> iterator =
        this.worldsDataHandler.getScheduledResets().iterator();
    if (!iterator.hasNext()) {
      Message.LIST_SCHEDULED_RESETS_NO_ELEMENT.send(subject);
      return 1;
    }

    final Instant now = Instant.now();
    do {
      final ScheduledReset scheduledReset = iterator.next();
      Message.LIST_SCHEDULED_RESETS_ELEMENT.send(
          subject,
          Utils.shortDuration(Duration.between(now, scheduledReset.getResetInstant())),
          Utils.longDuration(Duration.between(now, scheduledReset.getResetInstant())),
          Utils.shortDuration(scheduledReset.getInterval()),
          Utils.longDuration(scheduledReset.getInterval()));
    } while (iterator.hasNext());
    return 1;
  }

  private void schedule(final String worldName, final Duration interval,
                        final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.send(subject, worldName);
      return;
    }

    this.worldsDataHandler.schedule(worldName, interval);
    Message.SCHEDULED_SUCCESSFULLY.send(subject, worldName,
                                        Utils.shortDuration(interval),
                                        Utils.longDuration(interval));
  }

  private void unschedule(final String worldName, final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.send(subject, worldName);
      return;
    }

    if (this.worldsDataHandler.unschedule(worldName)) {
      Message.UNSCHEDULED_SUCCESSFULLY.send(subject, worldName);
    } else {
      Message.WASNT_SCHEDULED.send(subject, worldName);
    }
  }

  private int resetNow(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);

    final String worldName = StringArgumentType.getString(context, "world");
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.send(subject, worldName);
      return 0;
    }

    Message.RESETTING_NOW.send(subject, worldName);
    this.worldsDataHandler.resetNow(worldName);
    return 1;
  }

  private int unscheduleCurrent(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);
    if (source instanceof Player) {
      final World world = ((Player) source).getWorld();
      unschedule(world.getName(), subject);
      return 1;
    }

    Message.PLAYERS_ONLY.send(subject);
    return 0;
  }

  private int unscheduleWorld(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);

    final String worldName = StringArgumentType.getString(context, "world");
    unschedule(worldName, subject);
    return 1;
  }

  private int scheduleDefault(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);
    if (source instanceof Player) {
      final Duration interval = this.configAdapter.get(ConfigKeys.DEFAULT_RESET_INTERVAL);
      final World world = ((Player) source).getWorld();
      schedule(world.getName(), interval, subject);
      return 1;
    }

    Message.PLAYERS_ONLY.send(subject);
    return 0;
  }

  private int scheduleWorld(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);

    final String worldName = StringArgumentType.getString(context, "world");
    final Duration interval = this.configAdapter.get(ConfigKeys.DEFAULT_RESET_INTERVAL);
    schedule(worldName, interval, subject);
    return 1;
  }

  private int scheduleWorldWithInterval(final CommandContext<CommandSender> context) {
    final CommandSender source = context.getSource();
    final MessagingSubject subject = this.subjectFactory.from(source);

    final String worldName = StringArgumentType.getString(context, "world");
    final Duration interval = DurationArgumentType.getDuration(context, "interval");
    schedule(worldName, interval, subject);
    return 1;
  }

  private int help(final CommandContext<CommandSender> context) {
    final MessagingSubject subject = this.subjectFactory.from(context.getSource());
    Message.PLUGIN_INFO.send(subject, this.plugin.getDescription().getVersion());
    usages(subject, context.getSource());
    return 1;
  }

  private CompletableFuture<Suggestions> suggestWorlds(final CommandContext<CommandSender> context,
                                                       final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    Bukkit.getWorlds().parallelStream().map(World::getName).filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private void usages(final MessagingSubject subject, final CommandSender source) {
    Message.USAGE_TITLE.send(subject);
    for (final String usage : this.dispatcher.getAllUsage(this.rootNode, source, true)) {
      Message.USAGES_COMMAND.send(subject, usage);
    }
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final @NotNull String label,
                           final @NotNull String @NotNull [] args) {
    this.executorService.submit(() -> {
      final String input = command.getLabel() + ' ' + String.join(" ", args);
      final ParseResults<CommandSender> results = this.dispatcher.parse(input.trim(), sender);
      final MessagingSubject subject = this.subjectFactory.from(sender);

      final ParsedCommandNode<CommandSender> last =
          Iterables.getLast(results.getContext().getNodes());
      if (!last.getNode().canUse(sender)) {
        Message.NO_PERMISSION.send(subject);
        return;
      }

      final Map<CommandNode<CommandSender>, CommandSyntaxException> map = results.getExceptions();
      if (!map.isEmpty()) {
        map.values().forEach(exception -> {
          Message.COMMAND_ERROR.send(subject, exception.getMessage());
        });
        return;
      }

      try {
        this.dispatcher.execute(results);
      } catch (final CommandSyntaxException exception) {
        usages(subject, sender);
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
    return this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(input, sender)).join()
                          .getList().parallelStream().map(Suggestion::getText)
                          .collect(Collectors.toList());
  }

  private LiteralArgumentBuilder<CommandSender> literal(final String name) {
    return LiteralArgumentBuilder.literal(name);
  }

  private <T> RequiredArgumentBuilder<CommandSender, T> argument(final String name,
                                                                 final ArgumentType<T> type) {
    return RequiredArgumentBuilder.argument(name, type);
  }
}
