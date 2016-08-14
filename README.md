File Watcher是一个轻量级的Java第三方库。它底层封装了Java 7中的WatchService，但更加容易使用，而且更加强大，支持监控文件和文件夹，可配置递归遍历，可以设置多种复杂的过滤条件，异步监控而不阻塞当前线程，支持多种复杂的回调响应，可以异步执行代码。

# 作为一个轻量级的Java第三方库
org.wenzhe.filewatcher可以作为第三方库方便用于你的Java应用中，非常轻量级，它大小只有36KB，跨平台，它不依赖于Groovy，只依赖常用的jar，如下：

![org.wenzhe.filewatcher的依赖包](http://img.blog.csdn.net/20160811181920194)

依赖的库也很常用很轻量级，而且都是开源免费可商用的license。使用方法可以是：
下面是一个使用`org.wenzhe.filewatcher`的例子，如下：
``` java
public class FileWatcherTest1 {

  public static void main(String[] args) {
    FileWatcherExecutor.execute(ctx -> {

      ctx.start(recursively).watch("E:/wenzhe/aa")
      .filter(include).extension("txt")
      .filter(exclude).file(name).contains("123")
      .on(file).modified(updatedFile ->
        async(() -> {
          System.out.printf("file %s modifieddddd\n", updatedFile);
        })
      )
      .on(file).modified(updatedFile -> {
        System.out.println("open notepad");
        try {
          Runtime.getRuntime().exec(String.format("notepad '%s'", updatedFile));
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
      
      ctx.start(to).watch("E:/wenzhe/bb")
      .on(file).updated((updatedFile, updatedType) -> {
        System.out.printf("file %s %s\n", updatedFile, updatedType);
      });
    });
    
    while (true) {
      
    }
  }

}
```
# 设计文档
1. 需求分析：[基于领域特定语言（DSL）的用例驱动开发（UDD）](http://blog.csdn.net/liuwenzhe2008/article/details/52184910)
2. 技术实现：[实验驱动开发与响应式编程 ---- File Watcher的技术实现](http://blog.csdn.net/liuwenzhe2008/article/details/52185447)


 ---------------------- 本博客所有内容均为原创，转载请注明作者和出处 -----------------------
 
 作者：刘文哲

 联系方式：liuwenzhe2008@qq.com

 博客：http://blog.csdn.net/liuwenzhe2008
