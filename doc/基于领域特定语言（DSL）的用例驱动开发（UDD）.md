
本文通过设计File Watcher这个软件，来阐述DSL-UDD设计思想。
# 文件监控工具File Watcher的设计愿景

1. 监控文件（夹）变化，包括文件（夹）的创建、修改、删除
2. 文件变化时能够自动运行指定的命令
3. 方便用户配置要监控的文件（夹），能够定义丰富灵活的过滤规则
4. 每个用户都可以有自己的配置，不同用户的配置可以不相同
5. 被触发运行的命令可以是shell命令，shell脚本，也可以是某种编程语言代码
6. 命令可以是同步执行，也可以是异步执行
7. 容易定制，配置灵活，方便扩展。
8. 跨平台，至少支持Windows和Linux
9. 绿色版发布，无需安装，不需要管理员权限也能使用


# 跟随用例，理解需求
产品要站在用户使用的角度来描述，这样才容易使用，才能够让用户喜欢。

上面的设计愿景的描述，还是比较抽象，估计不同开发者理解起来都有所偏差，最好是跟用户一起，通过不断交流、反馈，来理解。

让我跟用户一起，通过构造用户需要的一个具体用例，通过讨论来理解需求。我一边跟用户讨论需求，一边将我所理解的，用“英语”描述一下起来。

用户说道，他想监控文件“file1.md”的变化，如果变化了，自动调用脚本“update_blog.bat”，将文件自动更新到他的博客上。
我随手用“英语”描述了他的话，如下：
``` groovy
  watch "E:/wenzhe/file1.md" on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
又提了一个需求，想要我提供一个开关，能对单独控制每个文件夹或文件的监控。于是我在每个`watch`前面加上了`start to`（启动监控）或者`stop to`（关闭监控），描述如下：
``` groovy
  start to watch "E:/wenzhe/file1.md" on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
  stop to watch "E:/wenzhe/file2.md" on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
我发现一个问题，就是某些脚本调用可能是需要指定工作路径的，不然运行就会出错，因此需要用户提供恰当的工作路径。
``` groovy
  start to watch "E:/wenzhe/file1.md" on file modified {
    "E:/wenzhe/script/update_blog.bat".execute(null, new File("E:/wenzhe/script")
  }
```
接着，他说还有另一个脚本“send_email.bat”，需要在前面提到的脚本“update_blog.bat”成功调用后被调用。
``` groovy
  start to watch "E:/wenzhe/file1.md" on file modified {
    def process = "E:/wenzhe/script/update_blog.bat".execute()
    process.text.eachLine { println it }
    if (process.exitValue() == 0) {
      "E:/wenzhe/script/send_email.bat".execute()
    }
  }
```
另外还有一个脚本“upload_to_cloud.bat”，跟前面两个互不影响，可以跟第一个脚本“update_blog.bat”同时并发执行。我说，可以再加上一个`on file modified`语句块，不同的`on file modified`语句块可以同时并发执行。
``` groovy
  start to watch "E:/wenzhe/file1.md" on file modified {
    async {
      "E:/wenzhe/script/update_blog.bat".execute()
    }
    "E:/wenzhe/script/send_email.bat".execute()
  }
```
他总是觉得调用脚本有点太麻烦了，因为需要他编写一个额外的脚本文件，他希望File Watcher工具能够支持直接嵌入代码。

作为用例，在执行完脚本“upload_to_cloud.bat”之后，执行由用户给定的指定代码，同样可以指定工作路径，可以重定向标准输出和错误输出。这里指定了的代码，用来打印文件更新到云的时间。
``` groovy
  start to watch "E:/wenzhe/file1.md" on file modified { updatedFile ->
    // 模拟用户指定的代码
    def now = LocalDateTime.now()
    println "file $updatedFile upload to cloud on $now"
  }
```
调用命令行可以可以，如：
``` groovy
start to watch "E:/wenzhe/file1.md" on file modified { updatedFile ->
  "cp $updateFile /home/wenzhe/myfolder".execute()
}
```
他又说，想要当文件创建的时候，并发调用另外一段代码判断文件是否是markdown文件，并且打印输出到文件“file1_create_stdout.log”上。我在后面加上一段 `on file created`，如下：
``` groovy
  start to watch "E:/wenzhe/file1.md" \
  on file modified { updatedFile ->
    def now = LocalDateTime.now()
    println "file $updatedFile upload to cloud on $now"
  } \
  on file created { updatedFile ->
    println "file $updatedFile created"
    if (updatedFile.endsWith(".md")) {
      println "this is a md file"
    } else {
      println "this is not a md file"
    }
  }
```
“对了，”他突然补充到，他还有另外一个文件夹，下面有几篇文章，也想能够更新到博客上。另外，他只关心扩展名为.md, .txt, .doc, .docx, .png, .jpg, .jpeg的文件，不需要关心其他文件和子文件夹。
``` groovy
  start to watch "E:/wenzhe/folder1" filter include extension (
    "md", "txt", "doc", "docx", "png", "jpg", "jpeg"
  ) on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
他接着又想想，如果是上班时间，就不要更新博客了，以免让领导知道他不务正业。他的上班时间是每周一到周五的9点到18点。
``` groovy
  def isWorkTime = {dateTime ->
    def dayOfWeek = dateTime.getDayOfWeek()
    int hour = dateTime.getHour()
    return hour >= 9 && hour < 18 &&
      [DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
       DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
      ].contains(dayOfWeek)
  }
  
  start to watch "E:/wenzhe/folder1" filter include extension (
    ".md", ".txt", ".doc", ".docx", ".png", ".jpg", ".jpeg"
  ) filter include when { updatedFile, updatedType ->
    !isWorkTime(LocalDateTime.now())
  } on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
另外，他又补充到，还有另一个文件夹，它的某些文件夹也包含了这些需要更新的文件，需要递归的监控和过滤；但是不需要监控文件夹名字为target, bin, .settings的那些文件夹。我把`start to`改成`start recursively`。
``` groovy
  start recursively watch "E:/wenzhe/folder1" \
  filter include extension (
    "md", "txt", "doc", "docx", "png", "jpg", "jpeg"
  ) filter exclude folder name equalsTo "target", "bin", ".settings" \
  on file modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
另外，有些特殊的.txt，的文件也不需要监控，这些特殊的文件都是不同系统生成的日志文件，以年月日命名，但规则比较复杂，先看看例子：
```
2016.07.09.txt
2016.7.9.txt
2016.7.09.txt
```
总结起来就是显示年月日，年为4个数字，月和日为1到2个数字，第一个数字如果是0可以省略。规则比较复杂，我用正则表达式描述为 `\d{4}\.\d?\d\.\d?\d`
``` groovy
 start recursively watch "E:/wenzhe/folder1" \
  filter include extension (
    "md", "txt", "doc", "docx", "png", "jpg", "jpeg"
  ) filter exclude folder name equalsTo "target", "bin", ".settings" \
  filter exclude file name matches "\\d{4}\\.\\d?\\d\\.\\d?\\d" \
  on file modified {
   "E:/wenzhe/script/update_blog.bat".execute()
  }
```
然后他有想要加上很多其他的过滤规则，比如只是想监控文件名为wenzhe和java开头的文件，不监控目录名为test或DSL结尾（大小写不敏感）的目录，不监控目录路径包含src/test或者src/main/resources的目录。

规则很复杂，我还是可以把它用英文记录下来，如下所示：
``` groovy
  start recursively watch "E:/wenzhe/folder1" \
  filter include extension (
    "md", "txt", "doc", "docx", "png", "jpg", "jpeg"
  ) filter exclude folder name equalsTo "target", "bin", ".settings" \
  filter exclude file name matches "\\d{4}\\.\\d?\\d\\.\\d?\\d" \
  filter include file name startsWith "wenzhe", "java" \
  filter exclude folder name cases insensitive endsWith "test", "DSL" \
  filter exclude folder path contains "src/test", "src/main/resources" \
  on file and folder modified {
    "E:/wenzhe/script/update_blog.bat".execute()
  }
```
看了看上面的“英文”记录，他意识到规则越来越复杂，问我是不是太苛刻了，能不能实现，同时要给他提供一个容易灵活配置的文件或者操作界面。

我问他，上面的“英语”是否容易理解？是否足够可以反映他复杂的需求？如果他的需求变化了，他是否自己可以修改上面的“英语”来描述他的新需求？

他说：“当然没问题，上面只是英文描述而已。虽说不才，哥也学过几年英语，不仅看得懂，而且修改也没问题。”

“那很好，”我说，“那我就让这对英文描述-- 飞 -- 起来！”

“什么？”他问道，带着一脸的不解。

我解释说，上面的“英语”也是可以直接运行的，而且会按照他期望的方式，他大吃一惊，惊叹现代化计算机如此智能，上面的英文描述不正就是他想要的那个容易灵活配置的用户操作界面吗？

# DSL-UDD
在跟用户交流中，我们不谈抽象的领域概念，而是聚焦到具体的用例上。通过构造出一个又一个具体的用例，来逐步理解抽象的需求。通过一个又一个的用例，来驱动软件开发，这就是“用例驱动开发”。

其实，上面的“英文”是一段DSL代码（领域驱动语言），描述了用户需要的具体场景。所谓的“领域驱动语言”，说白了，就是DIY,自创出来的语言。

在交流过程中，我换位思考，站在用户的角度，倾听用户的声音；然后反馈，按照我的理解，用DSL记录下来；然后用户通过DSL了解我的理解程度，并作出修正或补充；然后我再修改DSL描述，如此循环。在这个不断地“记录-反馈-记录”过程中，我们会可能会发现对方的某些想法片面，从而可以不断修正补充，并且想到更多用例丰富需求，这样一来，DSL不仅成为交流估计、共通语言、具体的用例需求文档，而且也成为日后软件开发设计的驱动源泉。我把这种模式，赋予一个名字“DSL用例驱动开发”，简称DSL-UDD（wenzhe本人原创）。

根据不同的业务需求，采用用户与开发者都能理解的共同语言记录下来，这样方便沟通和反馈。当以后需求越来越复杂时，这种共通的语言（也就是DSL）也会随之发展来描述新的需求。领域驱动设计（DDD）提倡定义共通的词汇以方便交流，这一点，我们可以在DSL层面上实现。除了能提供共通词汇，DSL还描述共通的动作，因为DSL就是用户与开发者约定的共通的特定语言。

用DSL描述需求，不仅方便沟通和反馈，而且当产品发布出去之后，如果DSL也提供给用户，那么，当用户有一些简单的需求变更，直接修改DSL的描述就行，不需要开发者再改代码后提供新的build。可以说，DSL在产品发布后，能够极大地方便用户定制更具体的需求，而无需开发者参与。我把这点称作DSL的动态属性，这些提供给用户使用的DSL成为“动态DSL”，因为用户可以动态修改DSL，而无需编译。

当然，并不是说所有产品都会给用户提供DSL，这仍然取决于用户的需要。如果用户不需要，采用DSL仍然是非常有用和值得推荐的。

用DSL，对代码可读性和可维护性，是很有帮助的。DSL用例驱动开发（DSL-UDD），就是把需求文档通过构造一个又一个的具体用例，转换为一个又一个的DSL描述，从中抽象出DSL的语法和语义，而软件的功能实现，就转换为对DSL提供底层支持的驱动代码。从代码的设计层面上讲，就是把业务需求变化紧密相关的业务层代码，转移到DSL上去，而其他代码成为支持DSL的驱动代码，与业务需求隔离开，受业务需求变更的影响就大大减小，我把这些代码成为DSL的驱动代码，简称驱动层。

当DSL随之业务发展不断丰富时，当有些新的业务到来或者原有的需求变更了，如果现有的DSL足以描述新的需求，那么只需要在DSL层上做调整，而无需修改驱动层代码。

只有当现有的DSL不足以描述新需求时，增加驱动层代码，扩展DSL。
而如果没有业务需求，但有非功能需求时，比如想改进设计，提高性能，则无需修改DSL层，只需确保驱动层接口不变的情况下，改进驱动层实现代码。

好了，很多读者会疑惑，说了这么多，上面那段“英语”是怎么“飞”（运行）起来的呢？

# DSL分类
## 内部DSL和外部DSL
业界一般按照DSL的实现方式，把DSL分为内部DSL和外部DSL。

内部DSL指的是通过某种通用的编程语言（称为宿主语言）的语法编写出来的DSL，该DSL语法受制于宿主语言的语法，但不需要额外的规则去解析。这种方式简单而且强大，因为它就是宿主语言本身。能够提供内部DSL的宿主语言有很多，最优秀的DSL宿主语言包括Groovy和Scala等。当然Java也可以，只是多了些噪声。内部DSL的一个优秀范例是Gradle（作为编译脚本广泛用于大量Java，Groovy项目，经典用例是Android的编译脚本采用Gradle编写），其宿主语言就是Groovy；另一个优秀范例是Web框架Grails，与经典的SSH框架相比Grails简化配置，提供开发效率；另外logback的groovy配置文件也是一个例子。

外部DSL是自己实现的新语言，不受任何限制，但工作量大（毕竟是要开发一种新语言），需要文本解析，构造抽象语法树。XML可以认为是一种外部DSL，虽然容易解析，但XML的结构制作太多冗余的噪声，影响可读性，而且不适合描述一下流程结构，如if，else，loop等。目前有一些库能不帮助开发者简化外部DSL，如Antlr库。

对于本文要开发的软件，目前内部DSL的表现力以及足够满足需求，不需要外部DSL。

## 动态DSL和静态DSL
### 动态DSL
前面提过，如果用户需要，我们可以向用户提供DSL以方便用户配置，我把这类DSL称为“动态DSL”。用户可以在运行时编辑DSL脚本，不需要重新编译（甚至不需要重启，如本文介绍的File Watcher工具）。一般采用外部DSL（如XML）或者支持动态编译执行的内部DSL（如Groovy）。由于Scala属于静态编译语言，无法提供动态DSL。

### 静态DSL
前文也说过，如果用户不需要，代码内部使用DSL也能带来很大的好处。

最好是静态编译语言，这样可以利用编译器检查语法。最好与产品其他代码保持同一种语言。一个产品中拥有统一的主流语言，可以减少团队开发者学习难度，减少招聘难度，也容易使代码更容易维护，IDE也能更好地使用，比如重构。可以支持静态DSL的语言，包括Java和Scala。

我对选择动态DSL还是静态DSL的建议是：能用静态DSL的就尽量用静态DSL，除了提供给用户动态修改的部分采用动态DSL外，其他采用静态DSL。

平衡各自的优缺点，本工具File Watcher的设计采用了动态DSL加静态DSL相结合的方式。考虑到给用户提供一个方便灵活的操作接口（定义为扩展名为fw的文件，下文称为fw脚本），给用户提供了基于Groovy的动态DSL，程序内部实现采用基于Java的静态DSL。

限制Groovy的使用范围，代码中没有使用Groovy的代码，只有一个类用于调用fw脚本，需要依赖于Groovy类库，但仍然采用Java编写，因此程序中只有一种语言，就是Java。

# 需求描述与技术实现细节分离
DSL描述了需求，但如何实现则取决于技术实现层面。尽管内部DSL中宿主语言可以直接执行复杂的过程，但如果直接与技术实现细节强耦合，即不利于适应需要的变化，也限制了技术的选择。

本文提出的DSL（需求描述）与技术实现细节分离，类似于面向接口编程中的接口与实现类之间的关系。DSL相当于定义接口，而技术实现细节是实现类。如果需求变化，我们只需要修改DSL即可，无需更改实现代码，除非现有实现代码满足不了新的需求；如果调整技术实现细节，也无需修改DSL定义。
# Builder模式
如何做到DSL（需求描述）与实现细节分离，可以采用Builder模式，即执行DSL时，并不真正执行，而是把所有的DSL需求记录起来，并且在需要的时候，指导真正代码的执行。这有点类似于解析一个XML配置文件，记录起来，然后在需要的时候取出来执行。

# Groovy DSL
先创建一个Groovy工程org.wenzhe.grv，用来做实验，采用实验驱动开发（EDD，wenzhe本人自创，见另一篇文章），来一步步支持DSL。

新建一个Groovy脚本filewatcher1.groovy，用来做实验，编写DSL，用来驱动开发DSL驱动层代码。代码如下：
``` groovy
package org.wenzhe.grv

import org.wenzhe.filewatcher.FileWatcherExecutor

FileWatcherExecutor.execute { fwctx -> fwctx.with {
  
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
  
  start to watch "E:/wenzhe/bb" \
  on file and folder updated { updatedFile, updatedType ->
    println "file $updatedFile $updatedType"
  }
  
}}

while (true) {
  
}
```
可以看到DSL定义在Groovy文件中。下面根据上面的DSL定义，创建Java工程org.wenzhe.filewatcher，用来编写File WatcherDSL的驱动层代码和技术实现代码。

`FileWatcherExecutor`是用来解析DSL，并且执行DSL描述的业务逻辑。
``` java
/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class FileWatcherExecutor {
  
  public static Subscription execute(Action1<FileWatcherDslContext> dslContextInitializer) {
    return run(dslContextInitializer).subscribe();
  }
  
  public static Observable<FileWatchEvent> run(Action1<FileWatcherDslContext> dslContextInitializer) {
    val ctx = new FileWatcherDslContext();  // 创建DSL上下文对象（即Builder对象）
    dslContextInitializer.call(ctx);        // 初始化DSL上下文对象（运行DSL从而完善Builder对象）
    return run(ctx);                          // 通过Builder，执行技术实现细节
  }
```
`FileWatcherExecutor` 的代码可以看到整个DSL解析执行过程，见上面的注释，即先创建DSL上下文对象（即Builder对象），再始化DSL上下文对象（运行DSL从而完善Builder对象），最后通过Builder，执行技术实现细节。
  
再回到Groovy代码第5行，`FileWatcherExecutor.execute`方法接收一个函数，其参数为`FileWatcherDslContext`类，这个类作为收集所有的DSL需求描述，相当于Builder。函数参数`fwctx` 就是`FileWatcherDslContext`类的对象。

主要到Groovy的`with`关键字，它把fwctx对象隐藏，在`with`语句块中出现的方法和域来着fwctx对象，但我们不用写。比如第7行的start，它其实是fwctx对象（`FileWatcherDslContext`类）的`start`方法，如果没有`with`语句块，自必须写成：
``` java
fwctx.start
```
Groovy代码第7行的`recursively`是`FileWatcherDslContext`类定义的公有静态成员常量（with语句块了可以省略fwctx）。

下面是FileWatcherDslContext.java的代码：
``` java
/**
 * @author liuwenzhe2008@gmail.com
 *
 */
@Slf4j
@Getter
public class FileWatcherDslContext {
      
  public static final boolean recursively = true;
  public static final boolean to = !recursively;
  
  public static final FileType file = FileType.FILE;
  public static final FileType folder = FileType.FOLDER;
  public static final FilterType include = FilterType.INCLUDE;
  public static final FilterType exclude = FilterType.EXCLUDE;
  public static final NamePath name = NamePath.NAME;
  public static final NamePath path = NamePath.PATH;
  
  public static final boolean sensitive = false;
  public static final boolean insensitive = !sensitive;

  private final List<Watcher> watchers = new ArrayList<>();
  
  public Watcher start(boolean recursively) {
    if (log.isDebugEnabled()) {
      log.debug("start {}to", recursively ? "recursively " : "");
    }
    val w = new Watcher();
    w.setRecursively(recursively);
    w.setStart(true);
    watchers.add(w);
    return w;
  }
  
  public Watcher stop(boolean to) {
    log.debug("stop to");
    val w = new Watcher();
    w.setStart(false);
    watchers.add(w);
    return w;
  }
  
  public static Subscription async(Action0 action) {
    return Schedulers.io().createWorker().schedule(action);
  }
}
```
由于Groovy的函数调用可以省略小括号，因此DSL中的 start recursively 其实可以相当于：
``` java
  fwctx.start(recursively)
```
其中，recursively从类FileWatcherDslContext中静态导入。
同理，stop to 相当于：
``` java
  fwctx.stop(to)
```
从代码可看到，`start`方法返回`Watcher`对象，`Watcher`有`watch`方法，接受一个`String`的`path`。如下：
``` java
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
```
所以，如果不省略点和括号的话，start recursively watch "E:/wenzhe/aa"  就是
``` java
fwctx.start(recursively).watch("E:/wenzhe/aa")
```
类似的，我们编写了更多的Java代码来支持DSL。由于篇幅关系，这里不做详细介绍，下图显示的是所有的DSL驱动代码文件名，都在`org.wenzhe.filewatcher.dsl`包中：

![DSL driver package](http://img.blog.csdn.net/20160812234221890)


# Java DSL
由于DSL驱动代码采用Java编写，使用它们并不需要Groovy，用Java统一可以编写DSL。如果用Java DSL表达，可读写同样也很好，只是稍微多了些噪声（点和括号），如下所示：
``` java
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
      .on(file).and(folder).updated((updatedFile, updatedType) -> {
        System.out.printf("file %s %s\n", updatedFile, updatedType);
      });
    });
    
    while (true) {
      
    }
```
Java DSL可读性也相当好，当把File Watcher作为一个轻量级Java第三方库时可以作为API使用。

我们把工程org.wenzhe.filewatcher作为一个通用的Java库，它不依赖于Groovy，只是提供DSL的支持，以及底层的技术实现逻辑（后文会介绍）。

# fw文件
File Watcher作为一个工具，或者作为一种语言，用户可以编写DSL代码，并且动态执行，我们采用了Groovy。

新建Java工程org.wenzhe.filewatcher.app，它仍然是Java工程，但以普通jar包的方式第三方依赖于Groovy运行时库，这样，用户编写的DSL代码就可以动态执行了。

用户可以编写以fw为后缀名的DSL文件 test1.fw，（我们称为fw文件，这种语言成为fw语言），如下：
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
  
  start to watch "E:/wenzhe/bb" \
  on file and folder updated { updatedFile, updatedType ->
    println "file $updatedFile $updatedType"
  }
```
Java代码调用Groovy库的`GroovyShell`类来执行fw文件，生成`FileWatcherDslContext`对象，如下FileWatcherDslRunner.java代码所示：
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
```
下面的Java DSL代码，过滤出文件名后缀为fw的文件，当fw文件创建、修改和删除时，调用`onUpdateDsl`方法。
``` java
package org.wenzhe.filewatcher.app;

import static org.wenzhe.filewatcher.dsl.FileWatcherDslContext.file;
import static org.wenzhe.filewatcher.dsl.FileWatcherDslContext.include;
import static org.wenzhe.filewatcher.dsl.FileWatcherDslContext.name;
import static org.wenzhe.filewatcher.dsl.FileWatcherDslContext.to;

import org.wenzhe.filewatcher.FileWatcherExecutor;

import rx.functions.Action1;

/**
 * @author liuwenzhe2008@gmail.com
 *
 */
public class DslWatcher {

  public static void watch(String dslPath, Action1<String> onUpdateDsl) {
    FileWatcherExecutor.execute(context -> context
    
      .start(to).watch(dslPath)
      .filter(include).file(name).extension("fw")
      .on(file).modified(onUpdateDsl)
      .on(file).deleted(onUpdateDsl)
    );
  }
}
```
这个方法可以实现fw文件更新，自动生效，而不需要重启File Watcher程序。

可见，通过定义一种领域语言并支持来描述用户需求，一点都不难。

好了，我们已经通过运行DSL得到了包含所有需求信息的`FileWatcherDslContext`对象，但是，目前用户想要的文件监控过程还没有开始，接下来就是从技术细节上怎么实现这个需求目标，欢迎继续阅读下一篇文章：[实验驱动开发与响应式编程 ---- File Watcher的技术实现](http://blog.csdn.net/liuwenzhe2008/article/details/52185447)。

 ---------------------- 本博客所有内容均为原创，转载请注明作者和出处 -----------------------
 作者：刘文哲

 联系方式：liuwenzhe2008@qq.com

 博客：http://blog.csdn.net/liuwenzhe2008
