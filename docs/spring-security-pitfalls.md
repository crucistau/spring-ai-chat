# Spring Security + JWT 实战踩坑记录

> 本文档记录了在 Spring Boot 3.4 + Spring Security 6.x 中集成 JWT 认证时遇到的所有问题和解决方案。

---

## 问题一：请求返回 403 但 Token 明明有效

### 现象

登录成功拿到 Token，但访问 `/api/conversations`、`/api/chat/stream` 等接口全部返回 403。

### 原因

JWT Filter 只往**自定义**的 ThreadLocal 容器里放了 userId，没有设置 **Spring Security 自己的 `Authentication` 对象**。

```java
// ❌ 错误做法：只设置了自定义上下文，Spring Security 感知不到
SecurityContextHolder.setUserId(userId);  // 这是自己写的工具类
// Spring Security 的 SecurityContextHolder 里是空的！
```

Spring Security 的 `AuthorizationFilter` 检查时发现 `SecurityContext` 里没有 `Authentication`，直接拒绝访问。

### 修复

验证 Token 后，必须创建 `Authentication` 对象并写入 Spring Security 的上下文。

```java
// ✅ 正确做法：把认证信息注入到 Spring Security 的上下文
var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
```

---

## 问题二：自定义 SecurityContextHolder 与 Spring Security 冲突

### 现象

项目里自定义的 `SecurityContextHolder` 和 Spring Security 的 `SecurityContextHolder` 同名，导入时混乱。

### 原因

两个同名类存在于不同包中：
- `com.example.chat.util.SecurityContextHolder`（自定义）
- `org.springframework.security.core.context.SecurityContextHolder`（框架）

### 修复

将自定义类重命名为 `UserContext`，职责单一，避免冲突。

```java
// ✅ 重命名后清晰明了
public class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    // ...
}
```

**教训：** 取名时避免与框架同名。

---

## 问题三：SSE 流式接口返回 403 + "response is already committed"

### 现象

调用 `/api/chat/stream` 时报错：

```
AuthorizationDeniedException: Access Denied
Unable to handle the Spring Security Exception because the response is already committed.
```

### 原因链

这个问题的根因涉及三层机制，需要逐一理解：

#### 3.1 Tomcat 的异步分发机制

当 Controller 返回 `Flux<String>`（SSE 流）时，Spring MVC 会启动 Tomcat 的**异步处理模式**（Async Processing）。异步流会触发 `AsyncContext.dispatch()`，这会让 Tomcat 的 filter 链**再执行一遍**（称为 async dispatch）。

```
初始请求:     Filter链执行 → Controller返回Flux → 响应开始写入
异步分发:     Filter链再次执行 → 继续写入数据
```

#### 3.2 OncePerRequestFilter 的默认行为

`OncePerRequestFilter` 的设计初衷是保证**每个请求** filter 只执行一次，它通过检测 `DispatcherType` 来判断是否跳过：

```java
// OncePerRequestFilter 源码（简化）
private boolean skipDispatch(ServletRequest request) {
    DispatcherType dispatcherType = httpRequest.getDispatcherType();
    // ASYNC 类型默认被跳过！
    return dispatcherType == DispatcherType.ASYNC && shouldNotFilterAsyncDispatch();
}

// 默认返回 true，即：遇到 ASYNC 分发就跳过本 filter
protected boolean shouldNotFilterAsyncDispatch() {
    return true;
}
```

所以之前 `JwtAuthenticationFilter extends OncePerRequestFilter`，异步分发时直接被跳过。

#### 3.3 AuthorizationFilter 不会跳过

`AuthorizationFilter` 直接实现 `Filter` 接口（不是 `OncePerRequestFilter`），所以异步分发时**照常执行**。但它发现 `SecurityContext` 是空的（因为 JwtFilter 被跳过了），于是抛出 `AuthorizationDeniedException`。

此时 SSE 响应已经开始写入（response committed），Security 又试图返回 403 错误页，就报了 `response is already committed` 的冲突错误。

```
时间线:
  初始分发 ─── JwtFilter设置认证 ─── AuthorizationFilter通过 ─── 响应开始写入(committed)
  异步分发 ─── JwtFilter被跳过！ ─── AuthorizationFilter: 没认证！── 403 ── 但响应已提交！
```

### 修复

将 JwtAuthenticationFilter 改为直接实现 `Filter` 接口，每次 dispatch 都执行。

```java
// ✅ 不用 OncePerRequestFilter，而是直接实现 Filter
@Component
public class JwtAuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) {
        // 每次 dispatch 都会执行，重新设置认证
        // ...
        filterChain.doFilter(request, response);
    }
}
```

---

## 问题四：JWT Secret 太短

### 现象

注册时报错：

```
The specified key byte array is 32 bits which is not secure enough for any JWT HMAC-SHA algorithm.
The JWT JWA Specification (RFC 7518, Section 3.2) states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits.
```

### 原因

JJWT 库强制要求 HMAC-SHA256 的密钥至少 256 位（32 字节），而配置中 `secret: MOON` 只有 4 字节。

### 修复

对任意长度的密钥做 SHA-256 哈希，输出固定 256 位的密钥材料。

```java
private SecretKey getSigningKey() {
    byte[] keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(secret.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(keyBytes, "HmacSHA256");
}
```

无论输入是 `MOON` 还是其他任意长度，SHA-256 输出始终 256 位，满足 JJWT 要求。

---

## 问题五：Filter 中过早清理 SecurityContext

### 现象

登录后有些请求正常，有些请求 403，表现不稳定。

### 原因

```java
try {
    filterChain.doFilter(request, response);
} finally {
    UserContext.clear();
    SecurityContextHolder.clearContext();  // ❌ 过早清理！
}
```

`finally` 块在我们的 filter 返回后就执行了。但此时后续的 filter（如 `AuthorizationFilter`）还没运行。`SecurityContextHolderFilter` 会在**整个链结束**后自动清理上下文，不需要手动干预。

### 修复

```java
try {
    filterChain.doFilter(request, response);
} finally {
    UserContext.clear();  // ✅ 只清理自己的上下文
    // 不碰 Spring Security 的上下文，由框架自行管理
}
```

---

## 核心要点总结

| 要点 | 说明 |
|------|------|
| **两套上下文** | 自定义 UserContext（业务 userId）+ Spring SecurityContext（框架认证），各管各的 |
| **JWT Filter 责任** | 解析 Token → 构建 Authentication → 写入 Spring Security 的 Context |
| **不要用 OncePerRequestFilter** | SSE/异步请求会触发多次 dispatch，OncePerRequestFilter 会跳过异步分发 |
| **不要手动清理 SecurityContext** | Spring Security 的 SecurityContextHolderFilter 会自动管理生命周期 |
| **JWT 密钥要哈希** | 用 SHA-256 哈希后作为密钥，输入任意长度都满足 256 位要求 |

## Spring Security Filter 链执行顺序（参考）

```
DispatcherServlet
    │
    ▼
FilterChainProxy (Spring Security 入口)
    │
    ├── SecurityContextHolderFilter    ← 管理 SecurityContext 生命周期
    ├── CorsFilter                     ← 跨域处理
    ├── JwtAuthenticationFilter        ← 解析 JWT，设置 Authentication ★
    ├── LogoutFilter
    ├── UsernamePasswordAuthenticationFilter
    ├── RequestCacheAwareFilter
    ├── SecurityContextHolderAwareRequestFilter
    ├── AnonymousAuthenticationFilter
    ├── ExceptionTranslationFilter
    ├── AuthorizationFilter            ← 检查是否有 Authentication ★
    │
    ▼
Controller (业务处理)
```

---

*记录日期：2026-05-11*  
*技术栈：Spring Boot 3.4.5 + Spring Security 6.4.5 + JJWT 0.12.6*
