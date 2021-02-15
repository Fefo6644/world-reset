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
