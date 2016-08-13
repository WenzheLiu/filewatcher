package org.wenzhe.filewatcher.dsl;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.Getter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Slf4j
@Data
public class Watcher {
  
  private boolean start;
  private boolean recursively;
  private String watchedFile = "";
  
  @Getter private final List<Handler> handlers = new ArrayList<>();
  @Getter private final List<Filter> filters = new ArrayList<>();
  
  public Watcher watch(String path) {
    log.debug("watch {}", path);
    watchedFile = path;
    return this;
  }
  
  public Handler on(FileType fileType) {
    log.debug("on {}", fileType);
    val handler = new Handler(this, fileType);
    handlers.add(handler);
    return handler;
  }
  
  public Filter filter(FilterType filterType) {
    log.debug("filter {}", filterType);
    val ft = new Filter(this, filterType);
    filters.add(ft);
    return ft;
  }
}
