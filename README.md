File Watcher不只是一个工具，还是一种语言，同时又是一个轻量级的Java第三方库。
# 作为工具
## 简介
File Watcher是这样一个通用的命令行工具：

1. 监控文件（夹）变化，包括文件（夹）的创建、修改、删除
2. 文件变化时能够自动运行指定的命令
3. 方便用户配置要监控的文件（夹），能够定义丰富灵活的过滤规则
4. 每个用户都可以有自己的配置，不同用户的配置可以不相同
5. 被触发运行的命令可以是shell命令，shell脚本，也可以是某种编程语言代码
6. 命令可以是同步执行，也可以是异步执行
7. 容易定制，配置灵活，方便扩展。
8. 跨平台，至少支持Windows和Linux
9. 绿色版发布，无需安装，不需要管理员权限也能使用

## 使用方法
```
java -jar filewatcher.jar [fw_file_path]
```
其中，`fw_file_path`是一个可省略的参数，表示fw文件的路径或者是文件夹，`fw`文件是一种采用`fw`语言描述的脚本，以`fw`为扩展名。如果`fw_file_path`是一个文件，则执行`fw`文件定义的语言来监控相应的文件（夹）；如果`fw_file_path`是文件夹，则执行文件夹下的所有`fw`文件；如果没有指定，则相当于指定当前文件夹。

# 作为一种特定语言
File Watcher可以作为语言`fw`的虚拟机执行器，下面是fw语言的语法：

`start to`   非递归启动一个观察器watcher，fw文件可以有一个到多个观察器，也就是说start语句可以有多个
`start recursive` 递归启动一个观察器
`stop to`  关闭观察器
`watch` 观察某路径，后面跟着一个字符串，指定要观察的路径
`filter` 定义过滤条件，后面跟着include或者exclude，include表示要的条件，而exclude表示排除的条件。
`async`  异步执行代码
`on file modified`  当文件修改时调用紧跟着的代码块

上面列出部分关键字和语法，更多请联系作者wenzhe。下面给出例子：
``` groovy
  start recursively watch "E:/wenzhe/aa" \
  filter include extension (
    "txt"
  ) filter exclude file name contains "123" \
  on file modified { updatedFile ->
    async {
      println "file $updatedFile modifieddddd"
    }
  } on file modified { updatedFile ->
    println "open notepad"
    //  "notepad '$updatedFile'".execute()
  }
  
  start to watch "E:/wenzhe/bb" \
  on file and folder updated { updatedFile, updatedType ->
    println "file $updatedFile $updatedType"
  }
```
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