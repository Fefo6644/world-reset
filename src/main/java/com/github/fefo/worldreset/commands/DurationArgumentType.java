package com.github.fefo.worldreset.commands;

import com.github.fefo.worldreset.Utils;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationArgumentType implements ArgumentType<Duration> {

  private static final Map<String, ChronoUnit> SCALES;
  private static final List<String> SCALES_SUGGESTIONS;
  private static final Dynamic2CommandExceptionType DURATION_TOO_SMALL =
      new Dynamic2CommandExceptionType((found, min) -> {
        return new LiteralMessage("Duration must not be less than " + min + ", found " + found);
      });
  private static final Dynamic2CommandExceptionType DURATION_TOO_BIG =
      new Dynamic2CommandExceptionType((found, max) -> {
        return new LiteralMessage("Duration must not be more than " + max + ", found " + found);
      });
  private static final Collection<String> EXAMPLES =
      Collections.unmodifiableList(Arrays.asList("12d", "25mins", "8.5ys", "3mo5ws2days4.045secs"));
  private static final Pattern DURATION_PATTERN =
      Pattern.compile("^" +
                      "(?:(\\d+(?:\\.\\d+)?)(y)(?:ear)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(mo)(?:nth)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(w)(?:eek)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(d)(?:ay)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(h)(?:r|our)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(m)(?:in|inute)?s?)?" +
                      "(?:(\\d+(?:\\.\\d+)?)(?:(s)(?:ec|econd)?s?)?)?" +
                      "$");

  static {
    // Use a LinkedHashMap so they retain the order they were put into
    // That same order will be the one in which suggestions will appear
    // See #listSuggestions comments for a brief example
    final Map<String, ChronoUnit> tempMap = new LinkedHashMap<>(7);
    tempMap.put("y", ChronoUnit.YEARS);
    tempMap.put("mo", ChronoUnit.MONTHS);
    tempMap.put("w", ChronoUnit.WEEKS);
    tempMap.put("d", ChronoUnit.DAYS);
    tempMap.put("h", ChronoUnit.HOURS);
    tempMap.put("m", ChronoUnit.MINUTES);
    tempMap.put("s", ChronoUnit.SECONDS);

    SCALES = Collections.unmodifiableMap(tempMap);
    SCALES_SUGGESTIONS = Collections.unmodifiableList(new ArrayList<>(SCALES.keySet()));
    // This is where and why the order has to be retained
  }

  public static <S> Duration getDuration(final CommandContext<S> context, final String name) {
    return context.getArgument(name, Duration.class);
  }

  public static DurationArgumentType duration() {
    return new DurationArgumentType(null, null);
  }

  public static DurationArgumentType duration(final @Nullable Duration minimumInclusive) {
    return new DurationArgumentType(minimumInclusive, null);
  }

  public static DurationArgumentType duration(final @Nullable Duration minimumInclusive,
                                              final @Nullable Duration maximumExclusive) {
    return new DurationArgumentType(minimumInclusive, maximumExclusive);
  }

  private final Duration minimum;
  private final Duration maximum;

  private DurationArgumentType(final Duration minimum, final Duration maximum) {
    this.minimum = minimum;
    this.maximum = maximum;
  }

  public @Nullable Duration getMinimum() {
    return this.minimum;
  }

  public @Nullable Duration getMaximum() {
    return this.maximum;
  }

  @Override
  public Duration parse(final StringReader reader) throws CommandSyntaxException {
    final String input = reader.readString().toLowerCase(Locale.ROOT);
    if (input.isEmpty()) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                                                      .createWithContext(reader);
    }

    final Matcher matcher = DURATION_PATTERN.matcher(input);
    Duration duration = Duration.ZERO;

    if (matcher.find() && !matcher.group().isEmpty()) {
      for (int i = 1; i <= SCALES.size(); ++i) {
        final String current = matcher.group(i * 2 - 1);
        if (current == null) {
          continue;
        }

        // Defaults to seconds if no scale is provided (ergo: group is null)
        final ChronoUnit unit = SCALES.getOrDefault(matcher.group(i * 2), ChronoUnit.SECONDS);
        final double parsedCurrent = Double.parseDouble(current) * unit.getDuration().getSeconds();
        // Whacky workaround because you can't use ChronoUnits
        // larger than DAYS in Duration#plus(long, TemporalUnit)
        duration = duration.plus(Math.round(parsedCurrent), ChronoUnit.SECONDS);
      }
    } else {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                                                      .createWithContext(reader);
    }

    if (this.minimum != null && duration.compareTo(this.minimum) < 0) {
      throw DURATION_TOO_SMALL.createWithContext(reader, Utils.shortDuration(duration),
                                                 Utils.shortDuration(this.minimum));
    }

    if (this.maximum != null && duration.compareTo(this.maximum) >= 0) {
      throw DURATION_TOO_BIG.createWithContext(reader, Utils.shortDuration(duration),
                                               Utils.shortDuration(this.maximum));
    }

    return duration;
  }

  @Override
  public Collection<String> getExamples() {
    return EXAMPLES;
  }

  @Override
  public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context,
                                                            final SuggestionsBuilder builder) {
    final String current = builder.getRemaining().toLowerCase(Locale.ROOT);
    final boolean isQuoted = StringReader.isQuotedStringStart(current.charAt(0));
    final String toMatch = (isQuoted ? current.substring(1) : current).replaceAll("\\s", "");
    final Matcher matcher = DURATION_PATTERN.matcher(toMatch);

    if (matcher.find()) {
      int nullGroups = 0;
      String lastScale = null;
      // Reverse for loop because we'll take the last non-digit non-null group as base index
      // for taking the suggestions
      for (int i = matcher.groupCount(); i > 0; --i) {
        final String currentGroup = matcher.group(i);
        if (currentGroup == null) {
          ++nullGroups;
        } else if (SCALES.containsKey(currentGroup) && lastScale == null) {
          lastScale = currentGroup;
        }
      }

      // 123 -> [y, mo, w, d, h, m, s]
      // 123ws4 -> [d, h, m, s]
      // 123ws4h56 -> [m, s]
      // 123mo -> []
      if (nullGroups % 2 == 1) {
        final int index = SCALES_SUGGESTIONS.indexOf(lastScale) + 1;
        for (int i = index; i < SCALES_SUGGESTIONS.size(); ++i) {
          builder.suggest(current + SCALES_SUGGESTIONS.get(i));
        }
        return builder.buildFuture();
      }
    }

    return Suggestions.empty();
  }
}
