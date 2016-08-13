package org.wenzhe.filewatcher.dsl;

import java.util.regex.Pattern;

import lombok.Value;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class MatchFilter implements FilterCondition {
  Pattern pattern;
  
  @Override
  public boolean filter(String nameOrPath) {
    return pattern.matcher(nameOrPath).matches();
  }
}
