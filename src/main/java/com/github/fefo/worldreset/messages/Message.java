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

package com.github.fefo.worldreset.messages;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.stream.Collectors;

import static com.github.fefo.worldreset.util.Utils.longDuration;
import static com.github.fefo.worldreset.util.Utils.shortDuration;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection;

public interface Message {

  Component PREFIX =
      text()
          .color(GRAY)
          .append(text('['),
                  text("WR", GOLD, BOLD),
                  text(']'))
          .build();

  Args1<Plugin> PLUGIN_INFO = plugin ->
      text()
          .color(YELLOW)
          .append(text("WorldReset", GOLD),
                  space(),
                  text("by"),
                  space(),
                  text()
                      .append(join(text(", "),
                                   plugin.getDescription().getAuthors().stream()
                                         .map(Component::text).collect(Collectors.toList()))),
                  text(" - ", GRAY),
                  text('v'),
                  text(plugin.getDescription().getVersion()));

  Args0 NO_PERMISSION = () ->
      prefixed()
          .append(text("You are not allowed to run this command", RED));

  Args1<String> CONSOLE_INCOMPLETE_COMMAND = what ->
      prefixed()
          .color(RED)
          .append(join(space(),
                       text("Please"),
                       text(what),
                       text("when running this command from console")));

  Args2<String, Duration> SCHEDULED_SUCCESSFULLY = (world, interval) ->
      prefixed()
          .color(GRAY)
          .append(join(space(),
                       text("World reset scheduled successfully."),
                       text("World"),
                       text(world, AQUA),
                       text("will reset every"),
                       text(shortDuration(interval), GREEN)
                           .hoverEvent(showText(text(longDuration(interval))))));

  Args2<String, Duration> RESCHEDULED_SUCCESSFULLY = (world, interval) ->
      prefixed()
          .color(GRAY)
          .append(join(space(),
                       text("World reset rescheduled successfully."),
                       text("World"),
                       text(world, AQUA),
                       text("will reset every"),
                       text(shortDuration(interval), GREEN)
                           .hoverEvent(showText(text(longDuration(interval))))));

  Args1<String> UNSCHEDULED_SUCCESSFULLY = world ->
      prefixed()
          .color(GRAY)
          .append(join(space(),
                       text("World"),
                       text(world, AQUA),
                       text("has been unscheduled for reset")));

  Args1<String> WASNT_SCHEDULED = world ->
      prefixed()
          .color(GRAY)
          .append(join(space(),
                       text("World"),
                       text(world, AQUA),
                       text("was not scheduled for reset")));

  Args0 LIST_SCHEDULED_RESETS_TITLE = () ->
      prefixed()
          .color(WHITE)
          .append(text("Worlds scheduled to reset"),
                  space(),
                  text()
                      .color(GRAY)
                      .append(text('('),
                              join(text(" - "),
                                   text("world"),
                                   text("next reset"),
                                   text("interval")),
                              text(')')),
                  text(':'));

  Args3<String, Duration, Duration> LIST_SCHEDULED_RESETS_ELEMENT = (world, until, interval) ->
      prefixed()
          .append(join(text(" - ", GRAY),
                       text(world, AQUA)
                           .hoverEvent(showText(text()
                                                    .append(text("Click to unschedule", WHITE),
                                                            space(),
                                                            text(world, AQUA)))),
                       text().apply(builder -> {
                         if (until.isNegative()) {
                           builder
                               .append(text("Next server restart", GREEN));
                         } else {
                           builder
                               .append(text(shortDuration(until), GREEN)
                                           .hoverEvent(showText(text(longDuration(until), WHITE))));
                         }
                       }),
                       text(shortDuration(interval), GREEN)
                           .hoverEvent(showText(text(longDuration(interval), WHITE))))
                      .clickEvent(suggestCommand("/worldreset unschedule " + world)));

  Args0 LIST_SCHEDULED_RESETS_NO_ELEMENT = () ->
      prefixed()
          .append(text("There are no scheduled resets", GRAY));

  Args0 ERROR_WHILE_SAVING = () ->
      prefixed()
          .color(RED)
          .append(text("There was an error while saving scheduled data."),
                  space(),
                  text("Please check console for any errors"));

  Args0 USAGE_TITLE = () ->
      prefixed()
          .append(text("Usage(s):", WHITE));

  Args1<String> USAGES_COMMAND = usage ->
      text()
          .color(RED)
          .append(text('/'),
                  text(usage))
          .hoverEvent(showText(text()
                                   .append(text("Click to run:", WHITE),
                                           space(),
                                           text('/', GRAY),
                                           text(usage, GRAY))))
          .clickEvent(suggestCommand('/' + usage));

  Args1<String> UNKNOWN_WORLD = unknownWorld ->
      prefixed()
          .color(RED)
          .append(join(space(),
                       text("No world for name"),
                       text(unknownWorld, AQUA),
                       text("was found")));

  Args1<String> COMMAND_ERROR = error ->
      prefixed()
          .append(text(error, RED));

  static TextComponent.Builder prefixed() {
    return TextComponent.ofChildren(PREFIX, space()).toBuilder().resetStyle();
  }

  @FunctionalInterface
  interface Args0 {

    default void send(final Audience audience) {
      audience.sendMessage(build());
    }

    default String legacy() {
      return legacySection().serialize(build().asComponent());
    }

    ComponentLike build();
  }

  @FunctionalInterface
  interface Args1<T> {

    default void send(final Audience audience, final T t) {
      audience.sendMessage(build(t));
    }

    default String legacy(final T t) {
      return legacySection().serialize(build(t).asComponent());
    }

    ComponentLike build(T t);
  }

  @FunctionalInterface
  interface Args2<T, S> {

    default void send(final Audience audience, final T t, final S s) {
      audience.sendMessage(build(t, s));
    }

    default String legacy(final T t, final S s) {
      return legacySection().serialize(build(t, s).asComponent());
    }

    ComponentLike build(T t, S s);
  }

  @FunctionalInterface
  interface Args3<T, S, R> {

    default void send(final Audience audience, final T t, final S s, final R r) {
      audience.sendMessage(build(t, s, r));
    }

    default String legacy(final T t, final S s, final R r) {
      return legacySection().serialize(build(t, s, r).asComponent());
    }

    ComponentLike build(T t, S s, R r);
  }

  @FunctionalInterface
  interface Args4<T, S, R, Q> {

    default void send(final Audience audience, final T t, final S s, final R r, final Q q) {
      audience.sendMessage(build(t, s, r, q));
    }

    default String legacy(final T t, final S s, final R r, final Q q) {
      return legacySection().serialize(build(t, s, r, q).asComponent());
    }

    ComponentLike build(T t, S s, R r, Q q);
  }

  @FunctionalInterface
  interface Args5<T, S, R, Q, P> {

    default void send(final Audience audience, final T t, final S s, final R r, final Q q, final P p) {
      audience.sendMessage(build(t, s, r, q, p));
    }

    default String legacy(final T t, final S s, final R r, final Q q, final P p) {
      return legacySection().serialize(build(t, s, r, q, p).asComponent());
    }

    ComponentLike build(T t, S s, R r, Q q, P p);
  }
}
