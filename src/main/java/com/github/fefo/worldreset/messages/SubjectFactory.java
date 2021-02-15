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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class SubjectFactory {

  private static final UUID ZERO = new UUID(0L, 0L);

  private final LoadingCache<CommandSender, MessagingSubject> subjectsCache =
      CacheBuilder.newBuilder()
                  .weakKeys()
                  .expireAfterWrite(5, TimeUnit.MINUTES)
                  .expireAfterAccess(5, TimeUnit.MINUTES)
                  .build(CacheLoader.from(sender -> {
                    return new MessagingSubject(uuidFrom(sender), audienceFrom(sender),
                                                nameFrom(sender), sender::hasPermission,
                                                worldSupplierFrom(sender));
                  }));
  private final BukkitAudiences audiences;

  public SubjectFactory(final Plugin plugin) {
    this.audiences = BukkitAudiences.create(plugin);
  }

  public void cleanup() {
    this.subjectsCache.invalidateAll();
  }

  public MessagingSubject from(final CommandSender sender) {
    return this.subjectsCache.getUnchecked(sender);
  }

  public Audience permission(final String permission) {
    return this.audiences.permission(permission);
  }

  private Audience audienceFrom(final CommandSender sender) {
    if (sender == null) {
      return this.audiences.console();
    }
    return this.audiences.sender(sender);
  }

  private UUID uuidFrom(final CommandSender sender) {
    if (!(sender instanceof Player)) {
      return ZERO;
    }
    return ((Player) sender).getUniqueId();
  }

  private String nameFrom(final CommandSender sender) {
    if (sender == null) {
      return "INVALID";
    }
    return sender.getName();
  }

  private Supplier<? extends World> worldSupplierFrom(final CommandSender sender) {
    if (!(sender instanceof Player)) {
      return null;
    }
    return ((Player) sender)::getWorld;
  }
}
