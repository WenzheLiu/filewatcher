package org.wenzhe.filewatcher.edd.rx2;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

@Value
class FileWatchEvent {
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

class FileWatcherException extends RuntimeException {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  public FileWatcherException() {
    super();
    // TODO Auto-generated constructor stub
  }

  public FileWatcherException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // TODO Auto-generated constructor stub
  }

  public FileWatcherException(String message, Throwable cause) {
    super(message, cause);
    // TODO Auto-generated constructor stub
  }

  public FileWatcherException(String message) {
    super(message);
    // TODO Auto-generated constructor stub
  }

  public FileWatcherException(Throwable cause) {
    super(cause);
    // TODO Auto-generated constructor stub
  }
}

@ThreadSafe
class FileWatchService implements AutoCloseable {
  
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
    val isWatchDir = Files.isDirectory(path);
    val pathToWatch = isWatchDir ? path : path.getParent();
 
    val obsvPath = isWatchDir && recursively ? 
        Observable.from(listDirsRecursively(pathToWatch)) : Observable.just(pathToWatch);
    
    val watchService = new FileWatchService();

    Observable<FileWatchEvent> obsv = obsvPath.reduce(watchService, (watcher, path) -> watcher.register(path))
    .flatMap(watcher -> Observable.create(subscriber -> watchFile(watcher, subscriber)));
    
    long timeout = 1500; // ms
    Cache<Path, Long> pathLastModifiedTime = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, TimeUnit.MILLISECONDS)
        .build();
    
    return obsv.filter(event -> 
      isWatchDir || path.equals(event.getPath())
    )
    .filter(event ->
      event.exists() || event.isDeleted()
    )
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

  public static void main(String[] args) {
    val root = Paths.get("E:/wenzhe");
    val fileWatcher = new FileWatcher(root, true).asObservable();
    val subscription = fileWatcher.filter(event -> event.isFile() || !event.isModified())
        .doOnNext(event -> System.out.println("subscribeOn1 thread " + Thread.currentThread().getName()))
        .subscribeOn(Schedulers.newThread())
        .doOnNext(event -> System.out.println("subscribeOn2 thread " + Thread.currentThread().getName()))
        .observeOn(Schedulers.newThread())
        .subscribe(new Subscriber<FileWatchEvent>() {

      @Override
      public void onCompleted() {
        System.out.println("onCompleted called");
      }

      @Override
      public void onError(Throwable e) {
        System.out.println("onError called");
        e.printStackTrace();
      }

      @Override
      public void onNext(FileWatchEvent event) {
        System.out.println(event);
        System.out.println("observeOn thread " + Thread.currentThread().getName());
      }
    });
    
    // random to receive with the above first subscription
    fileWatcher.subscribeOn(Schedulers.io())
    .observeOn(Schedulers.newThread())
    .subscribe(event -> System.out.println("another " + event));
    
    val o = Observable.just(1, 2, 3, 4, 5);
    o.subscribe(i -> System.out.println(i));
    o.subscribe(i -> System.out.println(-i));
    
    while (!subscription.isUnsubscribed()) {
      try {
        if (System.in.read() == 'a') {
          subscription.unsubscribe();
        }
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
    }
    // sleep for wait for closed
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    //while (true);
  }

}
