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

package com.github.fefo.worldreset.commands;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.github.fefo.worldreset.WorldResetPlugin;
import com.github.fefo.worldreset.config.ConfigKeys;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.Message;
import com.github.fefo.worldreset.messages.MessagingSubject;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.util.CommandMapHelper;
import com.github.fefo.worldreset.work.ScheduledReset;
import com.github.fefo.worldreset.work.WorldsDataHandler;
import com.google.common.collect.ImmutableList;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.fefo.worldreset.commands.DurationArgumentType.duration;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public final class WorldResetCommand extends Command implements Listener {

  private static final Pattern COMMAND_PATTERN = Pattern.compile("^/?(?:worldreset:)?worldreset ");

  private final WorldResetPlugin plugin;
  private final WorldsDataHandler worldsDataHandler;
  private final YamlConfigAdapter configAdapter;
  private final SubjectFactory subjectFactory;

  private final ExecutorService asyncExecutor =
      Executors.newFixedThreadPool(5, new ThreadFactoryBuilder()
          .setPriority(Thread.NORM_PRIORITY)
          .setDaemon(false)
          .setNameFormat("worldreset-command-pool-thread-%d")
          .build());

  private final CommandDispatcher<MessagingSubject> dispatcher = new CommandDispatcher<>();
  private final RootCommandNode<MessagingSubject> rootNode = this.dispatcher.getRoot();

  public WorldResetCommand(final WorldResetPlugin plugin) {
    super("worldreset", "General administration command for world resetting", "/worldreset help", ImmutableList.of());
    this.plugin = plugin;
    this.configAdapter = plugin.getConfigAdapter();
    this.subjectFactory = plugin.getSubjectFactory();
    this.worldsDataHandler = plugin.getWorldsDataHandler();

    setPermission("worldreset.command");
    setPermissionMessage(Message.NO_PERMISSION.legacy());
    CommandMapHelper.getCommandMap().register(plugin.getName(), this);

    try {
      Class.forName("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent");
      Bukkit.getPluginManager().registerEvents(this, plugin);
    } catch (final ClassNotFoundException exception) {
      // ignore
    }

    final LiteralArgumentBuilder<MessagingSubject> builder = literal(getName());
    builder
        .requires(subject -> subject.hasPermission(getPermission()))
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
                            .suggests(this::suggestScheduledWorlds)
                            .executes(this::unscheduleWorld)))
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
    Message.LIST_SCHEDULED_RESETS_TITLE.send(subject);

    final Iterator<? extends ScheduledReset> iterator = this.worldsDataHandler.getScheduledResets().iterator();
    if (!iterator.hasNext()) {
      Message.LIST_SCHEDULED_RESETS_NO_ELEMENT.send(subject);
      return 1;
    }

    final Instant now = Instant.now();
    do {
      final ScheduledReset scheduledReset = iterator.next();
      Message.LIST_SCHEDULED_RESETS_ELEMENT.send(subject, scheduledReset.getWorldName(),
                                                 Duration.between(now, scheduledReset.getNextReset()),
                                                 scheduledReset.getInterval());
    } while (iterator.hasNext());
    return 1;
  }

  private void schedule(final String worldName, final Duration interval, final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.send(subject, worldName);
      return;
    }

    try {
      switch (this.worldsDataHandler.schedule(worldName, interval)) {
        case SUCCESS_OTHER:
          Message.SCHEDULED_SUCCESSFULLY.send(subject, worldName, interval);
          break;

        case SUCCESS_RESCHEDULED:
          Message.RESCHEDULED_SUCCESSFULLY.send(subject, worldName, interval);
          break;
      }

      this.worldsDataHandler.save();
    } catch (final IOException exception) {
      exception.printStackTrace();
      Message.ERROR_WHILE_SAVING.send(subject);
    }
  }

  private void unschedule(final String worldName, final MessagingSubject subject) {
    if (Bukkit.getWorld(worldName) == null) {
      Message.UNKNOWN_WORLD.send(subject, worldName);
      return;
    }

    if (this.worldsDataHandler.unschedule(worldName)) {
      Message.UNSCHEDULED_SUCCESSFULLY.send(subject, worldName);
      try {
        this.worldsDataHandler.save();
      } catch (final IOException exception) {
        exception.printStackTrace();
        Message.ERROR_WHILE_SAVING.send(subject);
      }
    } else {
      Message.WASNT_SCHEDULED.send(subject, worldName);
    }
  }

  private int unscheduleCurrent(final CommandContext<MessagingSubject> context) {
    final MessagingSubject subject = context.getSource();
    if (subject.existsInWorld()) {
      unschedule(subject.getWorld().getName(), subject);
      return 1;
    }

    Message.CONSOLE_INCOMPLETE_COMMAND.send(subject, "provide a world");
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

    Message.CONSOLE_INCOMPLETE_COMMAND.send(subject, "provide a world");
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
    Message.PLUGIN_INFO.send(subject, this.plugin);
    usages(subject);
    return 1;
  }

  private CompletableFuture<Suggestions> suggestScheduledWorlds(final CommandContext<MessagingSubject> context, final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    this.worldsDataHandler.getScheduledResets().stream()
                          .map(ScheduledReset::getWorldName).filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestWorlds(final CommandContext<MessagingSubject> context, final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    Bukkit.getWorlds().stream().map(World::getName).filter(world -> {
      return world.toLowerCase(Locale.ROOT).startsWith(current);
    }).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private void usages(final MessagingSubject subject) {
    Message.USAGE_TITLE.send(subject);
    for (final String usage : this.dispatcher.getAllUsage(this.rootNode, subject, true)) {
      Message.USAGES_COMMAND.send(subject, usage);
    }
  }

  @Override
  public boolean execute(final @NotNull CommandSender sender,
                         final @NotNull String alias,
                         final @NotNull String @NotNull [] args) {
    this.asyncExecutor.execute(() -> {
      final String input = getName() + ' ' + String.join(" ", args);
      final MessagingSubject subject = this.subjectFactory.from(sender);
      final ParseResults<MessagingSubject> results = this.dispatcher.parse(input.trim(), subject);

      if (results.getContext().getNodes().isEmpty()) {
        Message.NO_PERMISSION.send(subject);
        return;
      }

      final Map<CommandNode<MessagingSubject>, CommandSyntaxException> map = results.getExceptions();
      if (!map.isEmpty()) {
        map.values().forEach(exception -> {
          Message.COMMAND_ERROR.send(subject, exception.getMessage());
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

  private List<String> tabComplete(final String input, final MessagingSubject subject) {
    return this.dispatcher.getCompletionSuggestions(this.dispatcher.parse(input, subject)).join()
                          .getList().stream()
                          .map(Suggestion::getText)
                          .collect(Collectors.toList());
  }

  @Override
  public @NotNull List<String> tabComplete(final @NotNull CommandSender sender,
                                           final @NotNull String alias,
                                           final @NotNull String @NotNull [] args) {
    final String input = getName() + ' ' + String.join(" ", args);
    final MessagingSubject subject = this.subjectFactory.from(sender);
    return tabComplete(input, subject);
  }

  @EventHandler(ignoreCancelled = true)
  private void onAsyncTabComplete(final AsyncTabCompleteEvent event) {
    if (event.isHandled() || !event.isCommand()) {
      return;
    }

    final String buffer = event.getBuffer();
    if (!COMMAND_PATTERN.matcher(buffer).find()) {
      return;
    }

    final MessagingSubject subject = this.subjectFactory.from(event.getSender());
    final String input = getName() + buffer.substring(buffer.indexOf(' '));

    event.setCompletions(tabComplete(input, subject));
    event.setHandled(true);
  }

  private LiteralArgumentBuilder<MessagingSubject> literal(final String name) {
    return LiteralArgumentBuilder.literal(name);
  }

  private <T> RequiredArgumentBuilder<MessagingSubject, T> argument(final String name, final ArgumentType<T> type) {
    return RequiredArgumentBuilder.argument(name, type);
  }
}
