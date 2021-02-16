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

package com.github.fefo.worldreset.config;

import com.github.fefo.worldreset.config.type.DurationConfigKey;
import com.github.fefo.worldreset.config.type.ListConfigKey;
import com.github.fefo.worldreset.config.type.StringConfigKey;
import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ConfigKeys {

  public static final ConfigKey<Duration> DEFAULT_RESET_INTERVAL =
      new DurationConfigKey("default-reset-interval", Duration.of(30L, ChronoUnit.DAYS), true);

  public static final ConfigKey<String> BROADCAST_MESSAGE =
      new StringConfigKey("broadcast-message", "&7Outer end islands will be reset in &a{time-left}", true);

  public static final ConfigKey<List<String>> BROADCAST_PRIOR_RESET =
      new ListConfigKey<>("broadcast-prior-reset",
                          ImmutableList.of("24hs", "12hs", "6hs", "3hs", "2hs", "1hs", "30min", "15min", "10min", "5min", "1min", "30s"),
                          false);
}
