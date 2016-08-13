package org.wenzhe.filewatcher;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class FileWatcher {

  Path path;
  boolean recursively;
  
  /**
   * include the root path itself
   */
  private static List<Path> listDirsRecursively(Path path) throws IOException {
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

  @SneakyThrows
  public Observable<FileWatchEvent> asObservable() {
    if (!Files.exists(path)) {
      throw new FileWatcherException("not exist path " + path);
    }
    
    long timeout = 1500; // ms
    Cache<Path, Long> pathLastModifiedTime = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, TimeUnit.MILLISECONDS)
        .build();
    
    val isWatchDir = Files.isDirectory(path);
    val pathToWatch = isWatchDir ? path : path.getParent();
 
    val obsvPath = isWatchDir && recursively ? 
        Observable.from(listDirsRecursively(pathToWatch)) : Observable.just(pathToWatch);
    
    val watchService = new FileWatchService();

    return obsvPath.reduce(watchService, (watcher, path) -> watcher.register(path))
    .flatMap(watcher -> 
      Observable.create(subscriber -> watchFile(watcher, subscriber))
      .subscribeOn(Schedulers.io())
      .cast(FileWatchEvent.class)
    )
    .filter(event -> isWatchDir || path.equals(event.getPath()))
    .filter(event -> event.exists() || event.isDeleted())
    .filter(event -> skipDuplicateEvent(pathLastModifiedTime, event, timeout))
    .doOnNext(event -> updateWatchService(watchService, event))
    .doAfterTerminate(() -> watchService.close());
  }

  private void updateWatchService(final FileWatchService watchService, FileWatchEvent event) {
    if (event.isCreated() && event.isDirectory()) {
      watchService.register(event.getPath());
    } else if (event.isDeleted() && !event.exists()) {
      watchService.cancel(event.getPath());
    }
  }

  /**
   * fix Java's watch service issue that modify file event will send 2 to 3 times
   */
  private boolean skipDuplicateEvent(Cache<Path, Long> pathLastModifiedTimeCache, 
      FileWatchEvent event, long periodInMilliSecond) {

    if (event.isModified() && event.isFile()) {
      val path = event.getPath();
      val now = System.currentTimeMillis();
      synchronized (this) {
        val lastModifiedTime = pathLastModifiedTimeCache.getIfPresent(path);
        if (lastModifiedTime == null || now - lastModifiedTime > periodInMilliSecond) {
          pathLastModifiedTimeCache.put(path, now);
          return true;
        } else {
          return false;
        }
      }
    } else {
      return true;
    }
  }

  private void watchFile(FileWatchService watcher, Subscriber<? super FileWatchEvent> subscriber) {
    try {
      while (!subscriber.isUnsubscribed()) {  
        val key = watcher.take();  
        for (val event : key.pollEvents()) {  
          val watchablePath = (Path) key.watchable();
          val path = watchablePath.resolve((Path) event.context());

          subscriber.onNext(new FileWatchEvent(path, event.kind()));
        }  
        if (key.isValid() && !key.reset()) {  
          break;  
        }
      }
      subscriber.onCompleted();
    } catch (Throwable e) {
      subscriber.onError(e);
    }
  }
}
