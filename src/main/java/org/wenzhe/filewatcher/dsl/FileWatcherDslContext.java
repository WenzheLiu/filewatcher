package org.wenzhe.filewatcher.dsl;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Slf4j
@Getter
public class FileWatcherDslContext {
      
  public static final boolean recursively = true;
  public static final boolean to = !recursively;
  
  public static final FileType file = FileType.FILE;
  public static final FileType folder = FileType.FOLDER;
  public static final FilterType include = FilterType.INCLUDE;
  public static final FilterType exclude = FilterType.EXCLUDE;
  public static final NamePath name = NamePath.NAME;
  public static final NamePath path = NamePath.PATH;
  
  public static final boolean sensitive = false;
  public static final boolean insensitive = !sensitive;

  private final List<Watcher> watchers = new ArrayList<>();
  
  public Watcher start(boolean recursively) {
    if (log.isDebugEnabled()) {
      log.debug("start {}to", recursively ? "recursively " : "");
    }
    val w = new Watcher();
    w.setRecursively(recursively);
    w.setStart(true);
    watchers.add(w);
    return w;
  }
  
  public Watcher stop(boolean to) {
    log.debug("stop to");
    val w = new Watcher();
    w.setStart(false);
    watchers.add(w);
    return w;
  }
  
  public static Subscription async(Action0 action) {
    return Schedulers.io().createWorker().schedule(action);
  }
}
