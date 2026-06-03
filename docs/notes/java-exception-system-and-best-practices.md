# Java 异常体系从 0 基础到精通与最佳实践

## 1. 异常是什么

异常是程序在运行过程中出现的非正常情况。Java 用“异常对象”来描述这些情况，并通过 `throw`、`try`、`catch`、`finally`、`throws` 等机制让程序可以：

- 发现错误：比如参数不合法、文件不存在、网络超时、数据库失败
- 中断当前流程：不再继续执行错误路径上的后续代码
- 传播错误原因：把失败原因交给更上层处理
- 恢复或兜底：记录日志、回滚事务、返回错误响应、释放资源

一句话理解：

> 异常不是“程序崩了”的同义词，而是 Java 表达和处理失败路径的一套机制。

---

## 2. Java 异常体系总览

Java 中所有异常和错误的共同父类是 `Throwable`。

```text
Throwable
├── Error
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── NoClassDefFoundError
└── Exception
    ├── IOException
    ├── SQLException
    ├── ClassNotFoundException
    └── RuntimeException
        ├── NullPointerException
        ├── IllegalArgumentException
        ├── IllegalStateException
        ├── IndexOutOfBoundsException
        ├── ClassCastException
        └── ArithmeticException
```

### 2.1 Throwable

`Throwable` 是所有可抛出对象的父类。它提供了几个核心能力：

- `getMessage()`：异常信息
- `getCause()`：异常原因
- `printStackTrace()`：打印堆栈
- `getStackTrace()`：获取堆栈数组
- `addSuppressed()`：记录被抑制的异常，常见于 `try-with-resources`

普通业务开发中很少直接继承 `Throwable`，通常继承 `Exception` 或 `RuntimeException`。

### 2.2 Error

`Error` 表示 JVM 层面、系统层面或非常严重的问题，通常程序无法也不应该主动恢复。

常见 `Error`：

- `OutOfMemoryError`：内存溢出
- `StackOverflowError`：栈溢出，常见于无限递归
- `NoClassDefFoundError`：运行时找不到类定义
- `ExceptionInInitializerError`：静态初始化失败

最佳实践：

- 不要捕获 `Error` 做业务兜底
- 不要自定义业务 `Error`
- 线上出现 `Error`，优先排查 JVM 参数、内存、依赖、类加载、递归和死循环

### 2.3 Exception

`Exception` 表示程序可以预期、可以处理或可以向上抛出的异常。

它分为两类：

- 受检异常：`Checked Exception`
- 非受检异常：`Unchecked Exception`，通常指 `RuntimeException` 及其子类

---

## 3. 受检异常与非受检异常

## 3.1 受检异常 Checked Exception

受检异常是编译器强制你处理的异常。方法内部如果可能抛出受检异常，必须：

- 使用 `try-catch` 捕获
- 或者在方法签名上使用 `throws` 声明继续向上抛

示例：

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDemo {
    public String readText(Path path) throws IOException {
        return Files.readString(path);
    }
}
```

`IOException` 是受检异常，所以调用者必须处理：

```java
try {
    String text = fileDemo.readText(Path.of("a.txt"));
    System.out.println(text);
} catch (IOException e) {
    System.err.println("读取文件失败：" + e.getMessage());
}
```

适合设计成受检异常的场景：

- 调用方可以合理恢复
- 失败是业务流程的一部分
- API 希望强制调用方感知这个失败

例如：

- 文件不存在，允许用户重新选择文件
- 网络超时，允许重试
- 第三方服务不可用，允许降级

但在现代 Java Web 项目中，业务异常通常不会设计成受检异常，因为它会污染大量方法签名。

## 3.2 非受检异常 Unchecked Exception

非受检异常指 `RuntimeException` 及其子类。编译器不会强制捕获或声明。

示例：

```java
public int divide(int a, int b) {
    return a / b;
}
```

如果 `b == 0`，会抛出：

```text
ArithmeticException: / by zero
```

常见非受检异常：

- `NullPointerException`：空指针
- `IllegalArgumentException`：参数不合法
- `IllegalStateException`：对象状态不合法
- `IndexOutOfBoundsException`：数组或集合索引越界
- `ClassCastException`：类型转换失败
- `NumberFormatException`：字符串转数字失败

适合设计成非受检异常的场景：

- 编程错误，比如空指针、参数非法、状态非法
- 业务规则失败，比如库存不足、余额不足、订单状态不允许取消
- Web 后端中希望统一由全局异常处理器转换成响应

## 3.3 两者对比

| 维度 | 受检异常 | 非受检异常 |
|---|---|---|
| 父类 | `Exception`，但不是 `RuntimeException` | `RuntimeException` |
| 编译器是否强制处理 | 是 | 否 |
| 是否需要 `throws` | 通常需要 | 不需要 |
| 典型例子 | `IOException`、`SQLException` | `NullPointerException`、`IllegalArgumentException` |
| 适合场景 | 调用者可以恢复的外部失败 | 编程错误或业务规则失败 |
| Web 项目常用程度 | 较少 | 较多 |

---

## 4. try-catch 基础语法

最基础的异常处理结构：

```java
try {
    // 可能出错的代码
} catch (ExceptionType e) {
    // 处理异常
}
```

示例：

```java
public int parseAge(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        return 0;
    }
}
```

这里的含义是：

- 正常情况：把字符串转成数字
- 异常情况：如果格式不合法，返回默认值 `0`

注意：是否应该返回默认值，取决于业务含义。如果 `0` 会掩盖错误，就不要这样做。

---

## 5. 多 catch 与捕获顺序

多个异常可以分别捕获：

```java
try {
    doSomething();
} catch (IllegalArgumentException e) {
    System.err.println("参数错误：" + e.getMessage());
} catch (IllegalStateException e) {
    System.err.println("状态错误：" + e.getMessage());
} catch (RuntimeException e) {
    System.err.println("运行时异常：" + e.getMessage());
}
```

捕获顺序必须从子类到父类。

错误示例：

```java
try {
    doSomething();
} catch (RuntimeException e) {
    // RuntimeException 已经捕获了 IllegalArgumentException
} catch (IllegalArgumentException e) {
    // 编译错误：这个 catch 永远执行不到
}
```

Java 7 开始支持多异常合并捕获：

```java
try {
    doSomething();
} catch (IllegalArgumentException | IllegalStateException e) {
    System.err.println(e.getMessage());
}
```

适合处理逻辑完全相同的异常。

---

## 6. finally：一定会执行吗

`finally` 通常用于释放资源：

```java
InputStream input = null;
try {
    input = new FileInputStream("a.txt");
    // 读取文件
} catch (IOException e) {
    // 处理异常
} finally {
    if (input != null) {
        try {
            input.close();
        } catch (IOException e) {
            // 关闭资源失败
        }
    }
}
```

`finally` 通常会执行，包括：

- `try` 正常结束
- `try` 中抛出异常
- `catch` 中再次抛出异常
- `try` 或 `catch` 中执行了 `return`

但以下情况不保证执行：

- JVM 直接退出：`System.exit(0)`
- 进程被杀死
- 机器断电
- JVM 崩溃

### 6.1 不要在 finally 中 return

错误示例：

```java
public int test() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

最终返回 `2`，`try` 中的返回值会被覆盖。

更严重的是，`finally` 中的 `return` 还会吞掉异常：

```java
public int test() {
    try {
        throw new RuntimeException("失败");
    } finally {
        return 2;
    }
}
```

调用方看不到异常，只能得到 `2`。这是非常危险的写法。

最佳实践：

- `finally` 只做资源释放
- 不要在 `finally` 中写 `return`
- 不要在 `finally` 中抛出新的业务异常

---

## 7. try-with-resources

Java 7 引入 `try-with-resources`，用于自动关闭资源。

只要对象实现了 `AutoCloseable` 或 `Closeable`，就可以放进 `try (...)`。

```java
try (InputStream input = new FileInputStream("a.txt")) {
    byte[] data = input.readAllBytes();
    System.out.println(data.length);
} catch (IOException e) {
    System.err.println("读取文件失败：" + e.getMessage());
}
```

代码离开 `try` 块时，`input.close()` 会自动执行。

多个资源也可以一起管理：

```java
try (
    InputStream input = new FileInputStream("a.txt");
    OutputStream output = new FileOutputStream("b.txt")
) {
    input.transferTo(output);
}
```

关闭顺序与声明顺序相反：

```text
先关闭 output，再关闭 input
```

最佳实践：

- 文件、流、数据库连接、HTTP 响应对象优先使用 `try-with-resources`
- 少写手动 `finally close`
- 关闭资源失败时，查看 `getSuppressed()`，因为主异常和关闭异常可能同时存在

---

## 8. throw 与 throws

## 8.1 throw：主动抛出异常对象

`throw` 用在方法内部。

```java
public void register(String username) {
    if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("用户名不能为空");
    }
}
```

## 8.2 throws：声明方法可能抛出的异常

`throws` 用在方法签名上。

```java
public String readText(String fileName) throws IOException {
    return Files.readString(Path.of(fileName));
}
```

两者区别：

| 关键字 | 位置 | 含义 |
|---|---|---|
| `throw` | 方法体内部 | 真正抛出一个异常对象 |
| `throws` | 方法签名 | 告诉调用者这个方法可能抛异常 |

---

## 9. 异常传播机制

如果当前方法不处理异常，异常会沿着调用栈向上传播。

```java
public void a() {
    b();
}

public void b() {
    c();
}

public void c() {
    throw new RuntimeException("失败");
}
```

调用链：

```text
a() -> b() -> c()
```

`c()` 抛出的异常会传播到 `b()`，再传播到 `a()`。如果最终没有任何地方捕获，线程会终止并打印堆栈。

---

## 10. 如何读懂异常堆栈

典型堆栈：

```text
Exception in thread "main" java.lang.NullPointerException: user is null
    at com.example.UserService.getName(UserService.java:18)
    at com.example.UserController.detail(UserController.java:25)
    at com.example.Application.main(Application.java:10)
```

阅读顺序：

1. 看异常类型：`NullPointerException`
2. 看异常信息：`user is null`
3. 看第一行自己项目代码：`UserService.java:18`
4. 沿着调用链往下看是谁调用了它

重点：

- 最上面的业务代码位置通常最重要
- 不要被框架堆栈吓到，先找自己包名下的代码
- `Caused by` 后面才是真正的根因时，要继续往下看

带 `Caused by` 的例子：

```text
ServiceException: 保存订单失败
    at com.example.OrderService.create(OrderService.java:42)
Caused by: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry
    at com.mysql.cj.jdbc.ClientPreparedStatement.execute(ClientPreparedStatement.java:123)
```

这里业务层抛出的是 `ServiceException`，根因是数据库唯一键冲突。

---

## 11. 常见内置异常怎么用

## 11.1 IllegalArgumentException

参数不合法时使用。

```java
public void setAge(int age) {
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("年龄必须在 0 到 150 之间");
    }
}
```

## 11.2 IllegalStateException

对象当前状态不允许执行该操作时使用。

```java
public void pay(Order order) {
    if (!order.isCreated()) {
        throw new IllegalStateException("只有待支付订单才能支付");
    }
}
```

`IllegalArgumentException` 和 `IllegalStateException` 的区别：

- 参数错了：`IllegalArgumentException`
- 状态错了：`IllegalStateException`

## 11.3 NullPointerException

空指针通常表示代码缺陷。不要用捕获空指针的方式写业务逻辑。

错误示例：

```java
try {
    return user.getName();
} catch (NullPointerException e) {
    return "";
}
```

更好的写法：

```java
if (user == null) {
    return "";
}
return user.getName();
```

或者在入口处明确拒绝：

```java
this.user = Objects.requireNonNull(user, "user 不能为空");
```

## 11.4 UnsupportedOperationException

当前对象不支持某个操作时使用。

```java
public void remove() {
    throw new UnsupportedOperationException("当前集合不支持删除");
}
```

## 11.5 TimeoutException

异步任务、并发工具或外部调用超时时常见。

```java
future.get(3, TimeUnit.SECONDS);
```

---

## 12. 自定义异常

当内置异常不能准确表达业务语义时，可以自定义异常。

## 12.1 最简单的业务异常

```java
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
```

使用：

```java
if (stock < quantity) {
    throw new BusinessException("库存不足");
}
```

## 12.2 带错误码的业务异常

实际项目更常见的写法：

```java
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

使用：

```java
throw new BusinessException("ORDER_STOCK_NOT_ENOUGH", "库存不足");
```

错误码的价值：

- 前端可以根据错误码做不同提示
- 日志和监控可以聚合同类错误
- 多语言场景可以用错误码做国际化
- 测试可以断言错误码，而不是断言中文文案

## 12.3 什么时候需要自定义异常

适合自定义：

- 需要表达明确业务失败：`OrderStatusException`
- 需要携带错误码：`BusinessException`
- 需要统一被全局异常处理器识别
- 需要区分不同层的失败：`RepositoryException`、`RemoteCallException`

不适合自定义：

- 只是为了把 `IllegalArgumentException` 换个名字
- 每个错误都建一个异常类，导致类型爆炸
- 没有额外语义，也没有额外字段

推荐策略：

- 中小型项目：一个 `BusinessException` + 错误码通常够用
- 大型项目：公共 `BusinessException` + 少量领域异常
- 基础设施层：可以用 `RemoteCallException`、`PersistenceException` 等表达边界失败

---

## 13. 异常链：不要丢根因

捕获异常后重新抛出时，要把原异常作为 `cause` 传进去。

错误示例：

```java
try {
    paymentClient.pay(request);
} catch (IOException e) {
    throw new BusinessException("支付失败");
}
```

这样会丢失根因。日志里只知道“支付失败”，不知道是网络超时、连接拒绝还是响应解析失败。

正确示例：

```java
try {
    paymentClient.pay(request);
} catch (IOException e) {
    throw new BusinessException("PAYMENT_FAILED", "支付失败", e);
}
```

最佳实践：

- 包装异常时保留 `cause`
- 异常信息描述当前层语义
- 原始异常负责提供底层细节

---

## 14. 日志与异常

## 14.1 不要重复记录同一个异常

错误示例：

```java
try {
    orderService.create(request);
} catch (Exception e) {
    log.error("创建订单失败", e);
    throw e;
}
```

如果全局异常处理器也会记录一次，这个异常就会被重复打印。

更好的策略：

- 能处理就处理并记录必要信息
- 不能处理就向上抛
- 在统一边界记录日志，比如 Controller 全局异常处理器、消息消费入口、定时任务入口

## 14.2 日志要带上下文

普通日志：

```java
log.error("创建订单失败", e);
```

更好的日志：

```java
log.error("创建订单失败，userId={}, productId={}, quantity={}",
    userId, productId, quantity, e);
```

注意：

- 日志里放定位问题需要的关键字段
- 不要记录密码、token、身份证号、银行卡号等敏感信息
- 异常对象 `e` 放在最后一个参数，日志框架才能打印完整堆栈

## 14.3 不要只打印 e.getMessage()

错误示例：

```java
log.error("支付失败：" + e.getMessage());
```

这会丢失堆栈。

正确示例：

```java
log.error("支付失败", e);
```

如果需要上下文：

```java
log.error("支付失败，orderId={}", orderId, e);
```

---

## 15. 异常和返回值怎么选

不是所有失败都应该用异常表示。

适合用返回值：

- 查询结果为空
- 判断某个条件是否成立
- 常规分支，不是错误
- 高频路径，不希望异常影响可读性和性能

示例：

```java
Optional<User> findById(Long id);

boolean existsByUsername(String username);
```

适合用异常：

- 参数非法
- 状态非法
- 业务规则不允许继续
- 外部系统失败
- 当前方法无法给出有效结果

示例：

```java
User getRequiredUser(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
}
```

判断标准：

> 如果这是调用方必须严肃处理的失败，用异常；如果这是业务上的正常可能性，用返回值。

---

## 16. Optional 与异常

`Optional` 适合表达“可能没有值”，不适合表达“为什么失败”。

适合：

```java
Optional<User> findById(Long id);
```

不适合：

```java
Optional<Order> createOrder(CreateOrderRequest request);
```

创建订单失败通常需要知道原因：库存不足、价格变化、用户被禁用、支付失败。这类场景应该使用异常或结果对象。

常用写法：

```java
User user = userRepository.findById(userId)
    .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));
```

---

## 17. Web 项目中的异常处理

在 Spring Boot 项目中，常见做法是：

- Service 层抛出业务异常
- Controller 层不写大量 `try-catch`
- 使用 `@RestControllerAdvice` 统一转换成 HTTP 响应

示例：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return ApiResponse.fail("BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.fail("INTERNAL_ERROR", "系统繁忙，请稍后重试");
    }
}
```

实际项目建议区分：

| 异常类型 | HTTP 状态 | 返回信息 |
|---|---:|---|
| 参数校验失败 | 400 | 具体参数错误 |
| 未登录 | 401 | 请先登录 |
| 无权限 | 403 | 无权限 |
| 资源不存在 | 404 | 数据不存在 |
| 业务规则失败 | 200 或 400，按团队规范 | 业务错误码和提示 |
| 系统未知异常 | 500 | 通用错误提示 |

注意：HTTP 状态码和业务错误码是两套东西。团队要统一规范，不要一个接口一种风格。

---

## 18. 参数校验异常

不要在业务代码里到处写重复校验：

```java
if (request.getName() == null || request.getName().isBlank()) {
    throw new IllegalArgumentException("名称不能为空");
}
```

Spring Boot 中可以使用 Bean Validation：

```java
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Min(value = 0, message = "年龄不能小于 0")
    private Integer age;
}
```

Controller：

```java
@PostMapping("/users")
public ApiResponse<Void> create(@Valid @RequestBody CreateUserRequest request) {
    userService.create(request);
    return ApiResponse.ok();
}
```

全局处理：

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
    String message = e.getBindingResult()
        .getFieldErrors()
        .stream()
        .findFirst()
        .map(FieldError::getDefaultMessage)
        .orElse("参数错误");

    return ApiResponse.fail("VALIDATION_FAILED", message);
}
```

最佳实践：

- DTO 入参使用注解校验
- 业务规则校验放 Service 层
- 复杂跨字段校验可以写自定义 Validator 或在 Service 层显式判断

---

## 19. 事务与异常

Spring 的声明式事务默认规则：

- 抛出 `RuntimeException` 或 `Error`：默认回滚
- 抛出受检异常：默认不回滚

示例：

```java
@Transactional
public void createOrder(CreateOrderRequest request) {
    orderRepository.save(order);

    if (stockNotEnough) {
        throw new BusinessException("STOCK_NOT_ENOUGH", "库存不足");
    }
}
```

`BusinessException` 继承 `RuntimeException`，事务会默认回滚。

如果抛出受检异常并希望回滚：

```java
@Transactional(rollbackFor = Exception.class)
public void importData() throws IOException {
    // 导入逻辑
}
```

事务异常最佳实践：

- 业务异常一般继承 `RuntimeException`
- 不要在事务方法内部捕获异常后吞掉，否则事务可能不会回滚
- 如果必须捕获并转换异常，重新抛出运行时异常
- 注意事务只对 Spring 代理调用生效，同类内部方法调用可能不触发事务

错误示例：

```java
@Transactional
public void createOrder() {
    try {
        orderRepository.save(order);
        stockService.deduct();
    } catch (Exception e) {
        log.error("创建订单失败", e);
        // 异常被吞掉，事务可能提交
    }
}
```

正确示例：

```java
@Transactional
public void createOrder() {
    try {
        orderRepository.save(order);
        stockService.deduct();
    } catch (Exception e) {
        throw new BusinessException("CREATE_ORDER_FAILED", "创建订单失败", e);
    }
}
```

---

## 20. 分层架构中的异常设计

推荐思路：

```text
Controller：不处理具体业务异常，交给全局异常处理器
Service：抛出业务异常，表达业务失败
Repository：让数据访问异常向上抛，必要时转换为基础设施异常
Client：把第三方调用失败转换成 RemoteCallException 或 BusinessException
```

示例：

```java
public void cancelOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));

    if (!order.canCancel()) {
        throw new BusinessException("ORDER_CANNOT_CANCEL", "当前订单状态不能取消");
    }

    order.cancel();
    orderRepository.save(order);
}
```

这个方法的异常语义很清楚：

- 找不到订单：业务失败
- 状态不允许取消：业务失败
- 数据库保存失败：基础设施失败，交给全局异常处理器或上层统一处理

---

## 21. 并发和异步中的异常

## 21.1 Thread 中的异常

子线程抛出的异常不会被主线程的 `try-catch` 捕获。

```java
try {
    new Thread(() -> {
        throw new RuntimeException("子线程失败");
    }).start();
} catch (Exception e) {
    // 捕获不到
}
```

处理方式：

- 在线程内部捕获
- 使用线程池和 `Future`
- 设置 `UncaughtExceptionHandler`

## 21.2 Future 中的异常

```java
Future<String> future = executor.submit(() -> {
    throw new RuntimeException("任务失败");
});

try {
    future.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    log.error("异步任务失败", cause);
}
```

真实异常被包装在 `ExecutionException` 里，要看 `getCause()`。

## 21.3 CompletableFuture 中的异常

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> remoteCall())
    .exceptionally(e -> {
        log.error("远程调用失败", e);
        return "fallback";
    });
```

也可以使用 `handle` 同时处理成功和失败：

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> remoteCall())
    .handle((result, error) -> {
        if (error != null) {
            return "fallback";
        }
        return result;
    });
```

异步异常最佳实践：

- 不要以为主线程能捕获子线程异常
- 异步任务边界要记录日志
- 使用 `getCause()` 找真实原因
- 重要任务要有超时、重试、降级和告警

---

## 22. 异常与性能

异常创建成本比普通对象高，因为需要填充堆栈信息。

不要用异常控制正常流程。

错误示例：

```java
public boolean isNumber(String text) {
    try {
        Integer.parseInt(text);
        return true;
    } catch (NumberFormatException e) {
        return false;
    }
}
```

如果这是低频输入校验，可以接受；如果在高频循环中大量执行，就不合适。

异常性能注意点：

- 异常适合表达异常路径，不适合表达普通分支
- 热路径上不要频繁创建异常
- 不要用异常实现循环跳转
- 不要为了性能牺牲可读性，先确认它真的是瓶颈

---

## 23. 常见反模式

## 23.1 吞异常

错误示例：

```java
try {
    doSomething();
} catch (Exception e) {
}
```

问题：

- 失败被隐藏
- 数据可能处于错误状态
- 线上排查没有线索

正确做法：

- 能恢复就恢复
- 不能恢复就向上抛
- 至少记录必要日志

## 23.2 捕获 Exception 后什么都返回成功

错误示例：

```java
try {
    orderService.create(request);
    return ApiResponse.ok();
} catch (Exception e) {
    return ApiResponse.ok();
}
```

这是严重问题。调用方以为成功，实际上业务已经失败。

## 23.3 直接 catch Throwable

错误示例：

```java
try {
    doSomething();
} catch (Throwable e) {
    log.error("失败", e);
}
```

问题：

- 会捕获 `OutOfMemoryError`、`StackOverflowError` 等严重错误
- 可能让系统继续在不可靠状态下运行

普通业务代码不要捕获 `Throwable`。

## 23.4 抛出过于宽泛的异常

不推荐：

```java
throw new RuntimeException("失败");
```

更好：

```java
throw new BusinessException("ORDER_CANNOT_CANCEL", "当前订单状态不能取消");
```

异常类型、错误码、信息都应该服务于定位和处理。

## 23.5 异常信息没有上下文

不推荐：

```java
throw new BusinessException("失败");
```

更好：

```java
throw new BusinessException("ORDER_NOT_FOUND", "订单不存在，orderId=" + orderId);
```

注意：返回给前端的文案不一定要包含内部 ID，可以在日志里记录更多上下文，在响应里返回用户可理解的信息。

## 23.6 用异常替代参数校验

错误示例：

```java
try {
    user.getName().trim();
} catch (NullPointerException e) {
    throw new BusinessException("用户名不能为空");
}
```

正确示例：

```java
if (user == null || user.getName() == null || user.getName().isBlank()) {
    throw new BusinessException("USERNAME_REQUIRED", "用户名不能为空");
}
```

---

## 24. 最佳实践总清单

### 24.1 设计异常

- 业务异常优先继承 `RuntimeException`
- 中小项目用统一 `BusinessException` + 错误码
- 不要为每个错误创建一个异常类
- 只有当异常类型本身有分支价值时，才新增异常类
- 包装异常时保留 `cause`
- 错误码要稳定，不要频繁变化

### 24.2 抛出异常

- 参数错误用 `IllegalArgumentException`
- 状态错误用 `IllegalStateException`
- 业务规则失败用 `BusinessException`
- 外部系统失败可包装成 `RemoteCallException` 或业务异常
- 异常信息要能帮助定位问题
- 不要抛出 `Exception`、`Throwable` 这种过宽泛的类型

### 24.3 捕获异常

- 只捕获你能处理的异常
- 不要吞异常
- 不要重复记录同一个异常
- 捕获后重新抛出时保留原始异常
- 异步任务边界必须处理异常
- 资源关闭优先使用 `try-with-resources`

### 24.4 日志

- `log.error("xxx", e)`，不要只打印 `e.getMessage()`
- 日志带关键上下文：用户 ID、订单 ID、请求 ID、外部系统名
- 异常对象放在日志参数最后
- 不要记录敏感信息
- 在系统边界统一记录未知异常

### 24.5 Web 接口

- Controller 不要堆满 `try-catch`
- 使用 `@RestControllerAdvice` 统一处理
- 参数校验失败、业务失败、系统失败要区分
- 对用户返回友好提示
- 对日志记录真实根因
- HTTP 状态码和业务错误码保持团队统一

### 24.6 事务

- 业务异常继承 `RuntimeException`，方便默认回滚
- 受检异常需要回滚时显式配置 `rollbackFor`
- 不要在事务方法里吞异常
- 捕获异常后如果业务失败，必须重新抛出

---

## 25. 一套推荐的项目异常模板

### 25.1 错误码枚举

```java
public enum ErrorCode {
    SUCCESS("0", "成功"),
    BAD_REQUEST("400", "参数错误"),
    UNAUTHORIZED("401", "请先登录"),
    FORBIDDEN("403", "无权限"),
    NOT_FOUND("404", "资源不存在"),
    INTERNAL_ERROR("500", "系统繁忙，请稍后重试"),

    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在"),
    ORDER_CANNOT_CANCEL("ORDER_CANNOT_CANCEL", "当前订单状态不能取消"),
    STOCK_NOT_ENOUGH("STOCK_NOT_ENOUGH", "库存不足");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
```

### 25.2 业务异常

```java
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }

    public String getCode() {
        return code;
    }
}
```

### 25.3 统一响应

```java
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = ErrorCode.SUCCESS.getCode();
        response.message = ErrorCode.SUCCESS.getMessage();
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        return response;
    }
}
```

### 25.4 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse(ErrorCode.BAD_REQUEST.getMessage());

        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统未知异常", e);
        return ApiResponse.fail(
            ErrorCode.INTERNAL_ERROR.getCode(),
            ErrorCode.INTERNAL_ERROR.getMessage()
        );
    }
}
```

---

## 26. 进阶理解：异常边界

写异常代码时，要先想清楚“谁负责处理”。

常见边界：

- Web 请求边界：ControllerAdvice 统一响应
- 消息消费边界：消费失败要记录、重试、进入死信队列
- 定时任务边界：单个任务失败不能让调度线程无声失败
- 线程池任务边界：异步任务异常要能被记录和感知
- 第三方调用边界：超时、重试、熔断、降级
- 数据库事务边界：失败时回滚

边界处的职责：

- 记录日志
- 转换响应
- 触发重试或降级
- 回滚事务
- 上报告警

业务内部的职责：

- 判断规则
- 抛出语义清晰的异常
- 不要过早把异常吞掉

---

## 27. 面试常问问题

### 27.1 Exception 和 Error 的区别

`Exception` 表示程序可以处理的异常情况，业务代码通常处理的是它。`Error` 表示严重的系统级错误，通常不可恢复，不建议业务代码捕获。

### 27.2 Checked Exception 和 RuntimeException 的区别

Checked Exception 编译器强制处理，适合调用方可以恢复的外部失败。`RuntimeException` 编译器不强制处理，适合编程错误、状态错误和业务规则失败。

### 27.3 finally 一定会执行吗

通常会执行，但 JVM 退出、进程被杀、断电、JVM 崩溃时不保证执行。不要在 `finally` 中写 `return`。

### 27.4 throw 和 throws 的区别

`throw` 是在方法体里真正抛出异常对象。`throws` 是在方法签名上声明这个方法可能抛出异常。

### 27.5 Spring 事务什么时候回滚

默认遇到 `RuntimeException` 和 `Error` 回滚，遇到受检异常不回滚。受检异常需要回滚时使用 `rollbackFor`。

### 27.6 为什么不建议 catch Exception

因为它范围太大，容易掩盖真正的问题。只有在系统边界、全局异常处理器、任务入口等地方才适合兜底捕获。

### 27.7 为什么包装异常时要传 cause

因为原始异常包含真正根因和堆栈。丢掉 `cause` 会让线上排查非常困难。

---

## 28. 最终记忆版

异常体系：

- `Throwable` 是根
- `Error` 是严重系统错误，通常不处理
- `Exception` 是可处理异常
- `RuntimeException` 是非受检异常，业务项目最常用
- 受检异常编译器强制处理，非受检异常不强制

异常语法：

- `try` 包住可能失败的代码
- `catch` 处理异常
- `finally` 释放资源
- `throw` 主动抛异常
- `throws` 声明异常
- `try-with-resources` 自动关闭资源

异常设计：

- 参数错：`IllegalArgumentException`
- 状态错：`IllegalStateException`
- 业务失败：`BusinessException`
- 系统未知失败：统一异常处理器兜底
- 包装异常：一定保留 `cause`

工程实践：

- 不吞异常
- 不重复打日志
- 不用异常控制正常流程
- 不在 `finally` 中 `return`
- Controller 少写 `try-catch`
- Service 抛业务异常
- 全局异常处理器统一响应
- 事务方法不要吞异常
- 异步任务边界要处理异常

一句话总结：

> 好的异常设计不是把所有错误都 catch 掉，而是让失败在正确的层级被表达、传播、记录、恢复或终止。
