package com.github.fefo.worldreset.messages;

import com.github.fefo.worldreset.Utils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
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

public enum Message {
  PREFIX(text()
             .color(GRAY)
             .append(text('['))
             .append(text().append(text("WR", GOLD, BOLD)))
             .append(text(']'))),

  PLUGIN_INFO(text()
                  .color(YELLOW)
                  .append(text("WorldReset", GOLD))
                  .append(space())
                  .append(text("by Fefo"))
                  .append(text(" - ", GRAY))
                  .append(text("v{0}"))),

  NO_PERMISSION(prefixed().append(text("You are not allowed to run this command", RED))),

  CONSOLE_INCOMPLETE_COMMAND(prefixed().append(text("Please provide a world from console", RED))),

  SCHEDULED_SUCCESSFULLY(prefixedLines(text("World reset scheduled successfully", GRAY),
                                       text()
                                           .color(GRAY)
                                           .append(join(space(),
                                                        text("World"),
                                                        text("{0}", AQUA),
                                                        text("will reset every"),
                                                        text("{1}", GREEN)
                                                            .hoverEvent(showText(text("{2}"))))))),

  UNSCHEDULED_SUCCESSFULLY(prefixed()
                               .color(GRAY)
                               .append(join(space(),
                                            text("World"),
                                            text("{0}", AQUA),
                                            text("has been unscheduled for reset")))),

  WASNT_SCHEDULED(prefixed()
                      .color(GRAY)
                      .append(join(space(),
                                   text("World"),
                                   text("{0}", AQUA),
                                   text("was not scheduled")))),

  RESETTING_NOW(prefixed()
                    .color(GRAY)
                    .append(join(space(),
                                 text("Resetting"),
                                 text("{0}", AQUA),
                                 text("now!")))),

  LIST_SCHEDULED_RESETS_TITLE(prefixedLines(text("Worlds scheduled to reset:", WHITE),
                                            text()
                                                .color(GRAY)
                                                .append(join(text(" - "),
                                                             text("world"),
                                                             text("next reset"),
                                                             text("interval"))))),

  LIST_SCHEDULED_RESETS_ELEMENT(prefixed()
                                    .append(join(text(" - ", GRAY),
                                                 text("{0}", AQUA),
                                                 text("{1}", GREEN)
                                                     .hoverEvent(showText(text("{2}", WHITE))),
                                                 text("{3}", GREEN)
                                                     .hoverEvent(showText(text("{4}", WHITE)))))),

  LIST_SCHEDULED_RESETS_NO_ELEMENT(prefixed().append(text("There are no scheduled resets", GRAY))),

  ERROR_WHILE_SAVING(prefixedLines(text("There was an error while saving scheduled data", RED),
                                   text("Please check console for any errors", RED))),

  USAGE_TITLE(prefixed().append(text("Usages:", WHITE))),

  USAGES_COMMAND(text()
                     .append(text("/{0}", RED))
                     .hoverEvent(showText(text()
                                              .append(text("Click to run: ", WHITE))
                                              .append(text("/{0}", GRAY))))
                     .clickEvent(suggestCommand("/{0}"))),

  UNKNOWN_WORLD(prefixed()
                    .color(RED)
                    .append(join(space(),
                                 text("No world for name"),
                                 text("{0}", AQUA),
                                 text("was found")))),

  COMMAND_ERROR(prefixed().append(text("{0}", RED)));

  private static TextComponent.Builder prefixed() {
    return TextComponent.ofChildren(PREFIX.component, space().color(WHITE)).toBuilder();
  }

  private static TextComponent prefixedLines(final ComponentLike... components) {
    return prefixed().append(join(newline().append(prefixed()), components)).build();
  }

  private static final Pattern INDEX_PATTERN = Pattern.compile("\\{(-1|\\d+)}");

  private final Component component;

  Message(final ComponentLike componentLike) {
    this.component = componentLike.asComponent();
  }

  public String legacy() {
    return LegacyComponentSerializer.legacySection().serialize(this.component);
  }

  public void send(final @NotNull MessagingSubject subject,
                   final @NotNull String @NotNull ... replacements) {
    subject.sendMessage(Identity.nil(), replaceWith(subject.getName(), replacements));
  }

  public void send(final @NotNull Audience audience,
                   final @NotNull String @NotNull ... replacements) {
    audience.sendMessage(Identity.nil(), replaceWith("INVALID", replacements));
  }

  private Component replaceWith(final String name, final String... replacements) {
    Component replaced = this.component.replaceText(
        TextReplacementConfig.builder().match(INDEX_PATTERN).replacement((matchResult, builder) -> {
          final int index = Integer.parseInt(matchResult.group(1));
          builder.content(index == -1 ? name : replacements[index]);
          return builder;
        }).build());

    final ClickEvent click = replaced.clickEvent();
    if (click != null) {
      final String newValue = Utils.replaceAll(INDEX_PATTERN, click.value(), matchResult -> {
        final int index = Integer.parseInt(matchResult.group(1));
        return index == -1 ? name : replacements[index];
      });
      replaced = replaced.clickEvent(suggestCommand(newValue));
    }

    return replaced;
  }
}
