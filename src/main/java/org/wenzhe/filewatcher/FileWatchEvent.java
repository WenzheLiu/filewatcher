package org.wenzhe.filewatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;

import lombok.Value;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class FileWatchEvent {
  Path path;
  Kind<?> kind;
  
  public boolean isModified() {
    return kind == StandardWatchEventKinds.ENTRY_MODIFY;
  }
  
  public boolean isCreated() {
    return kind == StandardWatchEventKinds.ENTRY_CREATE;
  }
  
  public boolean isDeleted() {
    return kind == StandardWatchEventKinds.ENTRY_DELETE;
  }
  
  public boolean exists() {
    return Files.exists(path);
  }
  
  public boolean isDirectory() {
    return Files.isDirectory(path);
  }
  
  public boolean isFile() {
    return exists() && !isDirectory();
  }
}