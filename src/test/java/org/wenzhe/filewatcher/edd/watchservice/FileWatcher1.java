package org.wenzhe.filewatcher.edd.watchservice;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * @author wen-zhe.liu@asml.com
 *
 */
public class FileWatcher1 {
  
  private static List<Path> getDirsToWatch(Path path) throws IOException {
    List<Path> dirsToWatch = new ArrayList<>(); 
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        dirsToWatch.add(dir);
        return FileVisitResult.CONTINUE;
      }
    });
    return dirsToWatch;
  }

  /**
   * @param args
   * @throws IOException 
   * @throws InterruptedException 
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
    
      Path root = Paths.get("E:/wenzhe");
      
      List<Path> dirsToWatch = getDirsToWatch(root);
      
      Map<Path, WatchKey> pathWatchKeyMap = new HashMap<>();
      
      for (Path path : dirsToWatch) {
        WatchKey watchKey = path.register(watcher,   
            StandardWatchEventKinds.ENTRY_CREATE,  
            StandardWatchEventKinds.ENTRY_DELETE,  
            StandardWatchEventKinds.ENTRY_MODIFY);
        pathWatchKeyMap.put(path, watchKey);
      }
      
      Map<Kind<?>, String> fileUpdateTypes = ImmutableMap.of(
          StandardWatchEventKinds.ENTRY_CREATE, "created",
          StandardWatchEventKinds.ENTRY_DELETE, "deleted",
          StandardWatchEventKinds.ENTRY_MODIFY, "modified");
  
      while (true) {  
        WatchKey key = watcher.take();  
        for (WatchEvent<?> event: key.pollEvents()) {  
          Path watchablePath = (Path) key.watchable();
          Path path = watchablePath.resolve((Path) event.context());
          Kind<?> kind = event.kind();
          
          String fileType = Files.isDirectory(path) ? "Folder" : "File";
          String updateType = fileUpdateTypes.getOrDefault(event.kind(), "unknown");
          System.out.printf("%s %s to %s\n", fileType, updateType, path.toString());  
          
          if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
            WatchKey watchKey = path.register(watcher,   
                StandardWatchEventKinds.ENTRY_CREATE,  
                StandardWatchEventKinds.ENTRY_DELETE,  
                StandardWatchEventKinds.ENTRY_MODIFY); 
            pathWatchKeyMap.put(path, watchKey);
          } else if (kind == StandardWatchEventKinds.ENTRY_DELETE && !Files.exists(path)) {
            WatchKey watchKey = pathWatchKeyMap.get(path);
            if (watchKey != null) {
              pathWatchKeyMap.remove(path);
              watchKey.cancel();
              System.out.println("watchKey.cancel()");
            }
          }
        }  
  
        System.out.println("key.isValid(): " + key.isValid());
        if (key.isValid() && !key.reset()) {  
          System.out.println("!key.reset()");
          break;  
        }  
      }
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      System.out.println("Exit");
    }
  }

}
