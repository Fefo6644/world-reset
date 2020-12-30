package com.github.fefo.worldreset.config;

import com.github.fefo.worldreset.config.type.DurationConfigKey;
import com.github.fefo.worldreset.config.type.ListConfigKey;
import com.github.fefo.worldreset.config.type.StringConfigKey;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ConfigKeys {

  public static final ConfigKey<Duration> DEFAULT_RESET_INTERVAL =
      new DurationConfigKey("default-reset-interval", Duration.of(30L, ChronoUnit.DAYS), true);

  public static final ConfigKey<String> BROADCAST_MESSAGE =
      new StringConfigKey("broadcast-message",
                          "&7Outer end islands will be reset in &a{time-left}",
                          true);

  public static final ConfigKey<String> KICK_MESSAGE =
      new StringConfigKey("kick-message",
                          "&7Outer end islands are currently resetting. " +
                          "&oPlease wait a minute before rejoining!",
                          true);

  public static final ConfigKey<List<String>> BROADCAST_PRIOR_RESET =
      new ListConfigKey<>("broadcast-prior-reset",
                          Collections.unmodifiableList(Arrays.asList(
                              "24hs", "12hs", "6hs", "3hs", "2hs", "1hs", "30min", "15min", "10min",
                              "5min", "1min", "30s")),
                          false);
}
