package com.github.fefo.worldreset;

import com.github.fefo.worldreset.commands.WorldResetCommand;
import com.github.fefo.worldreset.config.YamlConfigAdapter;
import com.github.fefo.worldreset.messages.SubjectFactory;
import com.github.fefo.worldreset.work.WorldsDataHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class WorldResetPlugin extends JavaPlugin {

  private final YamlConfigAdapter configAdapter = new YamlConfigAdapter(this);
  private WorldsDataHandler worldsDataHandler;
  private SubjectFactory subjectFactory;
  private WorldResetCommand worldResetCommand;

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
    } catch (final IOException exception) {
      throw new RuntimeException(exception);
    }

    this.worldResetCommand = new WorldResetCommand(this);
  }

  @Override
  public void onDisable() {
    this.worldResetCommand.shutdown();
    this.worldsDataHandler.shutdown();

    try {
      this.worldsDataHandler.save();
    } catch (final IOException exception) {
      exception.printStackTrace();
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
}
