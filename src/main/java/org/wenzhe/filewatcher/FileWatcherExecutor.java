package org.wenzhe.filewatcher;

import static java.util.stream.Collectors.partitioningBy;

import java.nio.file.Paths;
import java.util.List;

import org.wenzhe.filewatcher.dsl.FileType;
import org.wenzhe.filewatcher.dsl.FileWatcherDslContext;
import org.wenzhe.filewatcher.dsl.Filter;
import org.wenzhe.filewatcher.dsl.FilterType;
import org.wenzhe.filewatcher.dsl.UpdateType;
import org.wenzhe.filewatcher.dsl.Watcher;

import lombok.val;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class FileWatcherExecutor {
  
  public static Subscription execute(Action1<FileWatcherDslContext> dslContextInitializer) {
    return run(dslContextInitializer).subscribe();
  }
  
  public static Observable<FileWatchEvent> run(Action1<FileWatcherDslContext> dslContextInitializer) {
    val ctx = new FileWatcherDslContext();
    dslContextInitializer.call(ctx);
    return run(ctx);
  }
  
  public static Observable<FileWatchEvent> run(FileWatcherDslContext ctx) {
    return Observable.from(ctx.getWatchers())
    .filter(watcher -> watcher.isStart())
    .flatMap(FileWatcherExecutor::run);
  }
  
  public static Observable<FileWatchEvent> run(Watcher watcher) {
    val groupedFilters = watcher.getFilters().stream()
    .collect(partitioningBy(filter -> filter.getFilterType() == FilterType.INCLUDE));
    val includeFilters = groupedFilters.get(true);
    val excludeFilters = groupedFilters.get(false);
    
    Observable<FileWatchEvent> fwe = new FileWatcher(
        Paths.get(watcher.getWatchedFile()), watcher.isRecursively())
    .asObservable()
    .filter(evt -> includeFilters.isEmpty() 
        || includeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)))
    .filter(evt -> !excludeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)));
    
    for (val handler : watcher.getHandlers()) {
      fwe = fwe.doOnNext(evt -> {
        if (isFileTypeMatch(evt, handler.getFileTypes())
            && handler.getUpdateType().match(evt)) {
          handler.getCode().call(evt.getPath().toString(), 
             UpdateType.from(evt).toString().toLowerCase());
        }
      });
    }
    return fwe;
  }
  
  private static boolean matchFilter(FileWatchEvent evt, Filter filter) {
    return isFileTypeMatch(evt, filter.getFileTypes()) && filter.filter(evt);
  }

  private static boolean isFileTypeMatch(FileWatchEvent evt, List<FileType> fileTypes) {
    return (evt.isFile() && fileTypes.contains(FileType.FILE))
        || (evt.isDirectory() && fileTypes.contains(FileType.FOLDER))
        || evt.isDeleted();
  }
}


