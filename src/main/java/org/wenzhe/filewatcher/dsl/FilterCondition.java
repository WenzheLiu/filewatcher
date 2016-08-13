package org.wenzhe.filewatcher.dsl;

import java.nio.file.Path;

import org.wenzhe.filewatcher.FileWatchEvent;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public interface FilterCondition {
  
  default boolean filter(FileWatchEvent evt, Filter filter) {
    return filter(evt.getPath(), filter.getNameOrPath());
  }

  default boolean filter(Path path, NamePath namePath) {
    return filter(namePath == NamePath.NAME ? path.getFileName().toString() : path.toString());
  }
  
  boolean filter(String nameOrPath);

}
