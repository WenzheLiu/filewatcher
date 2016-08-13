package org.wenzhe.filewatcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

import lombok.SneakyThrows;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class FileWatchService implements AutoCloseable {
  
  private volatile WatchService watchService;
  
  private final Map<Path, WatchKey> pathWatchKeyMap = new HashMap<>();
  
  @SneakyThrows
  public WatchService getWatchService() {
    if (watchService == null) {
      synchronized (this) {
        if (watchService == null) {
          watchService = FileSystems.getDefault().newWatchService();
        }
      }
    }
    return watchService;
  }
  
  @SneakyThrows(IOException.class)
  public synchronized FileWatchService register(Path path) {
    pathWatchKeyMap.put(path, path.register(getWatchService(),   
        StandardWatchEventKinds.ENTRY_CREATE,  
        StandardWatchEventKinds.ENTRY_DELETE,  
        StandardWatchEventKinds.ENTRY_MODIFY));
    return this;
  }

  @Override
  @SneakyThrows
  public synchronized void close() {
    if (watchService != null) {
      watchService.close();
      watchService = null;
    }
  }

  @SneakyThrows
  public WatchKey take() {
    return getWatchService().take();
  }

  public synchronized void cancel(Path path) {
    WatchKey watchKey = pathWatchKeyMap.get(path);
    if (watchKey != null) {
      pathWatchKeyMap.remove(path);
      watchKey.cancel();
    }
  }
}
