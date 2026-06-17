# Freedex Agent Java SDK

Java SDK for Freedex Agent REST API.

第一版目标是让代理商接入时不需要自己处理签名、nonce、timestamp、金额字符串和 webhook 验签。SDK 以 Java 8 为最低版本，兼容存量 Spring Boot、传统 Tomcat 和较新的 Java 11/17/21 项目。

## 要求

- Java 8+
- Maven 3.9+

## Maven

当前仓库内版本：

```xml
<dependency>
    <groupId>com.freedex.exchange</groupId>
    <artifactId>freedex-agent-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

本地安装：

```bash
mvn install
```

## 初始化

```java
import com.freedex.agent.AgentClient;
import com.freedex.agent.AgentClientConfig;

AgentClient client = new AgentClient(AgentClientConfig.builder()
        .baseUrl("https://agent.example.com")
        .agentCode("AGENT001")
        .apiSecret("your-api-secret")
        .defaultCurrency("USDT")
        .build());
```

SDK 会自动注入以下字段：

- `agentCode`
- `timestamp`
- `nonce`
- `sign`

签名算法与 agent 服务端一致：排除 `sign`，空值不参与，按 key ASCII 排序，拼接为 `k=v&k2=v2` 后计算 HMAC-SHA256 hex。

## 绑定用户

```java
import com.freedex.agent.model.BindRequest;
import com.freedex.agent.model.BindResponse;

BindResponse resp = client.bind(BindRequest.of("agent-user-001"));
System.out.println(resp.platformUserId);
```

`platformUserId` 为平台返回的用户标识，10 位纯数字字符串，例如 `1188041528`。请按字符串保存，用于后续查询、webhook 对账或页面展示；不要自行生成、拆解或推断其含义。

## 固定金额划转

```java
import com.freedex.agent.model.Direction;
import com.freedex.agent.model.TransferRequest;
import com.freedex.agent.model.TransferResponse;

TransferResponse resp = client.transfer(TransferRequest.fixed(
        "AGT-ORDER-1001",
        "agent-user-001",
        Direction.IN,
        "USDT",
        "10.00"));

System.out.println(resp.orderStatus);
System.out.println(resp.agentUserId);
System.out.println(resp.platformUserId);
```

`Direction.IN` 表示转入平台，`Direction.OUT` 表示转出平台。金额使用字符串，避免浮点精度问题。

划转用户可用 `agentUserId` 或 `platformUserId` 定位。`platformUserId` 是 `bind` 返回的 10 位 public ID：

```java
TransferResponse resp = client.transfer(TransferRequest.fixedByPlatformUserId(
        "AGT-ORDER-1001",
        "1188041528",
        Direction.IN,
        "USDT",
        "10.00"));
```

如果两个 ID 都传，必须指向同一绑定用户：

```java
TransferRequest req = TransferRequest
        .fixed("AGT-ORDER-1001", "agent-user-001", Direction.IN, "USDT", "10.00")
        .withPlatformUserId("1188041528");
```

同步响应会返回 `agentUserId` 和 `platformUserId`，其中 `platformUserId` 固定为 public ID。

## 接口返回与订单确认

SDK 调用 agent API 后，agent 服务会在主路径同步调用平台交易核心（TK）RPC，并按 TK 返回结果推进订单状态。除非 TK RPC server 超时或网络异常，否则接口返回时已经拿到 TK 同步结果。

- 返回 `orderStatus=SUCCESS`：本次划转已由 TK 同步确认成功。
- 返回 `orderStatus=FAILED` 或抛出业务异常：TK 已同步拒绝，拒绝原因以响应中的业务码和消息为准。
- 返回 `orderStatus=PROCESSING`：通常表示 TK RPC server 超时或网络异常。代理商应使用 webhook 或 `queryOrder` 查询最终状态。

真实用户和模拟用户都使用同一个 `transfer` 接口。模拟用户转入平台只影响该用户的平台余额，不会扣减代理商保证金；接口入参不需要也不允许传用户类型。

## 全部划出

```java
import com.freedex.agent.model.TransferAllOutRequest;
import com.freedex.agent.model.TransferAllOutResponse;

TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.of("AGT-ORDER-1002", "agent-user-001", "USDT"));

System.out.println(resp.amount);
System.out.println(resp.agentUserId);
System.out.println(resp.platformUserId);
```

全部划出也支持使用 `platformUserId` 定位：

```java
TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.byPlatformUserId("AGT-ORDER-1002", "1188041528", "USDT"));
```

## 查单

```java
import com.freedex.agent.model.OrderQueryType;
import com.freedex.agent.model.QueryOrderRequest;
import com.freedex.agent.model.QueryOrderResponse;

QueryOrderResponse resp = client.queryOrder(
        QueryOrderRequest.of("AGT-ORDER-1001", OrderQueryType.TRANSFER_IN));

System.out.println(resp.status);
```

## 创建前端入口链接

```java
import com.freedex.agent.model.CreateEntryUrlRequest;
import com.freedex.agent.model.CreateEntryUrlResponse;

CreateEntryUrlResponse resp = client.createEntryUrl(
        CreateEntryUrlRequest.of("agent-user-001")
                .withRedirectPath("/trade/BTCUSDT")
                .withReturnUrl("https://partner.example/return#markets"));

System.out.println(resp.webUrl);
```

`returnUrl` 可选，仅用于直接跳转模式下让 6MM 前端在用户退出时跳回代理商站点；非空时必须是 `http` 或 `https` 绝对地址。

## 创建内嵌交易 token

Trading Widget `tokenProvider` 模式使用该接口。`channelId` 必须使用 SDK 传给 Partner 前端的值，Partner 后端只负责转发并签名调用 agent API。

```java
import com.freedex.agent.model.CreateEmbedTokenRequest;
import com.freedex.agent.model.CreateEmbedTokenResponse;

CreateEmbedTokenResponse resp = client.createEmbedToken(
        CreateEmbedTokenRequest.of("agent-user-001", "tw_abc123").withSymbol("ETHUSDT"));

System.out.println(resp.embedToken);
System.out.println(resp.expireAt);
```

## 业务异常

HTTP 非 2xx 或 agent 响应 `code != 0` 时，SDK 抛 `AgentApiException`：

```java
import com.freedex.agent.AgentApiException;

try {
    client.queryOrder(QueryOrderRequest.of("missing-order", OrderQueryType.TRANSFER_IN));
} catch (AgentApiException e) {
    System.out.println(e.getHttpStatus());
    System.out.println(e.getCode());
    System.out.println(e.getResponseBody());
}
```

网络、序列化、配置错误抛 `AgentSdkException`。

## Webhook 验签

agent 服务推送 webhook 时使用请求头：

- `X-Agent-Timestamp`
- `X-Agent-Nonce`
- `X-Agent-Signature`

验签示例：

```java
import com.freedex.agent.WebhookVerifier;

boolean ok = WebhookVerifier.verify(
        "your-api-secret",
        timestampHeader,
        nonceHeader,
        rawRequestBodyBytes,
        signatureHeader);

String idempotencyKey = WebhookVerifier.idempotencyKey(rawRequestBodyBytes);
```

`idempotencyKey` 格式为 `orderType:orderId:targetStatus`，可用于代理商侧 webhook 幂等处理。

### Webhook 推送事件

Webhook 只在订单到达终态时推送，不会推送 `PROCESSING`。

| `event` | 触发场景 | 常见 `targetStatus` |
|---------|----------|---------------------|
| `transfer.completed` | 普通固定金额划转、全部划出订单到达终态；原订单被冲正后也会收到该事件 | `SUCCESS` / `FAILED` / `REVERSED` |
| `reverse.completed` | 冲正订单到达终态 | `SUCCESS` / `FAILED` |
| `transfer.dead` | 系统多次重试后仍无法确认订单最终状态，需要人工介入 | `DEAD` |

Webhook 请求体里的 `platformUserId` 与 `bind` 返回值一致；`agentUserId` 为代理商侧用户标识。代理商应以 `orderType + orderId + targetStatus` 或 SDK 生成的 `idempotencyKey` 做幂等，避免重试通知导致重复处理。

## 测试

```bash
mvn test
mvn package
```
