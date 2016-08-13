File Watcher，是由wenzhe本人开发的一个文件监控工具（见：[File Watcher，不只是一个工具](http://blog.csdn.net/liuwenzhe2008/article/details/52185351)），关于它的具体需求以及需求分析过程，请参加wenzhe本人的另一篇文章： [基于领域特定语言（DSL）的用例驱动开发（UDD）](http://blog.csdn.net/liuwenzhe2008/article/details/52184910)。

不同于需求分析，本文主要立足于其技术实现细节。本文通过介绍它的技术实现过程，来阐述两个概念：实验驱动开发（EDD），以及响应式编程（使用RxJava库）。

# 实验驱动开发（EDD）
     
一个产品的功能除了受控于需求之外，另外还受到技术的限制。对于需求，我们介绍过DSL-UDD驱动；对于技术，这里要介绍的是实验驱动开发（EDD，wenzhe本文自创概念）。
     
   在编写产品代码之前，先根据需要，比较和选择合适的技术、框架、开源库，验证技术可行性，明确实现上的难点，通过实验解决。先做实验，看看如何使用，有什么坑（注意事项），有什么优缺点，能否满足产品的要求。
   
   通过实验的方式来验证相关技术的可行性，解决技术难点，调整实现的思路，从而决定开发的方向。也就是说，在编写产品代码前通过实验来驱动开发的方向，这样的开发模式，用一个酷一点的词来描述，我称它为“实验驱动开发”（这个词也许是我的首创），英文简写就叫做“EDD”吧，^_^。
   
   实验的代码也是宝贵的代码资源，同样需要保留在源码中，以便于以后回顾理解开发思路，也方便以后做更多实验做参考比较。当然，实验代码不能编译进产品中，因此，我把它们放在maven工程的src/test/java中，包名包含experiment，以便与unit test代码区分开。这样既能保留在源码中，又不会编译进产品。
   
   实验要针对产品的需要来做，一个实验只针对一项或少数的功能验证，以探索和验证某一技术解决方案为目的，而无需考虑产品的整体。每个实验的关注点在于局部，这是实验性代码与产品代码的不同。
   
  把产品技术实现难点细分到每个实验，把精力集中到一点，然后一点一点地从而解决整个问题。
  
  实验中的代码可以尝试各种情况，允许成功与失败。失败的代码也是宝贵的资源，不要删除，保留在源码中。失败的代码可以积累经验。在传统的软件开发中，使用某项技术之前没有做实验就直接设计和编写产品代码，很有可能在开发到一半才发现思路错了，那就需要返工，不仅浪费时间，而且还破坏原有设计良好的代码。相反，采用实验驱动开发，设计和编写产品代码之前通过实验试错，可以避免这些问题。
  
  在实验中积累了的有利于产品开发的代码，可以加入到产品代码中去。花时间去做实验，其实一点也不浪费。

## WatchService
本项目的主要难点在于如何监控文件的变化。可以考虑Java 7自带的WatchService。它底层采用操作系统的文件监控服务，当文件（夹）发生变化（增删改）时，操作系统会发送事件，WatchService监听到事件后会回调我们的代码。它利用了操作系统的底层机制，无需定时轮询遍历整个文件夹。

  通过对WatchService简单实验，我发现WatchService有一些问题不能满足需求：
  
（1）WatchService只能监控文件夹，不能监控文件。
（2）只能监控文件夹下的第一层文件，不能递归地监控每一层的文件
（3）当文件修改时，连续收到两次或三次同样的事件。
（4）Windows下，当监控一个非空子目录时，无法删除其父目录。
（5）当一个文件创建时，会发两个事件，第一个是Create事件，第二个是Modify事件。

针对第一个问题，我的解决方案是监控文件的父目录，然后通过过滤只选出要监控的文件。

针对第二个问题，我的解决方案是通过Java 7提供的Files.walkFileTree来得到每个目录，然后都加到WatchService里。

针对第三个问题，我的解决方案是使用缓存，1.5秒内若是收到同样的事件则忽略掉，也就是去抖动，避免无谓的重复处理。

第四个问题，发现已经有人向Oracle公司提Bug，但Oracle表示won't fix，因为这是Windows的问题，不是Java的问题。

第五个问题，可以认为文件创建会抛出两个事件就好，其实并不算问题，知道这个规则就好，本项目不做特别处理。

通过实验，我们能够尽早地发现问题，并且调整我们的实现策略，将困难更早地暴露出来尽早处理。

下面给出了对WatchService的实验代码：
``` java
/**
 * @author wen-zhe.liu@asml.com
 *
 */
public class FileWatcher1 {
  
  private static List<Path> getDirsToWatch(Path path) throws IOException {
    List<Path> dirsToWatch = new ArrayList<>(); 
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        dirsToWatch.add(dir);
        return FileVisitResult.CONTINUE;
      }
    });
    return dirsToWatch;
  }

  /**
   * @param args
   * @throws IOException 
   * @throws InterruptedException 
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
    
      Path root = Paths.get("E:/wenzhe");
      
      List<Path> dirsToWatch = getDirsToWatch(root);
      
      Map<Path, WatchKey> pathWatchKeyMap = new HashMap<>();
      
      for (Path path : dirsToWatch) {
        WatchKey watchKey = path.register(watcher,   
            StandardWatchEventKinds.ENTRY_CREATE,  
            StandardWatchEventKinds.ENTRY_DELETE,  
            StandardWatchEventKinds.ENTRY_MODIFY);
        pathWatchKeyMap.put(path, watchKey);
      }
      
      Map<Kind<?>, String> fileUpdateTypes = ImmutableMap.of(
          StandardWatchEventKinds.ENTRY_CREATE, "created",
          StandardWatchEventKinds.ENTRY_DELETE, "deleted",
          StandardWatchEventKinds.ENTRY_MODIFY, "modified");
  
      while (true) {  
        WatchKey key = watcher.take();  
        for (WatchEvent<?> event: key.pollEvents()) {  
          Path watchablePath = (Path) key.watchable();
          Path path = watchablePath.resolve((Path) event.context());
          Kind<?> kind = event.kind();
          
          String fileType = Files.isDirectory(path) ? "Folder" : "File";
          String updateType = fileUpdateTypes.getOrDefault(event.kind(), "unknown");
          System.out.printf("%s %s to %s\n", fileType, updateType, path.toString());  
          
          if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path)) {
            WatchKey watchKey = path.register(watcher,   
                StandardWatchEventKinds.ENTRY_CREATE,  
                StandardWatchEventKinds.ENTRY_DELETE,  
                StandardWatchEventKinds.ENTRY_MODIFY); 
            pathWatchKeyMap.put(path, watchKey);
          } else if (kind == StandardWatchEventKinds.ENTRY_DELETE && !Files.exists(path)) {
            WatchKey watchKey = pathWatchKeyMap.get(path);
            if (watchKey != null) {
              pathWatchKeyMap.remove(path);
              watchKey.cancel();
              System.out.println("watchKey.cancel()");
            }
          }
        }  
  
        System.out.println("key.isValid(): " + key.isValid());
        if (key.isValid() && !key.reset()) {  
          System.out.println("!key.reset()");
          break;  
        }  
      }
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      System.out.println("Exit");
    }
  }

}
```
watcher.take方法会阻塞地监听文件的变化，一旦文件变化，它会返回WatchKey对象，我们可以从中得到是哪一个文件发生了什么样的变化（创建，删除，修改）。

# 响应式编程
## 响应式的WatchService
在take方法后面的代码，其实是对监听到文件变化的响应代码。但是，如果不熟悉WatchService的话，这种响应代码是很难识别出来的，因为它与WatchService的服务提供代码混合在一起。

举个例子，如果我们要增加3个必要的过滤条件（缺一不可），都符合了才执行处理逻辑。处理逻辑执行中必须先调用某个函数，然后执行其他的用户定义的函数。代码如下所示：
``` java
WatchKey key = watcher.take();
for (WatchEvent<?> event: key.pollEvents()) {  
  Path watchablePath = (Path) key.watchable();
  Path path = watchablePath.resolve((Path) event.context());
  Kind<?> kind = event.kind();
  ...
  // 过滤，只处理abc开头的文件，或者包含“123”的文件
  if ((isWatchDir || path.equals(event.getPath()))
    && (event.exists() || event.isDeleted())
    && (skipDuplicateEvent(pathLastModifiedTime, event, timeout))) {
    // 响应处理逻辑
    updateWatchService(watchService, event)
    // 响应其他逻辑
    ...
  }
```
过滤逻辑和响应处理逻辑与WatchService代码紧密地耦合在一起，想一想，如果过滤条件再复杂一点，或者响应处理逻辑再复杂一点，那上面代码改起来会变得更加复杂，强耦合性会导致更加难以维护。如果要实现需求定义的复杂的条件逻辑和响应逻辑，那么很难想象代码能乱成怎样。

另外，take方法是阻塞式的，如果不想阻塞，那么我们需要创建线程去调用它。引入多线程（或线程池）的代码将会变得更复杂，可读性会进一步较低。

那么，有没有办法，在保持高可读性的情况下，不仅能够轻松控制阻塞与非阻塞，而且不管过滤条件多复杂，组合顺序多任意，响应逻辑多复杂以及不管有多少个不同的响应逻辑，我们代码都能够轻松灵活地应付。

答案当然是肯定的，那就是采用**响应式编程**。我们可以这样来理解响应式编程：把代码分为两部分，一部分是服务提供逻辑，另一部分是定义响应逻辑。把服务提供逻辑和定义响应逻辑分开，可以更好地实现模块化，通过类似于搭积木的方式，把多个简单的步骤组合出各种复杂的逻辑，不同的组合方式可以构成不同的逻辑。

对于上面WatchService的代码，基于RxJava的响应式编程是这样写的:
``` java
/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class FileWatcher {
  ...
    // 创建被观察者对象
    Observable.create(subscriber -> watchFile(watcher, subscriber))
    .subscribeOn(Schedulers.io()) // 在线程池中订阅，不阻塞主线程
  ...
  // 定义订阅逻辑，当被观察者对象被订阅时调用
  private void watchFile(FileWatchService watcher, Subscriber<? super FileWatchEvent> subscriber) {
    try {
      while (!subscriber.isUnsubscribed()) {  
        val key = watcher.take();  
        for (val event : key.pollEvents()) {  
          val watchablePath = (Path) key.watchable();
          val path = watchablePath.resolve((Path) event.context());

          subscriber.onNext(new FileWatchEvent(path, event.kind()));
        }  
        if (key.isValid() && !key.reset()) {  
          break;  
        }
      }
      subscriber.onCompleted();
    } catch (Throwable e) {
      subscriber.onError(e);
    }
  }
```
当被观察者被订阅的时候（当调用`Observable`的`subscribe`方法），上面的watchFile方法会被调用。而订阅者就是响应它的对象，即watchFile方法的参数`subscriber`，一个`Subscriber`类的对象。

`Subscriber`是一个`RxJava`中的订阅者类，它有三个方法，`onNext`方法用于响应每个事件时回调，`onCompleted`方法用于完成所有任务时的回调，当有未处理的运行时异常抛出时`onError`方法被回调。

`subscribeOn`方法可以设置订阅的线程（即执行`watchFile`方法的线程）在哪个线程池中执行，`Schedulers.io()`方法返回一个Java标准的`CacheThreadPool`，带缓存的线程池。这样轻松可以解决take方法阻塞的问题。如果想要阻塞的话，只需把这条语句去掉即可，简单吧！

现在，每次有文件更新都会触发订阅者`subscriber`的`onNext`方法，那是否要处理（取决于过滤条件），怎么处理，就不用WatchService代码操心了，交给后续的订阅者逻辑就行了。

比如，当
``` java
  Observable.create(subscriber -> watchFile(watcher, subscriber))
    .subscribeOn(Schedulers.io())
    .filter(event -> isWatchDir || path.equals(event.getPath()))
    .filter(event -> event.exists() || event.isDeleted())
    .filter(event -> skipDuplicateEvent(pathLastModifiedTime, event, timeout))
    .doOnNext(event -> updateWatchService(watchService, event))
    .doAfterTerminate(() -> watchService.close());
```

我们增加了一些很有用的过滤条件，以及定义了回调，只需在`Observable`后面加上不同的`filter`，`doOnNext`, `doAfterTerminate`，用链式组合的方式，非常灵活。

保证可读性也容易，通过代码很容易理解其中的逻辑。下面根据响应链条，依次分析每个响应节点的逻辑：

第一个条件，如果观察的是文件夹则通过，否则（指观察文件而非文件夹）只有被观察文件自己更新了才能通过，这是解决`WatchService`不能观察普通文件的解决方案，即如果路径为文件夹则直接监控，否则监控其父目录，然后通过过滤去掉其他兄弟的更新事件，从而实现对普通文件的监控。下面的Java代码描述了这种策略。
``` java
    val isWatchDir = Files.isDirectory(path);
    val pathToWatch = isWatchDir ? path : path.getParent();
```
第二个条件是更新的文件要么存在，要么就是删除事件（删除事件收到时文件已经不存在了）。

第三个条件是忽略重复事件，一个事件发生后，`timeout`时间内的相同事件将会被忽略掉，解决了WatchService的抖动问题。

`pathLastModifiedTime`定义为一个缓存，类似一个Map，key为文件路径，value为记录WatchService事件接收的时间。这个缓存采用Guava提供的超时缓存，即如果写完后timeout时间内没有被访问，会自动从缓存中清除该记录。
``` java
    long timeout = 1500; // ms
    Cache<Path, Long> pathLastModifiedTime = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, TimeUnit.MILLISECONDS)
        .build();
```
忽略重复事件的代码逻辑如下：
``` java
  /**
   * fix Java's watch service issue that modify file event will send 2 to 3 times
   */
  private boolean skipDuplicateEvent(Cache<Path, Long> pathLastModifiedTimeCache, 
      FileWatchEvent event, long periodInMilliSecond) {

    if (event.isModified() && event.isFile()) {
      val path = event.getPath();
      val now = System.currentTimeMillis();
      synchronized (this) {
        val lastModifiedTime = pathLastModifiedTimeCache.getIfPresent(path);
        if (lastModifiedTime == null || now - lastModifiedTime > periodInMilliSecond) {
          pathLastModifiedTimeCache.put(path, now);
          return true;
        } else {
          return false;
        }
      }
    } else {
      return true;
    }
  }
```
回到响应式编程代码，`doOnNext`是响应订阅者`onNext`的响应逻辑，这里调用的是更新WatchService，即当有新的文件夹创建时注册到WatchService中，当有老文件夹删除时，在WatchService中取消对该文件夹的监听。
``` java
  private void updateWatchService(final FileWatchService watchService, FileWatchEvent event) {
    if (event.isCreated() && event.isDirectory()) {
      watchService.register(event.getPath());
    } else if (event.isDeleted() && !event.exists()) {
      watchService.cancel(event.getPath());
    }
  }
```
`doAfterTerminate`是当观察者逻辑成功结束或者遇到错误时，即`onCompleted`或者`onError`时响应的逻辑，它类似于Java中的`finally`语句。这里是关闭`WatchService`服务，有了这个结束时响应逻辑，妈妈再也不要担心服务忘记关闭了！^_^

且不考虑用户在DSL中定义的过滤器和响应逻辑，以上这些过滤和响应逻辑是作为File Watcher基本功能必备的逻辑，我们作为底层代码给出基于响应式编程的`FileWatcher`类的实现：
``` java
package org.wenzhe.filewatcher;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Value
public class FileWatcher {

  Path path;
  boolean recursively;
  
  /**
   * include the root path itself
   */
  private static List<Path> listDirsRecursively(Path path) throws IOException {
    List<Path> dirsToWatch = new ArrayList<>(); 
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        dirsToWatch.add(dir);
        return FileVisitResult.CONTINUE;
      }
    });
    return dirsToWatch;
  }

  @SneakyThrows
  public Observable<FileWatchEvent> asObservable() {
    if (!Files.exists(path)) {
      throw new FileWatcherException("not exist path " + path);
    }
    
    long timeout = 1500; // ms
    Cache<Path, Long> pathLastModifiedTime = CacheBuilder.newBuilder()
        .expireAfterWrite(timeout, TimeUnit.MILLISECONDS)
        .build();
    
    val isWatchDir = Files.isDirectory(path);
    val pathToWatch = isWatchDir ? path : path.getParent();
 
    val obsvPath = isWatchDir && recursively ? 
        Observable.from(listDirsRecursively(pathToWatch)) : Observable.just(pathToWatch);
    
    val watchService = new FileWatchService();

    return obsvPath.reduce(watchService, (watcher, path) -> watcher.register(path))
    .flatMap(watcher -> 
      Observable.create(subscriber -> watchFile(watcher, subscriber))
      .subscribeOn(Schedulers.io())
      .cast(FileWatchEvent.class)
    )
    .filter(event -> isWatchDir || path.equals(event.getPath()))
    .filter(event -> event.exists() || event.isDeleted())
    .filter(event -> skipDuplicateEvent(pathLastModifiedTime, event, timeout))
    .doOnNext(event -> updateWatchService(watchService, event))
    .doAfterTerminate(() -> watchService.close());
  }

  private void updateWatchService(final FileWatchService watchService, FileWatchEvent event) {
    if (event.isCreated() && event.isDirectory()) {
      watchService.register(event.getPath());
    } else if (event.isDeleted() && !event.exists()) {
      watchService.cancel(event.getPath());
    }
  }

  /**
   * fix Java's watch service issue that modify file event will send 2 to 3 times
   */
  private boolean skipDuplicateEvent(Cache<Path, Long> pathLastModifiedTimeCache, 
      FileWatchEvent event, long periodInMilliSecond) {

    if (event.isModified() && event.isFile()) {
      val path = event.getPath();
      val now = System.currentTimeMillis();
      synchronized (this) {
        val lastModifiedTime = pathLastModifiedTimeCache.getIfPresent(path);
        if (lastModifiedTime == null || now - lastModifiedTime > periodInMilliSecond) {
          pathLastModifiedTimeCache.put(path, now);
          return true;
        } else {
          return false;
        }
      }
    } else {
      return true;
    }
  }

  private void watchFile(FileWatchService watcher, Subscriber<? super FileWatchEvent> subscriber) {
    try {
      while (!subscriber.isUnsubscribed()) {  
        val key = watcher.take();  
        for (val event : key.pollEvents()) {  
          val watchablePath = (Path) key.watchable();
          val path = watchablePath.resolve((Path) event.context());

          subscriber.onNext(new FileWatchEvent(path, event.kind()));
        }  
        if (key.isValid() && !key.reset()) {  
          break;  
        }
      }
      subscriber.onCompleted();
    } catch (Throwable e) {
      subscriber.onError(e);
    }
  }
}
```
`FileWatcher`类是本项目File Watcher核心库的核心实现，它是一个响应式风格的、更加强大和完善的、改善Java 7 `WatchService`的类，其主要逻辑都再于`asObservable`方法中，通过返回`Observable<FileWatchEvent>`的方式，响应式地提供给被调用者。
## 响应式地实现用户DSL中描述的复杂逻辑
假设用户的需求描述（定义在fw文件这个DSL中）如下：
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
    "notepad '$updatedFile'".execute()
  }
```
下面看看如何利用`FileWatcher`类提供的响应式方法`asObservable`，实现用户DSL需求描述的复杂逻辑。如下代码所示：
``` java
    Observable<FileWatchEvent> fwe = new FileWatcher(
        Paths.get(watcher.getWatchedFile()), watcher.isRecursively())
    .asObservable()
    .filter(evt -> includeFilters.isEmpty() 
        || includeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)))
    .filter(evt -> !excludeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)));
    
    for (val handler : watcher.getHandlers()) {
      fwe = fwe.doOnNext(evt -> {
        if (isFileTypeMatch(evt, handler.getFileTypes())
            && handler.getUpdateType().match(evt)) {
          handler.getCode().call(evt.getPath().toString(), 
             UpdateType.from(evt).toString().toLowerCase());
        }
      });
    }
```
上面代码中`watcher`变量为记录DSL中描述的一个`watch`语句块，它包含用户想要监控的文件（夹）路径，0个到多个的过滤条件（上面代码中的`includeFilters`和`excludeFilters`），另外还有1个到多个的响应处理逻辑（`DSL`中的`on`语句）。

不管用户描述的逻辑有多复杂，上面简单的几行代码就可以轻松应付之，而且仍然保持很好的可读性和灵活可组合性，即使需要更改逻辑或者扩展逻辑，都只是改变或增加链式响应式处理组合而已，可见响应式编程思维的强大之处。

下面还是根据响应链条，依次分析每个响应节点的逻辑：

第一个响应式过滤器`filter`是处理DSL中`include`语句的，如果没有定义`include`或者符合其中任意一个条件的响应事件都能够通过，否则被排斥在外。

第二个`filter`是处理DSL中`exclude`语句的，符合任何一个条件的响应事件都不能通过。

通过前面两个过滤器的响应事件，会进入到下个响应处理环节。遍历用户定义的每个处理方法（即on语句），通过`doOnNext`响应，响应逻辑中通过函数式调用用户定义的函数，如果同时符合文件类型匹配和事件更新类型匹配的话。

上面的代码都是定义在类`FileWatcherExecutor`中，它作为用户DSL的真正执行器来运行。下面是它的Java代码：
``` java
package org.wenzhe.filewatcher;

import static java.util.stream.Collectors.partitioningBy;

import java.nio.file.Paths;
import java.util.List;

import org.wenzhe.filewatcher.dsl.FileType;
import org.wenzhe.filewatcher.dsl.FileWatcherDslContext;
import org.wenzhe.filewatcher.dsl.Filter;
import org.wenzhe.filewatcher.dsl.FilterType;
import org.wenzhe.filewatcher.dsl.UpdateType;
import org.wenzhe.filewatcher.dsl.Watcher;

import lombok.val;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class FileWatcherExecutor {
  
  public static Subscription execute(Action1<FileWatcherDslContext> dslContextInitializer) {
    return run(dslContextInitializer).subscribe();
  }
  
  public static Observable<FileWatchEvent> run(Action1<FileWatcherDslContext> dslContextInitializer) {
    val ctx = new FileWatcherDslContext();
    dslContextInitializer.call(ctx);
    return run(ctx);
  }
  
  public static Observable<FileWatchEvent> run(FileWatcherDslContext ctx) {
    return Observable.from(ctx.getWatchers())
    .filter(watcher -> watcher.isStart())
    .flatMap(FileWatcherExecutor::run);
  }
  
  public static Observable<FileWatchEvent> run(Watcher watcher) {
    val groupedFilters = watcher.getFilters().stream()
    .collect(partitioningBy(filter -> filter.getFilterType() == FilterType.INCLUDE));
    val includeFilters = groupedFilters.get(true);
    val excludeFilters = groupedFilters.get(false);
    
    Observable<FileWatchEvent> fwe = new FileWatcher(
        Paths.get(watcher.getWatchedFile()), watcher.isRecursively())
    .asObservable()
    .filter(evt -> includeFilters.isEmpty() 
        || includeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)))
    .filter(evt -> !excludeFilters.stream().anyMatch(filter -> matchFilter(evt, filter)));
    
    for (val handler : watcher.getHandlers()) {
      fwe = fwe.doOnNext(evt -> {
        if (isFileTypeMatch(evt, handler.getFileTypes())
            && handler.getUpdateType().match(evt)) {
          handler.getCode().call(evt.getPath().toString(), 
             UpdateType.from(evt).toString().toLowerCase());
        }
      });
    }
    return fwe;
  }
  
  private static boolean matchFilter(FileWatchEvent evt, Filter filter) {
    return isFileTypeMatch(evt, filter.getFileTypes()) && filter.filter(evt);
  }

  private static boolean isFileTypeMatch(FileWatchEvent evt, List<FileType> fileTypes) {
    return (evt.isFile() && fileTypes.contains(FileType.FILE))
        || (evt.isDirectory() && fileTypes.contains(FileType.FOLDER))
        || evt.isDeleted();
  }
}
```
上面的代码中，主要到另一个FileWatcherExecutor.run方法的重载：
``` java
  public static Observable<FileWatchEvent> run(FileWatcherDslContext ctx) {
    return Observable.from(ctx.getWatchers())
    .filter(watcher -> watcher.isStart())
    .flatMap(FileWatcherExecutor::run);
  }
```
获取watchers，通过过滤筛选出isStart的，然后调用另一个run重载（前文已介绍过）。

## 响应式获取输入--DSL描述的需求
File Watcher工具软件允许用户提供一个文件或文件夹路径作为程序的运行参数，命令行为：`java -jar filewatcher.jar [fw_file_path]`。

作为程序参数的路径信息，当然是`String`类型的，我们把它作为一个输入，一个待处理者，或者说一个被观察者，于是可以通过RxJava的Observable.just方法转换为Observable对象，然后就可以使用响应式编程思想，通过链式响应处理，直至获取整个DSL需求记录起来，进而调用上一节的执行逻辑来响应记录的结果从而完成整个用户需求的执行。如下代码所示：
``` java
  public void start() {
    subscription = Observable.just(path)
        .map(strPath -> Paths.get(strPath).toAbsolutePath())
        .flatMap(FileWatcherDslRunner::getDslContexts)
        .flatMap(FileWatcherExecutor::run)
        .subscribe(this::onNext, this::onError);
  }
```
第3行的map方法把Observable里的String转换为Path，然后交由后面响应处理。这很像Java 8中函数式编程中的`map`。

第4行的`flatMap`方法是把`Path`对象转换成另一个`Observable`对象(与Java 8中`flatMap`相似），`FileWatcherDslRunner::getDslContexts`是用来执行`Path`对象（定义`DSL`的文件或文件夹），得到`FileWatcherDslContext`对象来记录所有的需求。这一点将在本节后续详细介绍。

接下来的`flatMap`根据输入的`FileWatcherDslContext`，执行`WatchService`逻辑，返回文件变化的事件`FileWatchEvent`的被观察者`Obserable<FileWatchEvent>`，这一点在上一小节以及介绍过了。

RxJava中的`Observable`中定义的逻辑都是懒惰的，只有被订阅（调用`subscribe`方法）才会执行。这一特性跟Java 8函数式编程中的流Stream很像。

上面代码中，`subscribe(this::onNext, this::onError)`，订阅观察者，只有订阅了，响应处理逻辑才会真正执行。

`subscribe`方法参数中，`onNext`方法是每次有`FileWatchEvent`的时候被调用，`onError`是出错时调用。

这里的订阅在响应式编程里称为**冷订阅**，因为只有被订阅者订阅了才会发事件；与之不同的是**热订阅**，不管有没有被订阅，都会发事件，本项目中没有用到，因此本文不做阐述，有兴趣的读者可阅读相关文献。

可以看到，subscribe方法返回订阅者Subscription对象，我们用成员变量subscription来接收。调用订阅者subscription的取消订阅方法unsubscribe，即可停止监听事件。见如下的代码：
``` java
  private void stop() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
  }
```
下面看看FileWatcherDslRunner::getDslContexts方法是如何解析DSL并记录起来的，先看看代码：
``` java
/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class FileWatcherDslRunner {

  @SneakyThrows
  private static FileWatcherDslContext parse(Path dslPath) {
    val context = new FileWatcherDslContext();
    val binding = new Binding();
    binding.setProperty("context", context);
    val configuration = new CompilerConfiguration();
    val dslText = new String(Files.readAllBytes(dslPath), "UTF8");
    val groovyCode = String.format("context.with {%s}", dslText);
    val dslScript = new GroovyShell(binding, configuration).parse(groovyCode);
    dslScript.run();
    return context;
  }
  
  @SneakyThrows
  private static Observable<Path> getDslFiles(Path folder, int maxDepth) {
    try (val stream = Files.walk(folder, maxDepth)) {
      return Observable.from(
          stream.filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".fw"))
          .toArray(Path[]::new));
    }
  }
  
  public static Observable<FileWatcherDslContext> getDslContexts(Path dsl) {
    return Observable.just(dsl)
    .flatMap(dslPath -> {
      if (Files.isDirectory(dslPath)) {
        return getDslFiles(dslPath, 1);
      } else {
        return Observable.just(dslPath);
      }
    })
    .map(FileWatcherDslRunner::parse)
    ;
  }
}
```
注意`getDslContexts`方法，输入`Path`类型的变量`dsl`，它可以是文件，也可以是文件夹。

先用`Observable.just`转成`Observable`对象，然后用`flatMap`，如果它是文件夹，就调用`getDslFiles`，其查找文件夹下的fw文件并将每个文件组织成`Obserable<Path>`，最后调用`parse`方法，使用`GroovyShell`执行`DSL`，为每个`fw`文件生成一个`FileWatcherDslContext`对象，其返回值`Observable<FileWatcherDslContext>`表示一个到多个`FileWatcherDslContext`对象的被观察者。

## RxJava简介
本文代码中，不管是用户输入文件的解析，还是`WatchService`的使用和执行，都采用了响应式编程思维，将服务实现逻辑和响应处理逻辑分开，采用链式响应式过滤和组合的方式，即保持高可读性的同时，有灵活的处理复杂的逻辑，同时仍然保持着足够的可扩展性，还有强大的线程池支持和控制，这些都归功于RxJava这个优秀的库。

RxJava目前在还提供了众多的库支持，详情请参考其官方网页：https://github.com/ReactiveX 。

 ---------------------- 本博客所有内容均为原创，转载请注明作者和出处 -----------------------
 作者：刘文哲

 联系方式：liuwenzhe2008@qq.com

 博客：http://blog.csdn.net/liuwenzhe2008