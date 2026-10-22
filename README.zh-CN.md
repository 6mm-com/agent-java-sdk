# 6MM Agent Java SDK

> 适用对象：代理商后端研发、技术支持、联调人员
> SDK：`com.sixmm.exchange.sdk:agent`
> 当前源码版本：`0.3.0`
> 最低运行环境：Java 8+

## 1. 文档目的

本文说明代理商如何在 Java 项目中接入 6MM Agent API。Java SDK 封装签名、时间戳、nonce、金额字符串、HTTP 调用、资金钱包划转、环境选择、六类永续合约数据查询、法币参考汇率和 Webhook 验签。

SDK 只应该运行在代理商后端服务中。`apiSecret` 不能下发到浏览器、App、小程序或其他客户端。

完整指南：[秒合约、钱包、模拟盘与旧 SDK 升级](docs/merchant-integration.md) · [合约查询返回字段说明](docs/perpetual-response-fields.md)。

## 2. 典型接入场景

| 分类 | 场景 | SDK 方法 | 说明 |
| --- | --- | --- | --- |
| 用户绑定 | 绑定用户 | `bind` | 为代理商用户创建或获取 6MM 平台用户绑定关系 |
| 资产查询 | 查询代理商资金 | `queryAccount` | 查询代理商保证金账户 |
| 资产查询 | 查询用户资产 | `queryUserAssets` | 查询合约资产；`withFunding()` 可同时查询资金钱包余额 |
| 资金划转 | 固定金额划转 | `transfer` | 支持转入平台和转出平台 |
| 资金划转 | 一键全部划出 | `transferAllOut` | 把用户在 6MM 侧可划出的余额划回代理商平台 |
| 资金划转 | 冲正 | `reverse` | 对已成功的划转订单发起反向处理 |
| 资金划转 | 查询订单 | `queryOrder` | 按代理商订单号查询单笔划转订单 |
| 资金划转 | 查询订单列表 | `listOrders` | 分页查询划转订单 |
| 资金划转 | Webhook 验签 | `WebhookVerifier.verify` | 校验 6MM 推送的划转终态通知 |
| 资金钱包 | 资金钱包划转 | `walletTransfer` | 商户与资金钱包增减，或资金钱包与合约钱包互转 |
| 资金钱包 | 资金钱包查单 | `queryWalletTransfer` | 按原商户单号恢复资金钱包操作结果 |
| 交易入口 | 直接跳转入口 | `createEntryUrl` | 生成一次性 SSO 跳转链接 |
| 交易入口 | 内嵌交易 token | `createEmbedToken` | 给 Trading Widget `tokenProvider` 模式签发短期 token |
| 合约数据 | 当前合约委托 | `listCurrentPerpetualOrders` | 查询普通或条件委托当前状态 |
| 合约数据 | 合约委托历史 | `listPerpetualOrderHistory` | 查询普通或条件委托终态 |
| 合约数据 | 合约成交明细 | `listPerpetualTrades` | 按显式公开玩家列表查询成交 |
| 合约数据 | 当前合约持仓 | `listCurrentPerpetualPositions` | 查询当前持仓 |
| 合约数据 | 合约持仓历史 | `listPerpetualPositionHistory` | 查询已结束持仓 |
| 合约数据 | 合约账变明细 | `listPerpetualLedger` | 查询逐条合约资金账变 |
| 法币汇率 | 查询支持法币 | `listSupportedFiatCurrencies` | 查询当前支持换算为 USDT 的法币列表 |
| 法币汇率 | 查询参考汇率 | `queryExchangeRates` | 查询全部或指定法币兑 USDT 的日级参考比例 |
| 服务信息 | 服务版本 | `version` | 查询 agent 服务版本信息 |

## 3. 接入前准备

请先从 6MM 获取以下配置：

| 配置项 | 示例 | 说明 |
| --- | --- | --- |
| `baseUrl` | `https://agent-api.6mm.com` | Agent API 服务地址，不带末尾 `/` |
| `agentCode` | `AGENT001` | 代理商编码 |
| `apiSecret` | `your-api-secret` | API 签名密钥，只能保存在后端 |
| `defaultCurrency` | `USDT` | 默认币种，通常为 `USDT` |
| `webhookUrl` | `https://partner.example.com/6mm/webhook` | 代理商接收 6MM 回调的地址 |

生产环境建议：

- `baseUrl` 使用 HTTPS。
- `apiSecret` 存放在配置中心或密钥管理系统，不写死到代码仓库。
- 服务器时间保持同步，建议使用 NTP。
- 所有订单号使用代理商侧全局唯一 ID，并持久化保存。

## 4. 依赖引入

### 4.1 Maven 坐标

当前源码中的 Maven 坐标如下：

```xml
<dependency>
    <groupId>com.sixmm.exchange.sdk</groupId>
    <artifactId>agent</artifactId>
    <version>0.3.0</version>
</dependency>
```

### 4.2 未发布到 Maven 仓库时如何使用

当前 GitHub 仓库只提供源码托管。接入方可以先通过 tag 安装到自己的本地 Maven 仓库：

```bash
git clone https://github.com/6mm-com/agent-java-sdk.git
cd agent-java-sdk
git checkout v0.3.0
mvn install
```

说明：GitHub 仓库只提供源码托管。仓库公开后，接入方可以 clone 源码并执行
`git checkout v0.3.0 && mvn install` 安装到自己的本地 Maven 仓库，但不能仅凭 GitHub 仓库地址在
`pom.xml` 中直接引用上面的依赖坐标。要让外部项目直接通过 Maven 引入，需要
额外发布到 Maven 仓库，例如公司私服、Maven Central、GitHub Packages，或接入
JitPack 这类基于 GitHub 构建的服务。

已有项目参见 [旧 SDK 升级指南](docs/merchant-integration.md#8-旧-sdk-升级)。安装与查阅接入说明均可使用稳定源码标签 `v0.3.0`。

## 资金钱包与模拟环境

资金钱包接口当前支持 USDT。`walletTransfer` 提供 `PARTNER_TO_FUNDING`、`FUNDING_TO_PARTNER`、`FUNDING_TO_CONTRACT`、`CONTRACT_TO_FUNDING` 四个方向；仅资金钱包与合约钱包互转支持 `transferAll=true`，固定金额使用正数十进制字符串。

```java
import com.sixmm.agent.model.ExecutionEnvironment;
import com.sixmm.agent.model.QueryUserAssetsRequest;
import com.sixmm.agent.model.QueryUserAssetsResponse;
import com.sixmm.agent.model.WalletTransferRequest;
import com.sixmm.agent.model.WalletTransferQueryRequest;
import com.sixmm.agent.model.WalletTransferResponse;

QueryUserAssetsResponse assets = client.queryUserAssets(
        QueryUserAssetsRequest.of("1188041528")
                .withFunding()
                .withEnvironment(ExecutionEnvironment.SIMULATED));

WalletTransferResponse transfer = client.walletTransfer(
        WalletTransferRequest.of("SIM-FUNDING-UNIQUE-001", "agent-user-001",
                "PARTNER_TO_FUNDING", "10.00000000", false)
                .withEnvironment(ExecutionEnvironment.SIMULATED));

// 结果未知或 PROCESSING 时查询原单，不换单号重新划转。
WalletTransferResponse status = client.queryWalletTransfer(
        WalletTransferQueryRequest.of("SIM-FUNDING-UNIQUE-001")
                .withEnvironment(ExecutionEnvironment.SIMULATED));
```

资金钱包创建和查单分别调用 `/v1/agent/wallet-transfer`、`/v1/agent/wallet-transfer-status`。响应按 `status=SUCCESS/PROCESSING/FAILED` 判断，不使用原合约划转的 `orderStatus`。结果未知时保留原单号和原环境查单；原 `queryOrder` 不能代替资金钱包专用查单。

有环境字段的资金请求通过 `withEnvironment` 选择：省略或 `PRODUCTION` 使用实盘，`SIMULATED` 使用模拟盘。前端交易模式及 HTTP/WS 目标需另行配置；绑定和入口认证不负责选择资金环境。六类永续查询目前查询正式数据，不接受 `environment`。

秒合约使用资金钱包，入口沿用 `createEntryUrl` / `createEmbedToken` 通用认证，由前端配置对应页面。签发或续签 token 不执行资金划转。完整流程见 [商户接入指南](docs/merchant-integration.md)。

## 5. 初始化客户端

### 5.1 普通 Java 项目

```java
import com.sixmm.agent.AgentClient;
import com.sixmm.agent.AgentClientConfig;

AgentClient client = new AgentClient(AgentClientConfig.builder()
        .baseUrl("https://agent-api.6mm.com")
        .agentCode("AGENT001")
        .apiSecret("your-api-secret")
        .defaultCurrency("USDT")
        .build());
```

### 5.2 Spring Boot 示例

```java
import com.sixmm.agent.AgentClient;
import com.sixmm.agent.AgentClientConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentSdkConfig {

    @Bean
    public AgentClient agentClient(AgentSdkProperties props) {
        return new AgentClient(AgentClientConfig.builder()
                .baseUrl(props.getBaseUrl())
                .agentCode(props.getAgentCode())
                .apiSecret(props.getApiSecret())
                .defaultCurrency("USDT")
                .timeout(Duration.ofSeconds(10))
                .build());
    }
}
```

建议业务项目通过环境变量或配置中心注入：

```properties
agent.sdk.base-url=https://agent-api.6mm.com
agent.sdk.agent-code=AGENT001
agent.sdk.api-secret=${AGENT_API_SECRET}
```

## 6. SDK 自动处理的字段

调用需要签名的 Agent API 时，SDK 会自动补充：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `agentCode` | `AgentClientConfig.agentCode` | 代理商编码 |
| `timestamp` | 当前 UTC 秒级时间戳 | Unix 时间戳，单位：秒 |
| `nonce` | 安全随机生成 | 防重放随机串 |
| `sign` | HMAC-SHA256 | 请求签名 |
| `currency` | `defaultCurrency` | 仅在划转、全部划出、代理商账户查询中空值时自动补齐 |

签名规则由 SDK 内部实现。规则是：排除 `sign`，空值不参与，按 key ASCII 升序排序，拼接为 `k=v&k2=v2`，再使用 `apiSecret` 计算 HMAC-SHA256 hex。

正常接入时不要自己计算签名，也不要手动设置 `agentCode`、`timestamp`、`nonce`、`sign`。

## 7. 法币参考汇率

SDK 提供两个已签名方法。先查询服务端当前支持的源法币：

```java
import com.sixmm.agent.model.ListSupportedFiatCurrenciesResponse;

ListSupportedFiatCurrenciesResponse supported = client.listSupportedFiatCurrencies();
System.out.println(supported.targetCurrency);     // USDT
System.out.println(supported.sourceCurrencies);  // AUD、BRL、CNY、EUR、USD 等
```

查询全部支持法币的参考汇率：

```java
import com.sixmm.agent.model.ExchangeRateItem;
import com.sixmm.agent.model.QueryExchangeRatesResponse;

QueryExchangeRatesResponse rates = client.queryExchangeRates();
for (ExchangeRateItem item : rates.rates) {
    System.out.println("1 " + item.sourceCurrency + " = " + item.rate + " " + item.targetCurrency);
}
```

只查询指定法币：

```java
import com.sixmm.agent.model.QueryExchangeRatesRequest;

QueryExchangeRatesResponse rates = client.queryExchangeRates(
        QueryExchangeRatesRequest.of("CNY", "EUR", "USD"));
```

请求工厂会去除首尾空格并转换为大写，再按服务端契约序列化为逗号分隔的 `sourceCurrencies`。不传请求或不传有效币种时查询全部支持法币。

| 响应字段 | 类型 | 说明 |
| --- | --- | --- |
| `snapshotVersion` | string | 可用于审计和对账的汇率快照版本 |
| `provider` | string | 数据提供方，当前为 `ECB` |
| `sourceDate` | string | 上游汇率数据日期，格式 `yyyy-MM-dd` |
| `fetchedAt` / `expiresAt` | int64 | Unix 毫秒时间戳 |
| `pricingPolicy` | string | 当前为 `FIXED_PEG` |
| `usdtUsdRate` | string | 每 1 USDT 对应的 USD 数量，当前固定为 `1` |
| `rateType` | string | 当前为 `INDICATIVE_DAILY` |
| `usage` | string | 当前为 `REFERENCE_ONLY` |
| `rateMeaning` | string | `1 sourceCurrency = rate USDT` |
| `rates[].rate` | string | 每 1 单位源法币对应的 USDT 数量 |

这些汇率用于代理商多币种钱包换算后的 USDT 入参参考，不是成交价、锁价或实时 USDT 市场报价。金额计算请使用 `BigDecimal` 和字符串，不要使用 `double` 或 `float`。

## 8. 用户 ID 规则

6MM 对外返回的 `platformUserId` 是平台对外用户 ID，当前为 10 位纯数字字符串，例如：

```text
1188041528
```

要求：

- 按字符串保存，不要按整数保存，避免不同语言或数据库类型带来精度、长度或前导零问题。
- 不要自行生成、拆解或推断 `platformUserId` 的含义。
- 代理商系统自己的用户 ID 使用 `agentUserId`。
- 划转、查单、Webhook 对账时优先保存 `agentUserId`、`platformUserId`、`agentOrderNo` 的映射关系。

## 9. 绑定用户

绑定用于建立代理商用户和 6MM 平台用户之间的关系。推荐在用户第一次进入交易场景或第一次充值到 6MM 前调用。

```java
import com.sixmm.agent.model.BindRequest;
import com.sixmm.agent.model.BindResponse;

BindResponse resp = client.bind(
        BindRequest.of("agent-user-001")
                .withUsername("alice")
                .withExt("{\"source\":\"web\"}"));

System.out.println(resp.platformUserId);
System.out.println(resp.bindStatus);
System.out.println(resp.isSimulatedUser);
```

### 9.1 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `agentUserId` | string | 是 | 代理商侧用户唯一标识 |
| `username` | string | 否 | 代理商侧用户展示名称，用作平台用户昵称 |
| `ext` | string | 否 | 扩展信息，建议传 JSON 字符串 |

### 9.2 响应字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `platformUserId` | string | 6MM 对外用户 ID |
| `bindStatus` | string | 绑定状态，通常为 `BOUND` |
| `isSimulatedUser` | boolean | 是否模拟用户 |

## 10. 固定金额划转

固定金额划转使用 `transfer`。`Direction.IN` 表示从代理商侧转入 6MM，`Direction.OUT` 表示从 6MM 转出到代理商侧。

金额必须使用字符串或 `BigDecimal`，不要使用 `double` 或 `float`。

### 10.1 使用 agentUserId 划转

```java
import com.sixmm.agent.model.Direction;
import com.sixmm.agent.model.TransferRequest;
import com.sixmm.agent.model.TransferResponse;

TransferResponse resp = client.transfer(TransferRequest.fixed(
        "AGT-ORDER-1001",
        "agent-user-001",
        Direction.IN,
        "USDT",
        "10.00"));

System.out.println(resp.orderNo);
System.out.println(resp.orderStatus);
System.out.println(resp.agentUserId);
System.out.println(resp.platformUserId);
```

### 10.2 使用 platformUserId 划转

```java
TransferResponse resp = client.transfer(TransferRequest.fixedByPlatformUserId(
        "AGT-ORDER-1002",
        "1188041528",
        Direction.OUT,
        "USDT",
        "5.00"));
```

### 10.3 同时传 agentUserId 和 platformUserId

如果同时传两个 ID，服务端会校验它们必须指向同一个绑定用户。

```java
TransferRequest req = TransferRequest
        .fixed("AGT-ORDER-1003", "agent-user-001", Direction.IN, "USDT", "20.00")
        .withPlatformUserId("1188041528");

TransferResponse resp = client.transfer(req);
```

### 10.4 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `agentOrderNo` | string | 是 | 代理商侧订单号，必须全局唯一 |
| `agentUserId` | string | 条件必填 | 与 `platformUserId` 二选一 |
| `platformUserId` | string | 条件必填 | 与 `agentUserId` 二选一 |
| `direction` | enum | 是 | `IN` 或 `OUT` |
| `currency` | string | 否 | 空值时使用 `defaultCurrency` |
| `amount` | string | 是 | 金额字符串 |

### 10.5 响应字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `orderNo` | string | 平台返回的订单号 |
| `orderStatus` | string | `SUCCESS` / `FAILED` / `PROCESSING` 等 |
| `agentUserId` | string | 代理商用户 ID |
| `platformUserId` | string | 6MM 对外用户 ID |

## 11. 订单确认与幂等

SDK 调用 `transfer` 后，agent 服务会在主路径同步调用交易核心。大多数情况下，接口返回时已经拿到交易核心的同步结果。

| 返回结果 | 含义 | 代理商处理建议 |
| --- | --- | --- |
| `SUCCESS` | 划转已成功 | 更新本地订单为成功 |
| `FAILED` | 划转被拒绝或失败 | 保存失败原因，必要时展示给用户 |
| `PROCESSING` | 交易核心超时或网络异常，结果待确认 | 不要重复换订单号重试，使用 Webhook 或 `queryOrder` 查询最终状态 |

幂等要求：

- 同一笔业务只使用一个 `agentOrderNo`。
- 如果 HTTP 超时或网络异常，优先用相同 `agentOrderNo` 查询订单，不要直接生成新订单号重复提交。
- Webhook 可能因重试被推送多次，代理商侧必须按幂等键去重。

## 12. 一键全部划出

一键全部划出用于把用户在 6MM 侧可划出的余额全部转回代理商侧。

```java
import com.sixmm.agent.model.TransferAllOutRequest;
import com.sixmm.agent.model.TransferAllOutResponse;

TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.of("AGT-ORDER-2001", "agent-user-001", "USDT"));

System.out.println(resp.orderStatus);
System.out.println(resp.amount);
```

使用 `platformUserId`：

```java
TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.byPlatformUserId("AGT-ORDER-2002", "1188041528", "USDT"));
```

响应里的 `amount` 是实际划出金额。

## 13. 冲正

冲正用于对已成功的划转订单发起反向处理。常见场景包括代理商本地订单异常、人工对账需要回滚等。

```java
import com.sixmm.agent.model.ReverseOrderRequest;
import com.sixmm.agent.model.ReverseOrderResponse;

ReverseOrderResponse resp = client.reverse(ReverseOrderRequest.of(
        "AGT-ORDER-1001",
        "REV-ORDER-1001",
        "partner order rollback"));

System.out.println(resp.orderStatus);
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `origOrderNo` | string | 是 | 原始划转订单号 |
| `reverseOrderNo` | string | 是 | 冲正订单号，必须全局唯一 |
| `reverseReason` | string | 是 | 冲正原因 |

注意：

- 冲正不是普通取消订单，只适用于服务端支持的反向处理场景。
- 冲正也应按订单号做幂等。
- 原订单被冲正后，Webhook 可能推送原订单的 `REVERSED` 终态。

## 14. 查单

```java
import com.sixmm.agent.model.OrderQueryType;
import com.sixmm.agent.model.QueryOrderRequest;
import com.sixmm.agent.model.QueryOrderResponse;

QueryOrderResponse resp = client.queryOrder(
        QueryOrderRequest.of("AGT-ORDER-1001", OrderQueryType.TRANSFER_IN));

System.out.println(resp.status);
System.out.println(resp.failReason);
```

`orderType` 支持：

- `TRANSFER_IN`
- `TRANSFER_OUT`

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `orderType` | string | 订单类型 |
| `orderNo` | string | 代理商订单号 |
| `status` | string | 订单状态 |
| `direction` | string | `IN` / `OUT` |
| `transferMode` | string | `FIXED_AMOUNT` / `ALL_OUT` |
| `currency` | string | 币种 |
| `amount` | string | 金额 |
| `agentUserId` | string | 代理商用户 ID |
| `platformUserId` | string | 6MM 对外用户 ID |
| `failReason` | string | 失败原因 |
| `createdAt` | string | 创建时间 |
| `completedAt` | string | 完成时间 |

## 15. 订单列表

```java
import com.sixmm.agent.model.ListOrdersRequest;
import com.sixmm.agent.model.ListOrdersResponse;

ListOrdersRequest req = ListOrdersRequest.page(1, 20);
req.orderType = "TRANSFER_IN";
req.status = "SUCCESS";
req.startTime = "2026-06-01T00:00:00Z";
req.endTime = "2026-06-16T00:00:00Z";

ListOrdersResponse resp = client.listOrders(req);

System.out.println(resp.total);
resp.orders.forEach(order -> System.out.println(order.orderNo));
```

分页建议：

- `page` 从 1 开始。
- `pageSize` 建议不超过 100。
- 对账任务建议按时间窗口分页拉取，并以 `orderNo` 去重。

## 16. 查询代理商资金

```java
import com.sixmm.agent.model.QueryAccountRequest;
import com.sixmm.agent.model.QueryAccountResponse;

QueryAccountResponse resp = client.queryAccount(QueryAccountRequest.of("USDT"));

System.out.println(resp.agentCode);
System.out.println(resp.agentStatus);
resp.assets.forEach(asset -> {
    System.out.println(asset.currency);
    System.out.println(asset.depositBalance);
});
```

`currency` 为空时，SDK 会自动使用 `defaultCurrency`。

## 17. 查询用户资产

```java
import com.sixmm.agent.model.QueryUserAssetsRequest;
import com.sixmm.agent.model.QueryUserAssetsResponse;

QueryUserAssetsResponse resp = client.queryUserAssets(
        QueryUserAssetsRequest.of("1188041528"));

System.out.println(resp.walletBalance);
System.out.println(resp.availableBalance);
System.out.println(resp.version);
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `walletBalance` | string | 钱包余额 |
| `frozenMargin` | string | 冻结保证金 |
| `usedMargin` | string | 已用保证金 |
| `availableBalance` | string | 可用余额 |
| `isolatedMargin` | string | 逐仓保证金 |
| `version` | int64 | 资产版本号 |
| `isSimulatedUser` | boolean | 是否模拟用户 |

## 18. 直接跳转入口

直接跳转入口适合用户从代理商站点点击按钮后，打开 6MM 前端并自动登录。

```java
import com.sixmm.agent.model.CreateEntryUrlRequest;
import com.sixmm.agent.model.CreateEntryUrlResponse;

CreateEntryUrlResponse resp = client.createEntryUrl(
        CreateEntryUrlRequest.of("agent-user-001")
                .withRedirectPath("/zh-CN/futures/BTCUSDT?tradingMode=real")
                .withReturnUrl("https://partner.example.com/return#markets"));

System.out.println(resp.webUrl);
System.out.println(resp.expireAt);
```

字段规则：

| 字段 | 说明 |
| --- | --- |
| `redirectPath` | 6MM 前端内部相对路径，必须以 `/` 开头，例如 `/zh-CN/futures/BTCUSDT?tradingMode=real` |
| `returnUrl` | 用户在 6MM 前端退出时回跳的代理商绝对 URL，只允许 `http` 或 `https` |
| `expireAt` | 入口 ticket 过期时间，Unix 秒级时间戳 |

本例进入永续合约实盘页面。模拟盘使用 `tradingMode=demo`，并在对应钱包请求中显式选择 `SIMULATED`。创建入口链接只负责用户认证，不执行资金划转，也不替商户选择钱包环境。

安全要求：

- `webUrl` 内含一次性 ticket，建议立即使用，不要长期存储。
- 入口链接过期或已使用后，用户需要重新从代理商站点发起。
- `returnUrl` 不允许使用 `javascript:`、相对路径或带 userinfo 的 URL。

## 19. 内嵌交易 token

内嵌交易适用于 Trading Widget SDK 的 `partner-token + tokenProvider` 模式。浏览器端调用代理商自己的后端，代理商后端再调用 Java SDK 创建 `embedToken`。

```java
import com.sixmm.agent.model.CreateEmbedTokenRequest;
import com.sixmm.agent.model.CreateEmbedTokenResponse;

CreateEmbedTokenResponse resp = client.createEmbedToken(
        CreateEmbedTokenRequest.of("agent-user-001", "tw_abc123")
                .withSymbol("ETHUSDT"));

System.out.println(resp.embedToken);
System.out.println(resp.expireAt);
```

对接要求：

- `channelId` 必须使用 Trading Widget SDK 传给代理商前端的值。
- `symbol` 可选，建议传当前 iframe 初始交易对。
- `embedToken` 是短期一次性 token，不能放到 URL query 中。
- 代理商前端不能持有 `apiSecret`，只能请求代理商后端。
- `expireAt` 是 Unix 秒级时间戳，不是 TTL。

代理商后端接口示例：

```java
@PostMapping("/api/trading/embed-token")
public EmbedTokenView createEmbedToken(@RequestBody EmbedTokenRequest request,
                                       PartnerSession session) {
    String agentUserId = session.getAgentUserId();
    CreateEmbedTokenResponse resp = agentClient.createEmbedToken(
            CreateEmbedTokenRequest.of(agentUserId, request.getChannelId())
                    .withSymbol(request.getSymbol()));

    return new EmbedTokenView(resp.embedToken, resp.expireAt, request.getChannelId());
}
```

## 20. Webhook 验签

6MM 在划转订单到达终态时会向代理商配置的 `webhookUrl` 推送通知。SDK 提供验签和幂等键辅助方法。

```java
import com.sixmm.agent.WebhookVerifier;

boolean ok = WebhookVerifier.verify(
        "your-api-secret",
        timestampHeader,
        nonceHeader,
        rawRequestBodyBytes,
        signatureHeader);

if (!ok) {
    throw new SecurityException("invalid 6mm webhook signature");
}

String idempotencyKey = WebhookVerifier.idempotencyKey(rawRequestBodyBytes);
```

Webhook 请求头：

| Header | 说明 |
| --- | --- |
| `X-Agent-Timestamp` | Unix 秒级时间戳 |
| `X-Agent-Nonce` | 随机串 |
| `X-Agent-Signature` | HMAC-SHA256 签名 |

签名原文：

```text
timestamp + nonce + rawBody
```

建议代理商侧额外做：

- 拒绝时间戳与当前时间相差超过 5 分钟的请求。
- 缓存 nonce，避免重放。
- 资金事件按 `WebhookVerifier.idempotencyKey(rawBody)` 做幂等；该辅助方法目前对强平预警返回空串，预警须单独按通知 ID 处理。
- 返回 HTTP 2xx 表示处理成功；非 2xx 会触发 6MM 重试。

完整四类事件、强平预警字段、新旧事件兼容、成功/失败响应例子见 [Webhook 对接说明](WEBHOOKS.md)。强平预警事件为 `liquidation.warning`（历史 `LIQUIDATION_WARNING`），验签并核对商户/环境后，业务代码按 `liquidation.warning:<notificationId>` 持久去重；不要使用辅助方法返回的空串。

平台只看 HTTP 状态码，不解析响应正文；`200 + {"success":false}` 仍会被确认成功，204 空正文也可确认。业务处理或可靠保存通知后再返回 2xx，同正文重复通知也返回 2xx。202 仅适用于已经可靠持久接管的任务。响应丢失等情况仍可能造成重复投递，需要持久去重。本次只更新文档，SDK 代码、版本号和 tag 不变。

## 21. 异常处理

SDK 异常分为两类：

| 异常 | 场景 | 处理建议 |
| --- | --- | --- |
| `AgentApiException` | HTTP 非 2xx，或 agent 响应 `code != 0` | 读取 `getHttpStatus()`、`getCode()`、`getResponseBody()`，按业务失败处理 |
| `AgentSdkException` | 网络、序列化、配置、签名计算异常 | 记录日志并按系统异常处理 |

示例：

```java
import com.sixmm.agent.AgentApiException;
import com.sixmm.agent.AgentSdkException;

try {
    TransferResponse resp = client.transfer(TransferRequest.fixed(
            "AGT-ORDER-1001", "agent-user-001", Direction.IN, "USDT", "10.00"));
    // 根据 resp.orderStatus 更新本地订单
} catch (AgentApiException e) {
    // 业务失败或 HTTP 错误
    log.warn("6mm agent api rejected request, httpStatus={}, code={}, body={}",
            e.getHttpStatus(), e.getCode(), e.getResponseBody());
} catch (AgentSdkException e) {
    // SDK 本地错误或网络错误
    log.error("6mm agent sdk request failed", e);
}
```

## 22. 推荐业务流程

### 22.1 用户首次进入交易

```text
1. 用户登录代理商站点
2. 代理商后端用 agentUserId 调 bind
3. 保存 agentUserId 与 platformUserId 绑定关系
4. 用户点击进入交易
5. 直接跳转：调用 createEntryUrl，浏览器打开 webUrl
6. 内嵌交易：前端 Trading Widget 触发 tokenProvider，后端调用 createEmbedToken
```

### 22.2 用户充值到 6MM

```text
1. 代理商生成唯一 agentOrderNo
2. 调 transfer(Direction.IN)
3. 如果返回 SUCCESS，更新订单成功
4. 如果返回 PROCESSING，等待 Webhook 或轮询 queryOrder
5. 如果返回 FAILED 或抛 AgentApiException，按失败处理
```

### 22.3 用户从 6MM 提回代理商

```text
1. 代理商生成唯一 agentOrderNo
2. 固定金额提回：transfer(Direction.OUT)
3. 全部提回：transferAllOut
4. 按 orderStatus、Webhook、queryOrder 确认最终状态
```

## 23. 日志与排查建议

建议代理商后端日志至少记录：

- `agentOrderNo`
- `agentUserId`
- `platformUserId`
- `direction`
- `currency`
- `amount`
- `orderStatus`
- `AgentApiException.code`
- `requestId`（如果响应体中包含）

不要记录完整 `apiSecret`、完整签名、用户敏感资料。

## 24. 联调检查清单

| 检查项 | 期望结果 |
| --- | --- |
| `client.version()` | 能返回服务名、版本、提交号 |
| `bind` | 返回 `platformUserId` 和 `BOUND` |
| `transfer IN` 小额转入 | 返回 `SUCCESS` 或可通过 `queryOrder` 查到最终成功 |
| `transfer OUT` 小额转出 | 返回 `SUCCESS` 或可通过 `queryOrder` 查到最终成功 |
| 重复提交同一 `agentOrderNo` | 不产生重复资金变动 |
| Webhook 验签 | 合法签名通过，篡改 body 后失败 |
| Webhook 幂等 | 同一通知重复投递不会重复入账 |
| `createEntryUrl` | 浏览器打开后能进入 6MM 页面 |
| `createEmbedToken` | Trading Widget 能完成内嵌登录 |
| `listSupportedFiatCurrencies` | 返回非空源法币列表，目标币种为 `USDT` |
| `queryExchangeRates` | 返回 `REFERENCE_ONLY`、有效快照版本和所请求币种 |

## 25. 常见问题

### Q1：`platformUserId` 可以用 long 保存吗？

不建议。请按字符串保存。当前看起来是 10 位数字，但对外契约是字符串。

### Q2：接口超时后可以换一个订单号重试吗？

不建议。先用原 `agentOrderNo` 调 `queryOrder`。只有确认原订单不存在或已失败，才由业务侧决定是否生成新订单。

### Q3：`PROCESSING` 是失败吗？

不是。它表示结果待确认。代理商应等待 Webhook 或主动查单。

### Q4：为什么金额要用字符串？

避免浮点精度问题。Java 可使用 `BigDecimal`，SDK 会转为普通十进制字符串。

### Q5：Webhook 收到多次怎么办？

这是正常的重试机制。请按 `orderType:orderId:targetStatus` 幂等处理。

### Q6：Trading Widget 的 `tokenProvider` 可以在浏览器里直接调 agent API 吗？

不可以。浏览器不能持有 `apiSecret`。正确方式是浏览器请求代理商后端，代理商后端调用 `createEmbedToken`。

## 26. 本地测试命令

在 SDK 仓库根目录运行：

```bash
mvn test
mvn package
```

如果业务项目使用本地安装方式：

```bash
mvn clean install
```

## 永续批量查询（0.3.0）

正式环境已支持六类查询；平台提供的其他接入地址需单独确认可用能力。六类方法均为 `/v1/agent/perpetual` 下的签名 POST：

| 方法 | 后缀 | 玩家参数 |
| --- | --- | --- |
| `listCurrentPerpetualOrders` | `/orders/current` | NORMAL 可省略，CONDITIONAL 必传 |
| `listPerpetualOrderHistory` | `/orders/history` | NORMAL 可省略，CONDITIONAL 必传 |
| `listPerpetualTrades` | `/trades` | 必传 |
| `listCurrentPerpetualPositions` | `/positions/current` | 可省略 |
| `listPerpetualPositionHistory` | `/positions/history` | 可省略 |
| `listPerpetualLedger` | `/account/ledger` | 可省略 |

`platformUserIds` 传 1～100 个不重复的十位公开平台用户 ID 字符串，不能传内部 user_id。在支持的接口省略时查询签名商户的全部直属玩家；显式空数组无效。委托接口必传 `orderCategory=NORMAL/CONDITIONAL`，商户归属由鉴权决定，不接受自行指定 Agent ID。

历史时间为 Unix 毫秒半开区间 `[startTime,endTime)`，分别按订单创建、成交、平仓、账变记录创建时间筛选；当前委托/仓位不接受历史时间。普通委托时间表示记录创建/更新时间。limit 默认500、最大1000，保持筛选条件不变，带 nextCursor 翻页直至 hasMore=false；中间空页也要按 hasMore 继续。不承诺跨页快照或完整增量同步。

SDK 自动按紧凑 JSON 数组签名，业务ID与金额保留字符串。当前普通委托省略杠杆、保证金模式；缺省字段不应解释为0。资金账变按明细ID返回，同一流水号可以有多条明细。

各资源的字段类型、状态枚举、保证金模式及缺省规则见 [合约查询返回字段说明](docs/perpetual-response-fields.md)。

六接口共享商户频率额度，默认每秒2次、突发4次，并受并发保护。6905表示频率限制，6904表示暂不可用或并发保护；错误通过 AgentApiException 透传，SDK不会自动重试。业务层决定有限退避后，可保留原游标/筛选条件重新调用SDK，SDK生成新的timestamp/nonce/sign；多个进程也要共享节流策略。

英文README提供成交分页示例。原资金接口的environment参数保留；这六类查询不接受environment。历史留存及异步入库会影响结果，不应将单页成功等同于完整对账。
