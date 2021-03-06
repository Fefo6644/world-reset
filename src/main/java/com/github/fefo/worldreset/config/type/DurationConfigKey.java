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

package com.github.fefo.worldreset.config.type;

import com.github.fefo.worldreset.commands.DurationArgumentType;
import com.github.fefo.worldreset.config.ConfigKey;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class DurationConfigKey extends ConfigKey<Duration> {

  private static final DurationArgumentType DURATION_PARSER = DurationArgumentType.duration();

  public DurationConfigKey(final @NotNull String key, final @NotNull Duration fallback,
                           final boolean reloadable) {
    super(key, fallback, reloadable);
  }

  @Override
  public @NotNull Duration get(final @NotNull YamlConfigAdapter configAdapter) {
    final String raw = configAdapter.getString(this.key);
    try {
      return raw == null ? this.fallback : DURATION_PARSER.parse(new StringReader(raw));
    } catch (final CommandSyntaxException exception) {
      exception.printStackTrace();
      return this.fallback;
    }
  }
}
