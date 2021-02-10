package com.github.fefo.worldreset.commands;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class CommandMapHelper {

  private static final Method BUKKIT_GET_COMMAND_MAP;

  static {
    try {
      final Class<? extends Server> serverClass = Bukkit.getServer().getClass();
      BUKKIT_GET_COMMAND_MAP = serverClass.getDeclaredMethod("getCommandMap");
    } catch (final NoSuchMethodException exception) {
      throw new RuntimeException(exception);  // no bueno
    }
  }

  public static @NotNull CommandMap getCommandMap() {
    try {
      return (CommandMap) BUKKIT_GET_COMMAND_MAP.invoke(Bukkit.getServer());
    } catch (final IllegalAccessException | InvocationTargetException exception) {
      throw new RuntimeException(exception);  // no bueno
    }
  }
}
