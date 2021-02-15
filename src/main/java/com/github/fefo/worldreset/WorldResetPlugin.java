package com.github.fefo.worldreset;

import com.github.fefo.worldreset.commands.WorldResetCommand;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.work.WorldsDataHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class WorldResetPlugin extends JavaPlugin {

  public static final Logger LOGGER = LoggerFactory.getLogger(WorldResetPlugin.class);

  private final Path pluginDataFolder = getDataFolder().toPath();
  private final YamlConfigAdapter configAdapter = new YamlConfigAdapter(this);
  private WorldsDataHandler worldsDataHandler;
  private SubjectFactory subjectFactory;
  private WorldResetCommand worldResetCommand;

  public Path getPluginDataFolder() {
    return this.pluginDataFolder;
  }

  public SubjectFactory getSubjectFactory() {
    return this.subjectFactory;
  }

  public YamlConfigAdapter getConfigAdapter() {
    return this.configAdapter;
  }

  public WorldsDataHandler getWorldsDataHandler() {
    return this.worldsDataHandler;
  }

  @Override
  public void onLoad() {
    try {
      this.configAdapter.load();
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  @Override
  public void onEnable() {
    this.subjectFactory = new SubjectFactory(this);

    try {
      this.worldsDataHandler = new WorldsDataHandler(this);
      this.worldsDataHandler.load();
      this.worldsDataHandler.deleteAny();
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }

    this.worldResetCommand = new WorldResetCommand(this);
  }

  @Override
  public void onDisable() {
    this.worldResetCommand.shutdown();

    try {
      this.worldsDataHandler.save();
    } catch (final IOException exception) {
      exception.printStackTrace();
    } finally {
      this.worldsDataHandler.shutdown();
      this.subjectFactory.cleanup();
    }
  }

  @Override
  public void reloadConfig() {
    try {
      this.configAdapter.reload();
    } catch (final IOException exception) {
      exception.printStackTrace();
    }
  }

  @Override
  public @NotNull Logger getSLF4JLogger() {
    return LOGGER;
  }
}
