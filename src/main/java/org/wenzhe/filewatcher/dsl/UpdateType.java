package org.wenzhe.filewatcher.dsl;

import java.util.Arrays;
import java.util.function.Predicate;

import org.wenzhe.filewatcher.FileWatchEvent;
import org.wenzhe.filewatcher.FileWatcherException;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public enum UpdateType {
  CREATED(evt -> evt.isCreated()), 
  MODIFIED(evt -> evt.isModified()), 
  DELETED(evt -> evt.isDeleted()), 
  UPDATED(evt -> true); // include all
  
  private final Predicate<FileWatchEvent> matcher;
  
  private UpdateType(Predicate<FileWatchEvent> matcher) {
    this.matcher = matcher;
  }
  
  public boolean match(FileWatchEvent evt) {
    return matcher.test(evt);
  }
  
  public static UpdateType from(FileWatchEvent evt) {
    return Arrays.stream(values())
        .filter(it -> it.match(evt))
        .findFirst()
        .orElseThrow(() -> new FileWatcherException("Cannot find event kind " + evt.getKind()));
  }
}
