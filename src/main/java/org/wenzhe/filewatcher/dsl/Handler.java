package org.wenzhe.filewatcher.dsl;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import rx.functions.Action1;
import rx.functions.Action2;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Slf4j
public class Handler {
  
  @Getter private final Watcher watcher;
  @Getter private final List<FileType> fileTypes = new ArrayList<>();

  @Getter private UpdateType updateType;
  
  @Getter private Action2<String, String> code;
  
  public Handler(Watcher watcher, FileType fileType) {
    this.watcher = watcher;
    fileTypes.add(fileType);
  }
  
  public Handler and(FileType fileType) {
    log.debug("and {}", fileType);
    fileTypes.add(fileType);
    return this;
  }
  
  private Watcher execute1(Action1<String> code) {
    return execute((updatedFile, updatedType) -> code.call(updatedFile));
  }
  
  private Watcher execute(Action2<String, String> code) {
    this.code = code;
    return watcher;
  }
  
  public Watcher updated(Action2<String, String> code) {
    updateType = UpdateType.UPDATED;
    log.debug(updateType.toString());
    return execute(code);
  }
  
  public Watcher modified(Action1<String> code) {
    updateType = UpdateType.MODIFIED;
    log.debug(updateType.toString());
    return execute1(code);
  }
  
  public Watcher created(Action1<String> code) {
    updateType = UpdateType.CREATED;
    log.debug(updateType.toString());
    return execute1(code);
  }
  
  public Watcher deleted(Action1<String> code) {
    updateType = UpdateType.DELETED;
    log.debug(updateType.toString());
    return execute1(code);
  }
}
