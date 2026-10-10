# 商户接入指南：秒合约、合约数据查询与模拟盘

适用对象：商户后端、前端及联调人员。核对日期：2026-10-10。Java 示例使用公开 Agent Java SDK `v0.3.0`；其他语言可按相同 HTTP 契约对接。

商户后端使用 Agent API 完成用户绑定、入口认证、资金操作和数据查询；商户前端使用交易页面或 Trading Widget 展示交易。秒合约与永续合约共用用户绑定和入口认证，选择不同页面即可进入对应产品。资金接口的 `environment` 与前端交易模式需要分别配置。

## 1. 当前能力与接入地址

| 能力 | 当前接入方式 | 说明 |
| --- | --- | --- |
| 秒合约跳转 | `createEntryUrl` + 对应玩法的 `redirectPath` | 支持涨跌、高低、网格预测页面 |
| 秒合约内嵌 | 通用 `createEmbedToken` + Trading Widget 对应页面 | 沿用现有认证；无需增加预测专用 token |
| 秒合约钱包 | `queryUserAssets(includeFunding=true)`、`walletTransfer`、`queryWalletTransfer` | 当前 Funding 接口使用 USDT |
| 永续合约业务数据 | 六类 `/v1/agent/perpetual/**` 查询 | 正式环境已支持；Java SDK `v0.3.0` 包含对应方法 |
| 模拟资金操作 | 支持环境字段的接口显式传 `SIMULATED` | 用户绑定不变，资金目标切换至模拟环境 |
| 模拟合约六类数据查询 | 本指南所列六接口尚无 `environment` 字段 | 不能据此查询模拟盘订单、成交、持仓或账变 |
| SDK 分发 | GitHub 稳定源码标签 `v0.3.0` | 尚未发布 Maven Central；先安装到本地或商户私服 |

正式 Agent 地址：`https://agent-api.6mm.com`。交易前端地址：`https://app.6mm.com`。商户使用平台提供的地址和权限配置，资金环境与交易模式分别按本文第 7 节设置。

商户后端通过 Java SDK 或签名 HTTP 请求调用 `/v1/agent/**`，并为自己的前端提供认证、钱包和查询接口。

本文“合约数据”指用户委托、成交、持仓和合约账变。秒合约下注记录、秒合约结算记录、Funding 账变及公开行情不属于这六类永续查询。

## 2. 商户需要准备的配置

| 配置 | 用途 |
| --- | --- |
| `agentCode`、`apiSecret` | 商户后端签名；密钥只保存在后端 |
| `baseUrl` | Agent 服务地址 |
| 接入权限 | 平台配置允许跳转、内嵌或两者；业务和资金权限按商户配置开通 |
| 交易前端地址、页面路径、Widget SDK | 由平台提供当前站点支持的前端配置 |
| 钱包目标 | `PRODUCTION` 或 `SIMULATED`，由商户服务端固定或受控选择 |
| 商户回跳地址 | 跳转模式中的 `returnUrl` |
| 回调地址 | 使用原合约划转通知时配置 `webhookUrl` |

保留三组映射：商户用户 `agentUserId` ↔ 平台公开用户 `platformUserId`；本地业务单 ↔ `agentOrderNo`；资金单 ↔ 请求环境及接口类型。对外平台用户 ID 和订单 ID 均按字符串保存，禁止使用平台内部数据库用户 ID。

初始化 Java 客户端：

```java
import com.sixmm.agent.*;
import com.sixmm.agent.model.*;
import java.time.Duration;
import java.util.*;

AgentClient client = new AgentClient(AgentClientConfig.builder()
        .baseUrl("https://agent-api.6mm.com")
        .agentCode(System.getenv("AGENT_CODE"))
        .apiSecret(System.getenv("AGENT_API_SECRET"))
        .defaultCurrency("USDT")
        .timeout(Duration.ofSeconds(10))
        .build());
```

后续 Java 片段共用以上导入和 `client`。示例 ID、单号和金额需替换为实际绑定和持久化业务单信息。

## 3. 绑定用户

首次进入交易或首次划转前调用：

```java
BindResponse binding = client.bind(
        BindRequest.of("merchant-user-001").withUsername("merchant-user"));
String platformUserId = binding.platformUserId;
```

对应接口：`POST /v1/agent/bind`。确认返回 `code=0`、`bindStatus=BOUND` 后保存映射。实盘和模拟盘共用该绑定；`bind` 请求没有 `environment` 字段。

后端从商户登录会话取得 `agentUserId`，并校验查询或资金操作针对的是当前用户。不要直接信任浏览器传入的其他用户 ID。

## 4. 秒合约页面接入

### 4.1 跳转进入

后端签发一次性入口链接，把 `webUrl` 返回前端，由浏览器立即打开：

```java
CreateEntryUrlResponse entry = client.createEntryUrl(
        CreateEntryUrlRequest.of("merchant-user-001")
                .withRedirectPath("/zh-CN/up-down?tradingMode=demo")
                .withReturnUrl("https://merchant.example/markets"));
String webUrl = entry.webUrl;
```

对应接口：`POST /v1/agent/create-entry-url`。本例进入模拟页面；实盘使用 `tradingMode=real`，并让商户钱包接口使用实盘环境。

以下为交易页面路径示例；语言、交易对和站点路径以平台提供的前端配置为准：

| 产品 | 路径 | 钱包 |
| --- | --- | --- |
| 涨跌预测 | `/zh-CN/up-down?tradingMode=real` 或 `demo` | Funding |
| 高低预测 | `/zh-CN/high-low?tradingMode=real` 或 `demo` | Funding |
| 网格预测 | `/zh-CN/grid-prediction?tradingMode=real` 或 `demo` | Funding |
| 永续合约 | `/zh-CN/futures/BTCUSDT?tradingMode=real` 或 `demo` | Contract |

`redirectPath` 是前端内部相对路径，必须以 `/` 开头；`returnUrl` 是商户绝对 HTTP/HTTPS 地址。`expireAt` 为 Unix 秒。一次性 ticket 过期或已消费后重新签发，不能长期缓存或共享给其他用户。Agent 保存目标路径；页面模式由前端处理，不会因签发入口链接自动执行资金划转。

### 4.2 SDK 内嵌进入

已有合约内嵌的商户可以复用后端签 token 和续签逻辑，并配置秒合约页面地址。前端继续使用 Widget 的 `partner-token + tokenProvider` 机制。

接入顺序：

1. 使用平台提供的 Widget SDK 创建实例，配置目标秒合约页面和实盘/模拟模式。
2. `tokenProvider` 收到该实例的 `channelId` 后，请求商户自己的认证接口。
3. 商户后端根据登录会话取得 `agentUserId`，调用通用 `createEmbedToken`。
4. 商户后端返回 `embedToken`、`expireAt`，并原样带回当前请求的 `channelId`，由前端交给 SDK。
5. 续签使用同一实例通道；重建 Widget 后使用新实例提供的通道。

后端签 token 的调用：

```java
// channelId 必须来自当前 Widget 实例。
String channelId = "tw_example_channel";
CreateEmbedTokenResponse auth = client.createEmbedToken(
        CreateEmbedTokenRequest.of("merchant-user-001", channelId));
String embedToken = auth.embedToken;
long expireAt = auth.expireAt;
```

对应接口：`POST /v1/agent/create-embed-token`。业务入参为 `agentUserId`、`channelId`，以及可选 `symbol`。秒合约不需要借用合约交易对来选择玩法。玩法、页面地址和交易模式由前端配置；token 不负责选择资金环境。

token 为短期一次性凭据，不放入 URL query，不当作用户永久登录凭据。签发或续签 token 不会充值、划转或改变余额。Java Agent SDK 负责后端认证；浏览器端另行加载 Trading Widget SDK。

#### 前端完整示例

以下参数已按公开 [Trading Widget SDK](https://app.6mm.com/sdk/trading-widget/v1/index.js) 核对（2026-10-10，线上版本 `1.7.0`）。Widget 版本与 Java SDK `v0.3.0` 是两个独立版本。

```html
<div id="seconds-widget" style="width: 100%; height: 720px"></div>
<script src="https://app.6mm.com/sdk/trading-widget/v1/index.js"></script>
<script>
  const page = 'up-down';
  const tradingMode = 'demo';
  const capabilities = TradingWidget.capabilities;
  if (!capabilities?.partnerToken ||
      !capabilities.pages?.includes(page) ||
      !capabilities.tradingModes?.includes(tradingMode)) {
    throw new Error('Trading Widget SDK does not support the requested page or mode');
  }

  const widget = TradingWidget.create('#seconds-widget', {
    baseUrl: 'https://app.6mm.com',
    page,
    tradingMode,
    theme: 'dark',
    locale: 'zh-CN',
    auth: {
      mode: 'partner-token',
      tokenProvider: async ({ channelId }) => {
        // 这是商户自行实现的接口；后端从登录会话取得用户身份。
        const response = await fetch('/api/seconds/embed-token', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ channelId }),
        });
        if (!response.ok) {
          throw new Error('Failed to request embed token');
        }
        const result = await response.json();
        if (typeof result.embedToken !== 'string' || !result.embedToken.trim() ||
            !Number.isFinite(result.expireAt) || result.channelId !== channelId) {
          throw new Error('Invalid embed token response');
        }
        return {
          embedToken: result.embedToken,
          expireAt: result.expireAt,
          channelId: result.channelId,
        };
      },
    },
    onReady() {
      console.log('Trading widget ready');
    },
    onError(error) {
      console.error('Trading widget error', error.code);
    },
  });
  window.addEventListener('beforeunload', () => widget.destroy());
</script>
```

| 选项 | 取值 | 说明 |
| --- | --- | --- |
| `page` | `up-down` / `high-low` / `grid-prediction` | 涨跌 / 高低 / 网格预测；这是页面选项，不是 token 入参 |
| `tradingMode` | `real` / `demo` | 实盘 / 模拟盘；省略时默认为 `real`，建议始终显式传入 |
| `baseUrl` | `https://app.6mm.com` | 平台前端地址；SDK 自动生成 `/embed/trading` 及页面、模式和通道参数 |

商户后端 `/api/seconds/embed-token` 接收 `{ "channelId": "tw_xxx" }`，校验当前登录会话并调用前面的 `createEmbedToken`，成功后返回以下**顶层结构**：

```json
{
  "embedToken": "short-one-time-token",
  "expireAt": 1791590700,
  "channelId": "tw_xxx"
}
```

`expireAt` 使用 Agent 返回的实际 Unix 秒时间戳，不要使用示例值；`channelId` 由商户后端原样带回当前请求值，Agent 的 token 响应本身不包含该字段。如果商户接口使用 `{ "data": ... }` 包装，前端须先取 `data` 再返回上述对象给 `tokenProvider`。业务失败应返回失败 HTTP 状态或在解析包装层时显式抛错。

SDK 在初始化和续签时调用 `tokenProvider`，每次都应请求新的 token；不要缓存并重复兑换同一个 token。用户切换或实盘/模拟盘切换时销毁旧实例，再以目标模式创建新实例。模拟页面的资金接口另行传 `environment=SIMULATED`；设置 `tradingMode` 或签 token 都不会替商户切换钱包请求的环境。

## 5. 钱包查询与划转

### 5.1 三种余额分别使用

| 钱包 | 余额来源 | 用途 |
| --- | --- | --- |
| 商户自己的用户钱包 | 商户自己的账务系统 | 用户在商户侧的可用余额 |
| 平台 Funding | `queryUserAssets` 的 `funding` | 秒合约资金及 Funding/Contract 互转 |
| 平台 Contract | `queryUserAssets` 的合约资产字段 | 永续合约保证金及可划出余额 |

`queryAccount` 查询商户平台保证金账户，不是商户用户钱包。该接口没有 `environment` 参数，不能作为模拟用户资产查询。

查询模拟用户的两个平台钱包：

```java
QueryUserAssetsResponse assets = client.queryUserAssets(
        QueryUserAssetsRequest.of(platformUserId)
                .withFunding()
                .withEnvironment(ExecutionEnvironment.SIMULATED));
```

接口：`POST /v1/agent/query-user-assets`。`includeFunding=true` 时才请求 Funding；默认沿用原合约资产查询语义。

| 响应字段 | 含义 |
| --- | --- |
| `walletBalance`、`availableBalance`、`frozenMargin`、`usedMargin`、`isolatedMargin` | 合约钱包相关金额 |
| `funding.assetCode` | 当前为 USDT |
| `funding.availableAmount`、`heldAmount`、`totalAmount` | Funding 可用、占用及总金额 |
| `funding.materialized` | Funding 账户是否已物化 |
| `environment` | 显式传环境时回显；省略环境的旧请求不返回此字段 |
| `isSimulatedUser` | 用户账户性质；不能代替交易环境选择 |

金额以字符串或 `BigDecimal` 处理，不使用 `float`/`double`。

### 5.2 Funding 的四种方向

创建：`POST /v1/agent/wallet-transfer`，SDK `walletTransfer`。

| `direction` | 资金流向 | `transferAll` |
| --- | --- | --- |
| `PARTNER_TO_FUNDING` | 商户 → 平台 Funding | 仅固定金额 |
| `FUNDING_TO_PARTNER` | 平台 Funding → 商户 | 仅固定金额 |
| `FUNDING_TO_CONTRACT` | 同环境平台 Funding → Contract | 支持固定金额或全部可划出 |
| `CONTRACT_TO_FUNDING` | 同环境平台 Contract → Funding | 支持固定金额或全部可划出 |

当前币种仅支持 USDT。固定金额必须为正数字符串，最多 8 位小数，不能用科学计数法。`transferAll=true` 时省略 `amount`，按最终响应 `amount` 入账；“全部”只包含可划出部分。

示例：商户向模拟 Funding 转入 10 USDT。先在商户系统保存该业务单和环境，再发请求：

```java
String fundingOrderNo = "SIM-FUNDING-20261010-000001";
WalletTransferResponse transfer = client.walletTransfer(
        WalletTransferRequest.of(fundingOrderNo, "merchant-user-001",
                "PARTNER_TO_FUNDING", "10.00000000", false)
                .withEnvironment(ExecutionEnvironment.SIMULATED));
```

Funding 全部可划出资金转至模拟 Contract：

```java
WalletTransferResponse move = client.walletTransfer(
        WalletTransferRequest.of("SIM-F2C-20261010-000001", "merchant-user-001",
                "FUNDING_TO_CONTRACT", null, true)
                .withEnvironment(ExecutionEnvironment.SIMULATED));
```

同一操作必须沿用原 `agentOrderNo`；不同操作和不同环境使用不同单号。同单号改金额、用户、方向或环境会被拒绝。Funding/Contract 互转仅在同一环境内进行，不能将模拟资金转为实盘资金。

### 5.3 结果确认与未知结果恢复

Funding 响应通过 `status` 表示 `SUCCESS`、`PROCESSING` 或 `FAILED`，并返回 `orderNo`、`direction`、`currency`、`amount`、`resultCode` 等。`code=0` 仅表示请求处理成功，需要继续判断 `status`。

| 结果 | 商户处理 |
| --- | --- |
| `SUCCESS` | 按实际金额完成一次本地账务处理 |
| `PROCESSING` | 保持本地待确认，查询原单 |
| HTTP 超时、网络错误、结果无法解析 | 视为结果未知，查询原单；不能直接认定失败或创建新单重试 |
| 已查明 `FAILED` | 按明确失败结果解除本地预留；需再次发起业务时新建业务单 |

Funding 查单使用专用接口 `POST /v1/agent/wallet-transfer-status`：

```java
WalletTransferResponse recovered = client.queryWalletTransfer(
        WalletTransferQueryRequest.of(fundingOrderNo)
                .withEnvironment(ExecutionEnvironment.SIMULATED));
```

该接口会恢复原资金操作状态，不能用旧 `queryOrder` 代替。建议保存原单环境，查单时显式传入相同值。`wallet-transfer-status` 省略 `environment` 时，服务端按原订单保存的环境查询和恢复，模拟单仍使用模拟环境；只有显式传入的环境与原单不一致时，才返回 `102002`（`order environment mismatch`）。省略环境时，响应也不带 `environment` 字段，不要将其解读为实盘订单。

商户本地出入账均做幂等；发起前持久化订单和余额预留，结果未知时保留待确认。Funding 接入以专用查单确认终态；不要假定旧合约划转 Webhook 或冲正方法自动适用于 Funding。

### 5.4 原合约划转继续保留

原 `transfer`、`transferAllOut`、`reverse`、`queryOrder`、`listOrders` 继续处理原合约划转流程。`queryOrder` 查询的是资金划转订单，`listOrders` 是划转对账列表；永续交易委托改用下一节的六类查询。

原资金响应使用 `orderStatus`；Funding 响应使用 `status`，不要混用。原划转 Webhook 验签使用 `X-Agent-Timestamp`、`X-Agent-Nonce`、`X-Agent-Signature`，签名原文是 `timestamp + nonce + rawBody`；先验证原始请求体，再反序列化并幂等入账。显式模拟环境通知包含 `environment=SIMULATED`，正式通知保持旧字段兼容。金额、单号、状态和环境需要与本地业务单核对。

## 6. 查询永续合约数据

### 6.1 六类接口与 Java 方法

全部使用签名 POST 请求，公共前缀为 `/v1/agent/perpetual`。

| 路径后缀 | Java 方法 / 请求类型 | 玩家范围 |
| --- | --- | --- |
| `/orders/current` | `listCurrentPerpetualOrders(CurrentPerpetualOrdersRequest)` | NORMAL 可省略玩家；CONDITIONAL 必须指定玩家 |
| `/orders/history` | `listPerpetualOrderHistory(PerpetualOrderHistoryRequest)` | 同上 |
| `/trades` | `listPerpetualTrades(PerpetualTradesRequest)` | 必须指定玩家 |
| `/positions/current` | `listCurrentPerpetualPositions(CurrentPerpetualPositionsRequest)` | 可省略玩家 |
| `/positions/history` | `listPerpetualPositionHistory(PerpetualPositionHistoryRequest)` | 可省略玩家 |
| `/account/ledger` | `listPerpetualLedger(PerpetualLedgerRequest)` | 可省略玩家 |

`platformUserIds` 是公开平台 ID 的字符串数组，指定时需 1～100 个不重复且有权访问的玩家；省略时仅查询当前商户有权访问的直属玩家范围。省略与 `[]` 不等价，空数组非法。范围由签名商户确定，不能传其他商户 ID 扩大范围。

委托查询必传 `orderCategory=NORMAL` 或 `CONDITIONAL`。普通委托使用 `orderId`，条件委托使用 `conditionId`，不能跨类别混用。条件单与其触发生成的普通委托分别按实体处理。

### 6.2 过滤条件

| 查询 | 可用业务筛选 |
| --- | --- |
| 当前委托 | `orderCategory`、`symbol` |
| 历史委托 | `orderCategory`、`symbol`、`startTime`、`endTime`、`orderId`、`conditionId`、`clientOrderId`、`side`、`status` |
| 成交 | `symbol`、`startTime`、`endTime`、`orderId` |
| 当前持仓 | `symbol` |
| 历史持仓 | `symbol`、`startTime`、`endTime`、`positionSide` |
| 合约账变 | `startTime`、`endTime`、`assetType`、`changeType` |

历史查询的时间为 Unix **毫秒**，区间为 `[startTime, endTime)`；请求签名的 `timestamp` 为 Unix **秒**。建议固定 UTC 时间窗口并分段查询；日对账采用 UTC 日切。状态和类型使用平台契约枚举，不自行翻译成中文字符串。

`limit` 默认 500，范围 1～1000；页大小不保证每次返回相同数量。六接口当前共享每商户 2 次/秒、突发 4 次的查询额度，实例内每商户并发上限 2。商户侧使用统一队列，建议从顺序分页和较小历史窗口开始。

### 6.3 Java 查询示例

查询一个玩家的当前普通委托：

```java
CurrentPerpetualOrdersRequest current = new CurrentPerpetualOrdersRequest();
current.orderCategory = "NORMAL";
current.platformUserIds = Collections.singletonList(platformUserId);
current.symbol = "BTCUSDT";
current.limit = 100;
PerpetualResponse currentPage = client.listCurrentPerpetualOrders(current);
```

查询最近一小时合约账变，并处理游标。下面示例不自动重试；实际应用遇限流或暂时故障时按第 6.5 节恢复同一页。

```java
PerpetualLedgerRequest ledger = new PerpetualLedgerRequest();
ledger.platformUserIds = Collections.singletonList(platformUserId);
ledger.endTime = System.currentTimeMillis();
ledger.startTime = ledger.endTime - 3_600_000L;
ledger.limit = 100;
Set<String> seenCursors = new HashSet<>();

while (true) {
    PerpetualResponse page = client.listPerpetualLedger(ledger);
    for (PerpetualRecord row : page.data.items) {
        // 在此按 platformUserId、entityType、entityId 幂等保存业务数据。
    }
    if (!page.data.hasMore) {
        break;
    }
    String next = page.data.nextCursor;
    if (next == null || next.isEmpty() || !seenCursors.add(next)) {
        throw new IllegalStateException("游标未推进，停止查询并排查");
    }
    ledger.cursor = next;
    Thread.sleep(650L); // 控制本查询频率；其他查询也需共享商户队列。
}
```

### 6.4 返回结构与保存规则

完整逐字段说明见 [合约查询返回字段说明](perpetual-response-fields.md)，包含公共分页、普通/条件委托、成交、当前/历史持仓、合约账变的类型、含义和适用范围。

六接口统一返回 `PerpetualResponse`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "nextCursor": "",
    "hasMore": false
  }
}
```

每条 `items` 包含 `platformUserId`、`entityType`、`entityId`、`data`。实体类型包括 `ORDER`、`CONDITION_ORDER`、`TRADE_FILL`、`POSITION`、`ACCOUNT_LEDGER`。使用这三个身份字段构建幂等键；不要将条件单 ID、委托 ID 和成交 ID 当成同一编号。

`data` 按资源提供价格、数量、状态、费用、损益或余额变化等字段。金额、价格、数量及实体 ID 按字符串处理；业务时间使用毫秒。缺失字段不补造为 0，不从其他实体推断。

游标是一次查询的分页位置，不是可靠增量订阅，也不保证跨页原子快照。当前游标从首轮生成起有效 24 小时；后续分页保留相同商户、资源、玩家集合和过滤条件。历史拉取固定窗口，重复拉取时幂等覆盖；当前委托/持仓重新查询后刷新状态。需要实时界面时使用平台前端的数据机制，不能把轮询六接口当作高频行情接口。

### 6.5 错误与恢复

HTTP 成功不代表业务成功，必须检查 `code`。Java SDK 会以 `AgentApiException` 抛出业务错误。

| 业务码 | 典型含义 | 处理 |
| --- | --- | --- |
| `102001` | 缺少必填参数，例如条件单查询缺玩家 | 补齐请求 |
| `102002` | 参数非法、空数组、游标不匹配或过期 | 修正参数；过期游标清空后重查固定窗口并幂等保存 |
| `102003` | limit、玩家数或时间范围非法 | 缩小或修正范围 |
| `6001` | 签名错误 | 检查密钥、数组序列化及签名字段 |
| `6009` | 商户接入方式或访问范围不允许 | 核对商户权限及玩家归属 |
| `6904` | 服务暂不可用、并发或查询资源保护 | 降低并发、缩小窗口；持续出现联系平台 |
| `6905` | 六类查询共享额度已耗尽 | 退避后重新调用原失败页 |

限流、超时或暂时故障后保留原失败页的 `cursor` 和所有筛选条件；每次重新调用 SDK 会生成新 `timestamp`、`nonce`、`sign`。不能直接跳到下一页，也不能把故障当作空结果。SDK 不负责自动重试资金业务。

## 7. 对接模拟盘

模拟盘有两个配置面：商户后端选择资金目标，交易前端选择交易模式及对应 HTTP/WS 数据目标。两者必须一致；token 和绑定不自动选择模拟环境。

| 项目 | 实盘 | 模拟盘 |
| --- | --- | --- |
| 绑定及入口认证 | 通用绑定、通用票据/token | 同一身份及通用认证，无需重新绑定 |
| 新资金划转及余额查询 | 省略或 `PRODUCTION` | 必须显式 `SIMULATED` |
| Funding 原单查单 `wallet-transfer-status` | 显式 `PRODUCTION`；省略时沿用原单环境 | 显式 `SIMULATED`；省略时沿用原单环境 |
| 前端模式 | `tradingMode=real` 或 SDK 对应配置 | `tradingMode=demo` 或 SDK 对应配置 |
| HTTP/WS 目标 | 实盘运行目标 | 模拟运行目标，按平台前端配置 |
| 余额及资金查单 | 实盘钱包、实盘原单 | 模拟钱包、模拟原单 |
| 本指南六类永续查询 | 查询正式环境数据 | 当前不提供模拟环境选择 |

Java `v0.3.0` 支持环境的请求类型包括 `TransferRequest`、`TransferAllOutRequest`、`ReverseOrderRequest`、`QueryOrderRequest`、`ListOrdersRequest`、`QueryUserAssetsRequest`、`WalletTransferRequest`、`WalletTransferQueryRequest`，均可使用 `withEnvironment(ExecutionEnvironment.SIMULATED)`。

商户后端须在发起、查询、恢复和对账中保持原单环境一致。模式由后端受控配置或授权选择，不能在超时后回退到省略环境。建议单号带 `SIM-` 前缀，数据库保存明确环境，并让模拟业务使用商户自己的虚拟账务流程。

`isSimulatedUser` 是账户性质；它不负责将请求路由到模拟盘。发起新资金划转或查询余额时，即使该字段为 true，省略 `environment` 仍按实盘目标处理。Funding 原单查单的环境恢复规则见第 5.3 节。

模拟秒合约联调顺序：绑定 → 显式模拟余额查询 → 小额模拟 Funding 转入 → 查询原单确认成功 → 跳转或内嵌模拟秒合约页面 → 查询模拟余额 → 需要退出资金时按模拟 Funding 单独划回。模拟永续联调可将模拟 Funding 转至模拟 Contract，然后在模拟合约页面交易；业务数据验收使用平台支持的模拟交易前端/用户接口，本指南六类商户查询不能替代它。

## 8. 旧 SDK 升级

### 8.1 是否必须升级

| 商户现状 | 升级判断 |
| --- | --- |
| 只沿用旧 SDK 原有实盘业务，不显式传 environment | 正式接口保留旧响应形状，可继续使用；不因本次查询上线被强制升级 |
| 秒合约已有通用内嵌认证 | 复用认证和续签，调整产品页面；资金新增能力按下列要求升级 |
| 要使用 Funding 余额/划转 | 使用包含 `withFunding`、`walletTransfer` 和专用查单的版本 |
| 要显式对接模拟资金环境 | 使用包含 environment 请求及响应模型的版本 |
| 要调用六类永续查询 | 使用公开 Java SDK `v0.3.0`，包含六方法和数组签名 |

升级后客户端 `baseUrl`、`agentCode`、`apiSecret` 配置方式不变；已有订单和用户映射继续保留。

### 8.2 固定稳定源码标签安装

公开 Java SDK：[6mm-com/agent-java-sdk](https://github.com/6mm-com/agent-java-sdk)。本指南固定 [v0.3.0](https://github.com/6mm-com/agent-java-sdk/tree/v0.3.0)，使用 Java 8 字节码目标。安装时核对 `HEAD` 与该标签解析出的提交一致。

在新的 SDK 源码目录安装，避免覆盖已有商户改动：

```bash
git clone --branch v0.3.0 --single-branch https://github.com/6mm-com/agent-java-sdk.git agent-java-sdk-v0.3.0
cd agent-java-sdk-v0.3.0
git rev-parse HEAD
git rev-parse 'v0.3.0^{commit}'
mvn install
```

核对两条 `git rev-parse` 输出一致后，在商户项目声明：

```xml
<dependency>
    <groupId>com.sixmm.exchange.sdk</groupId>
    <artifactId>agent</artifactId>
    <version>0.3.0</version>
</dependency>
```

该坐标来自 [v0.3.0 POM](https://github.com/6mm-com/agent-java-sdk/blob/v0.3.0/pom.xml)。GitHub 标签发布不等于 Maven Central 发布：本地 `mvn install` 后本机可以解析；CI 和其他开发机需执行同样安装，或从商户自己的制品私服获取已核对的 JAR/POM。

使用 Maven 检查依赖树，确保只有预期版本：

```bash
mvn dependency:tree -Dincludes=com.sixmm.exchange.sdk:agent
```

若旧项目已有自建 `0.3.0` 制品，核对其是否来自公开 `v0.3.0` 标签，并确认包含六类查询方法及新的请求/响应模型。共享私服中的既有同坐标制品由商户按私服策略处理，不静默覆盖。

### 8.3 升级后适配点

1. 请求/响应支持 `environment`。旧请求省略环境保持实盘兼容；显式传环境会回显环境，旧严格 JSON 模型可能不能解析，需升级模型。
2. 资产查询新增可选 Funding：使用 `withFunding()` 后读取 `funding`，避免拿 Contract 余额显示秒合约余额。
3. Funding 使用新 `/wallet-transfer`、`/wallet-transfer-status` 路径；不要调用旧拼写或原合约查单接口。
4. 六查询使用 `platformUserIds` 字符串数组和游标，签名器必须支持紧凑 JSON 数组。旧 Java `List.toString()` 格式不符合新接口签名。
5. 划转订单、永续交易委托、条件单及成交分别保存，响应状态字段按各接口契约解析。
6. 模拟环境回调增加 environment；原始请求体验签方式不变，幂等时同时校验原单环境。
7. 后端 Java SDK 与浏览器 Widget SDK 分别管理版本。后端升级不自动改变页面或前端交易模式；已有通用内嵌认证无需增加预测专用鉴权。

### 8.4 自研 HTTP 客户端

不使用 Java SDK 的商户可按 Agent HTTP 契约自行接入。新增能力需要同步适配请求字段、响应模型、环境选择、数组签名和分页逻辑；签名规则见下一节。

## 9. 非 Java 对接的签名规则

签名 POST 请求的 JSON 包含业务字段及 `agentCode`、`timestamp`、`nonce`、`sign`。删除 `sign` 和 null/空字符串字段，按字段名 ASCII 升序排列，拼接 `key=value` 并以 `&` 连接；用 `apiSecret` 计算 HMAC-SHA256，输出小写十六进制。布尔值参与签名，使用小写 `true`/`false`。

数组仍以真正 JSON 数组发送，其签名值是紧凑 JSON，例如 `platformUserIds=["1188041528","1188041529"]`；保持发送与签名的数组顺序一致，不用逗号字符串或语言默认列表显示格式代替。

当前普通委托请求示意：

```json
{
  "agentCode": "MERCHANT_CODE",
  "orderCategory": "NORMAL",
  "platformUserIds": ["1188041528"],
  "symbol": "BTCUSDT",
  "limit": 100,
  "timestamp": 1791590400,
  "nonce": "fresh-random-value",
  "sign": "computed-lowercase-hmac-sha256"
}
```

签名原文示意：

```text
agentCode=MERCHANT_CODE&limit=100&nonce=fresh-random-value&orderCategory=NORMAL&platformUserIds=["1188041528"]&symbol=BTCUSDT&timestamp=1791590400
```

调用路径为 `POST https://agent-api.6mm.com/v1/agent/perpetual/orders/current`，`Content-Type: application/json`。示例 timestamp、nonce、sign 为说明值，实际每次请求必须重新生成；分页 cursor 也参与签名。接口不接受通过传递任意内部字段扩大查询范围。

## 10. 商户联调验收

| 场景 | 通过标准 |
| --- | --- |
| 升级及旧业务兼容 | 固定 SDK 源码来源；原实盘查询省略 environment 可解析；新增方法可编译 |
| 用户绑定 | 获取并保存公开 platformUserId，其他商户玩家无法被查询 |
| 秒合约跳转 | ticket 可进入对应玩法及模式，过期/消费后重新签发 |
| 秒合约内嵌 | 既有 tokenProvider 可登录并续签，新实例使用新通道；签 token 不增加资金单 |
| Funding 固定金额 | 保存原单，确认 SUCCESS 后两端账务一致；使用实际响应金额 |
| Funding/Contract 互转 | 来源及目标钱包在同环境内变化，冻结部分不被全部划出 |
| 结果未知 | 模拟网络超时后只查原单，不产生第二笔资金操作 |
| 模拟隔离 | 所有模拟资金请求/恢复显式 SIMULATED；模拟前端 HTTP/WS 目标正确；实盘余额不变 |
| 合约六查询 | 六接口可用，必填玩家/类别、毫秒时间、空页及非空分页均正确处理 |
| 限流恢复 | 统一控制六接口速率，6905 后原页恢复，不丢页或错误推进游标 |
| 数据保存 | 字符串金额及 ID 无精度损失，实体幂等，固定 UTC 窗口可重复核对 |

资金联调使用商户确认的模拟用户与模拟账务。正式切换时同时调整后端钱包目标和前端交易模式，并核对存量待确认单仍按其原环境恢复。

## 11. 相关文档

- [合约查询返回字段说明](perpetual-response-fields.md)。
- [Java SDK v0.3.0 中文参考](https://github.com/6mm-com/agent-java-sdk/blob/v0.3.0/README.zh-CN.md)。
- [Java SDK 公开仓库](https://github.com/6mm-com/agent-java-sdk)。
