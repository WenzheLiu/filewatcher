package org.wenzhe.filewatcher.dsl;

import java.util.Arrays;

import lombok.Value;
import lombok.val;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class ExtensionFilter implements FilterCondition {
  private String[] extensions;

  @Override
  public boolean filter(String name) {
    val extName = getExtension(name);
    return Arrays.stream(extensions).anyMatch(extName::equalsIgnoreCase);
  }
  
  private String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index == -1 ? "" : fileName.substring(index + 1);
  }
}
