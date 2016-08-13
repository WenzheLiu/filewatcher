package org.wenzhe.filewatcher.dsl;

import java.nio.file.Path;

import org.wenzhe.filewatcher.FileWatchEvent;

import lombok.Value;
import rx.functions.Func2;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class ConditionFilter implements FilterCondition {
  
  private Func2<String, String, Boolean> condition;
  
  @Override
  public boolean filter(FileWatchEvent evt, Filter filter) {
    return condition.call(evt.getPath().toString(), 
        UpdateType.from(evt).toString().toLowerCase());
  }
  
  @Override
  public boolean filter(Path path, NamePath namePath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean filter(String nameOrPath) {
    throw new UnsupportedOperationException();
  }

  
}
