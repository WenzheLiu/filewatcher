package org.wenzhe.filewatcher.dsl;

import java.util.Arrays;

import lombok.Value;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class EndFilter implements FilterCondition {
  String[] values;
  boolean ignoreCase;
  
  @Override
  public boolean filter(String nameOrPath) {
    return Arrays.stream(values).anyMatch(it ->
      ignoreCase ? nameOrPath.toLowerCase().endsWith(it.toLowerCase())
      : nameOrPath.endsWith(it.toLowerCase()));
  }
}
