package com.github.fefo.worldreset;

import com.github.fefo.worldreset.commands.DurationArgumentType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {

  public static final Pattern TIME_LEFT_PATTERN = Pattern.compile("\\{time-left}");
  public static final Pattern TIME_LEFT_LONG_PATTERN = Pattern.compile("\\{time-left-long}");
  public static final Pattern WORLD_NAME_PATTERN = Pattern.compile("\\{world}");
  private static final DurationArgumentType DURATION_PARSER = DurationArgumentType.duration();
  private static final LegacyComponentSerializer LEGACY_AMPERSAND =
      LegacyComponentSerializer.legacyAmpersand();
  private static final LegacyComponentSerializer LEGACY_SECTION =
      LegacyComponentSerializer.legacySection();

  public static @NotNull String toLegacy(final @NotNull Component component) {
    return LEGACY_SECTION.serialize(component);
  }

  public static @NotNull Component fromLegacy(final @NotNull String message) {
    return LEGACY_AMPERSAND.deserialize(message);
  }

  public static @NotNull String replaceAll(
      final @NotNull Pattern pattern,
      final @NotNull String base,
      final @NotNull Function<? super MatchResult, ? extends String> replacer) {
    final Matcher matcher = pattern.matcher(base);
    if (matcher.find()) {
      final StringBuffer buffer = new StringBuffer();
      do {
        matcher.appendReplacement(buffer, replacer.apply(matcher));
      } while (matcher.find());
      matcher.appendTail(buffer);
      return buffer.toString();
    }
    return base;
  }

  public static @NotNull Duration parseDuration(final @NotNull String input) {
    try {
      return DURATION_PARSER.parse(new StringReader(input));
    } catch (final CommandSyntaxException exception) {
      return Duration.ZERO;
    }
  }

  public static @NotNull String shortDuration(final @NotNull Duration duration) {
    Validate.notNull(duration);

    final StringJoiner joiner = new StringJoiner("");

    long years = duration.getSeconds();
    long months = years % seconds(ChronoUnit.YEARS);
    long weeks = months % seconds(ChronoUnit.MONTHS);
    long days = weeks % seconds(ChronoUnit.WEEKS);
    long hours = days % seconds(ChronoUnit.DAYS);
    long minutes = hours % seconds(ChronoUnit.HOURS);
    final long seconds = minutes % seconds(ChronoUnit.MINUTES);

    years /= seconds(ChronoUnit.YEARS);
    months /= seconds(ChronoUnit.MONTHS);
    weeks /= seconds(ChronoUnit.WEEKS);
    days /= seconds(ChronoUnit.DAYS);
    hours /= seconds(ChronoUnit.HOURS);
    minutes /= seconds(ChronoUnit.MINUTES);

    if (years != 0) {
      joiner.add(years + "y");
    }
    if (months != 0) {
      joiner.add(months + "mo");
    }
    if (weeks != 0) {
      joiner.add(weeks + "w");
    }
    if (days != 0) {
      joiner.add(days + "d");
    }
    if (hours != 0) {
      joiner.add(hours + "h");
    }
    if (minutes != 0) {
      joiner.add(minutes + "m");
    }
    if (seconds != 0) {
      joiner.add(seconds + "s");
    }

    return joiner.toString();
  }

  public static @NotNull String longDuration(final @NotNull Duration duration) {
    Validate.notNull(duration);

    final StringJoiner joiner = new StringJoiner(", ");

    long years = duration.getSeconds();
    long months = years % seconds(ChronoUnit.YEARS);
    long weeks = months % seconds(ChronoUnit.MONTHS);
    long days = weeks % seconds(ChronoUnit.WEEKS);
    long hours = days % seconds(ChronoUnit.DAYS);
    long minutes = hours % seconds(ChronoUnit.HOURS);
    final long seconds = minutes % seconds(ChronoUnit.MINUTES);

    years /= seconds(ChronoUnit.YEARS);
    months /= seconds(ChronoUnit.MONTHS);
    weeks /= seconds(ChronoUnit.WEEKS);
    days /= seconds(ChronoUnit.DAYS);
    hours /= seconds(ChronoUnit.HOURS);
    minutes /= seconds(ChronoUnit.MINUTES);

    if (years != 0) {
      joiner.add(years + " year" + (years > 1 ? "s" : ""));
    }
    if (months != 0) {
      joiner.add(months + " month" + (months > 1 ? "s" : ""));
    }
    if (weeks != 0) {
      joiner.add(weeks + " week" + (weeks > 1 ? "s" : ""));
    }
    if (days != 0) {
      joiner.add(days + " day" + (days > 1 ? "s" : ""));
    }
    if (hours != 0) {
      joiner.add(hours + " hour" + (hours > 1 ? "s" : ""));
    }
    if (minutes != 0) {
      joiner.add(minutes + " minute" + (minutes > 1 ? "s" : ""));
    }
    if (seconds != 0) {
      joiner.add(seconds + " second" + (seconds > 1 ? "s" : ""));
    }

    return joiner.toString();
  }

  private static long seconds(final ChronoUnit chronoUnit) {
    return chronoUnit.getDuration().getSeconds();
  }

  private Utils() {
    throw new UnsupportedOperationException();
  }
}
