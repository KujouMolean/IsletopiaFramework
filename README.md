Isletopia Framework
1、精简的IoC框架，帮助我们处理类和对象。
2、ClassScanner接口用于提供各平台加载类的方式，以下称CS
3、ClassResolver是最主要的单例枚举类，以下称CR
4、应用启动前使用CR，loadClass指定package，框架会加载所有包下的类，并应用AnotationHandler（AH）。接下来可使用addBean添加手动创建的对象。接下来resolveBean完成依赖注入、BeanHandler（BH）处理
5.resolveBean会自动实例化被@Bean标记的类（注解的注解中有@Bean也可以，例如@Service和@DisguiseEffect），并添加到容器中，会从容器中查找依赖选择可用的构造函数。
6.被@Bean注解的类中，若有被@Bean标志的方法，则改函数会被调用（若容器中有满足的对象填充参数），返回值也被加入容器。注意⚠️：同一类型的对象最多一个，否则会被置换
7、对象全部创建完成后，对@AutoInject标注的字段进行依赖注入（从容器中按类型选取）
8、注入完成后，使用BH按照优先级处理所有对象。
9、AH用于发现类，实现自动化注册。

目前利用AH/BH完成的注解有：
1、@OnebotCommand@PaperCommand，包含@Bean自动实例化并注册指令。
2、@IntervalTask，周期任务，异步
3、@InitialTask，对象处理完成后执行任务
4、@DisableTask，服务器关闭时的任务
5、@Config，从config properties读取并注入String
6、@Bean标注的的Listener会自动注册
7、@ParameterCondition、@CommandContext @CommandCompletions自动注册指令Context、Condition、Completions
8、更多见源码
