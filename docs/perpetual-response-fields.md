# Agent 合约查询返回字段说明

适用接口：`POST /v1/agent/perpetual/orders/current`、`orders/history`、`trades`、`positions/current`、`positions/history`、`account/ledger`。本文按 2026-10-10 正式实现解释字段，与 [商户接入指南](merchant-integration.md) 配合使用。

以下是业务查询返回，不包含秒合约下注、秒合约结算或 Funding 钱包流水。Java SDK 将六接口解析为 `PerpetualResponse`；HTTP 账变接口使用独立的精简 `data` 字段集合。

## 1. 公共响应与分页

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 业务码，0 为成功；非 0 时按错误处理，不把失败当作空页 |
| `message` | string | 结果描述，不作为业务判断唯一依据 |
| `data` | object | 成功时的分页结果 |
| `data.items` | array | 本页记录；合法空页为 `[]` |
| `data.nextCursor` | string | 下一页游标；没有后续页时通常为空字符串 |
| `data.hasMore` | boolean | 是否存在后续页；true 时使用 nextCursor 继续相同查询 |

每条 `data.items[]` 的结构：

| 字段 | JSON 类型 | 说明 |
| --- | --- | --- |
| `platformUserId` | string | 平台公开用户 ID，保存为字符串；不是内部数据库用户 ID |
| `entityType` | string | 记录所属实体类型，见下表 |
| `entityId` | string | 当前实体的标识，与平台用户、实体类型组合构建本地幂等键 |
| `data` | object | 该实体的业务字段；不同资源字段不同 |

| 查询资源 | `entityType` | `entityId` 对应关系 |
| --- | --- | --- |
| 普通委托 | `ORDER` | `data.orderId` |
| 条件委托 | `CONDITION_ORDER` | `data.conditionId` |
| 成交明细 | `TRADE_FILL` | `data.fillId`，不是 tradeId |
| 当前/历史持仓 | `POSITION` | `data.positionId` |
| 合约账变明细 | `ACCOUNT_LEDGER` | 单条账变明细标识，**不是 data.ledgerId** |

同一个 `ledgerId` 可以拆出手续费、盈亏等多条明细；按 ledgerId 去重会丢数据。不同资源的 ID 不能混用。分页和字段保存规则见接入指南第 6 节。

## 2. 类型、单位与缺省行为

- 金额、价格、数量、实体 ID 使用 string；用十进制处理金额，不能使用浮点数或将长 ID 转为 JavaScript Number。金额币种和数量单位按交易对/合约规格解释。
- 业务时间使用 integer（int64），均为 Unix 毫秒；请求认证 timestamp 和入口凭据 expireAt 使用秒，不能混淆。
- 字符串业务字段为空时通常不输出，数值字段为 0 时有些会被省略。**字段缺失不代表业务值为 0**。
- 通用记录模型中的 `reduceOnly`、`closeOnly`、`makerOnly` 不省略 false，因此成交或持仓也可能出现这三个 false。只在委托资源中解释它们；不能据此认为成交/持仓执行了对应委托规则。
- `closePosition` 是可选 boolean：条件委托会提供，其他资源通常省略。缺失与 false 含义不同。
- 下表“当前提供”指读取逻辑具有字段来源；可选字段仍可能因没有值而省略。模型里存在的字段不保证每个接口都会填充。

## 3. 普通委托：当前与历史

当前：`orders/current` + `orderCategory=NORMAL`；历史：`orders/history` + `orderCategory=NORMAL`。

| `data` 字段 | 类型 | 含义与适用范围 |
| --- | --- | --- |
| `symbol` | string | 交易对，如 BTCUSDT |
| `orderId` | string | 平台普通委托 ID |
| `orderCategory` | string | 固定 NORMAL |
| `clientOrderId` | string | 交易订单来源提供的客户端单号；与商户资金划转 agentOrderNo 不是同一字段 |
| `orderType` | string | 委托类型，例如 LIMIT、MARKET；以实际枚举值为准 |
| `side` | string | 买卖方向 BUY / SELL |
| `status` | string | 普通委托状态，见第 8 节 |
| `timeInForce` | string | 有效期/成交执行策略，例如 GTC、IOC、FOK；按实际值解释 |
| `price` | string | 委托价格；未提供价格时可能省略，不自行补市场成交价 |
| `quantity` | string | 原始委托数量 |
| `filledQuantity` | string | 已成交数量 |
| `remainingQuantity` | string | 未成交数量；当前查询取 quantity − filledQuantity，历史查询取历史记录值 |
| `avgFilledPrice` | string | 已成交部分的平均成交价；未成交时可能没有值 |
| `reduceOnly` | boolean | 是否仅减少持仓 |
| `closeOnly` | boolean | 是否仅用于平仓 |
| `makerOnly` | boolean | 是否仅作为 Maker 执行 |
| `leverage` | integer | 杠杆倍数；**历史普通委托**有来源，当前普通委托不补造此值 |
| `marginMode` | string | 保证金模式；**历史普通委托**目前返回数字枚举的字符串，如 "1"、"2"；当前普通委托不提供 |
| `createdAt` | integer | 委托创建时间，毫秒；历史委托时间筛选使用该字段 |
| `updatedAt` | integer | 委托记录更新时间，毫秒 |

委托历史包括已终结的普通委托，不能仅凭接口名称将每条记录当成已完全成交。当前委托页是当前状态查询，不是委托事件流。

## 4. 条件委托：当前与历史

当前：`orders/current` + `orderCategory=CONDITIONAL`；历史：`orders/history` + `orderCategory=CONDITIONAL`。两者均需显式 platformUserIds。

| `data` 字段 | 类型 | 含义 |
| --- | --- | --- |
| `symbol` | string | 交易对 |
| `conditionId` | string | 条件委托 ID，与普通 orderId 独立 |
| `orderCategory` | string | 固定 CONDITIONAL |
| `generatedOrderId` | string | 触发生成的普通委托 ID；未生成时可能省略 |
| `clientOrderId` | string | 条件委托的客户端单号，存在时输出 |
| `side` | string | BUY / SELL |
| `orderType` | string | 触发后委托类型，按实际值解释 |
| `triggerType` | string | 触发规则类型，按实际来源枚举解释 |
| `workingType` | string | 触发价格的工作类型/价格来源，按实际枚举解释 |
| `timeInForce` | string | 触发后委托的有效期/执行策略 |
| `price` | string | 触发后委托价格，来源有值时输出 |
| `quantity` | string | 条件委托数量 |
| `triggerPrice` | string | 触发价格 |
| `reduceOnly` | boolean | 是否仅减仓 |
| `closePosition` | boolean | 是否采用全仓位平仓语义，不能与 closeOnly 混为同一字段 |
| `leverage` | integer | 杠杆倍数，非零时输出 |
| `marginMode` | string | 此类返回 CROSS / ISOLATED，区别于普通历史委托/持仓的数字字符串 |
| `status` | string | 条件单触发状态，见第 8 节 |
| `triggerStatus` | string | 当前实现与 status 使用相同的条件单状态值 |
| `createdAt` | integer | 条件委托创建时间，毫秒；历史条件单按此时间筛选 |
| `updatedAt` | integer | 更新时间，有值时输出，毫秒 |
| `deletedAt` | integer | 删除标记时间，有值时输出，毫秒；不是统一的成交/取消时间字段 |

条件单未提供普通委托的 filledQuantity、remainingQuantity、avgFilledPrice。触发后如需普通单成交情况，通过 generatedOrderId 查询普通委托/成交，不从条件单 status 推断成交量。

## 5. 成交明细

接口：`trades`。需显式 platformUserIds。

| `data` 字段 | 类型 | 含义 |
| --- | --- | --- |
| `fillId` | string | 成交明细 ID，对应本记录 entityId |
| `tradeId` | string | 成交关联的撮合交易 ID；不代替明细 ID |
| `orderId` | string | 该明细所属普通委托 ID |
| `positionId` | string | 关联持仓 ID |
| `symbol` | string | 交易对 |
| `side` | string | 本玩家的买卖方向 BUY / SELL |
| `roleType` | string | 本玩家的成交角色 MAKER / TAKER |
| `positionSide` | string | 持仓方向，按实际值 LONG / SHORT / BOTH 等解释 |
| `price` | string | 本次成交价 |
| `quantity` | string | 本次成交数量，不是原委托总数量 |
| `tradeValue` | string | 本次成交名义价值，来源为成交 notional |
| `handlingFee` | string | 本次成交手续费，保留来源符号和精度，不自行取绝对值 |
| `realizedPnl` | string | 本次成交对应的已实现盈亏；不是当前持仓实时浮盈亏 |
| `tradedAt` | integer | 成交时间，毫秒；时间筛选使用该字段 |

一笔委托可能有多条成交，应以明细为单位保存。不要将不同玩家/成交角色的记录按 tradeId 合并后丢弃其明细身份。

## 6. 持仓：当前与历史

| `data` 字段 | 类型 | 当前持仓 | 历史持仓 | 含义 |
| --- | --- | --- | --- | --- |
| `positionId` | string | 提供 | 提供 | 持仓 ID，对应 entityId |
| `symbol` | string | 提供 | 提供 | 交易对 |
| `positionSide` | string | 提供 | 提供 | LONG / SHORT / BOTH 等持仓方向 |
| `status` | string | 提供 | 提供 | 持仓数字状态的字符串，见第 8 节 |
| `leverage` | integer | 提供 | 提供 | 杠杆倍数 |
| `marginMode` | string | 提供 | 提供 | 数字枚举字符串 "1" 全仓、"2" 逐仓 |
| `quantity` | string | 提供 | 不提供 | 当前持仓数量 |
| `entryPrice` | string | 提供 | 提供 | 存储的持仓开仓均价 |
| `margin` | string | 提供 | 不提供 | 当前持仓保证金金额，不是用户整个合约钱包余额 |
| `maxQuantity` | string | 不提供 | 提供 | 该历史持仓记录的最大持仓数量 |
| `closeQuantity` | string | 不提供 | 提供 | 该历史持仓生命周期的累计平仓数量 |
| `closePrice` | string | 不提供 | 提供 | 存储的平仓均价 |
| `pnl` | string | 不提供 | 提供 | 历史持仓损益；不将其与成交 realizedPnl 简单重复累加 |
| `roe` | string | 不提供 | 提供 | 历史持仓收益率的比例值，未乘以 100；例如 `"0.043"` 表示 `4.3%`，`"-0.05"` 表示 `-5%` |
| `fundingFeeAccum` | string | 提供 | 提供 | 累计资金费，保留来源符号与精度 |
| `openedAt` | integer | 提供 | 提供 | 开仓时间，毫秒 |
| `updatedAt` | integer | 提供 | 不提供 | 当前持仓更新时间，毫秒 |
| `closedAt` | integer | 不提供 | 提供 | 平仓时间，毫秒；历史持仓时间筛选使用该字段 |

历史 `roe` 的计算口径为 `pnl / (entryPrice × maxQuantity / leverage)`，接口直接返回保存的比例值。百分比展示时使用十进制运算乘以 100，再添加 `%`；分母使用该历史持仓记录的名义保证金，不是账户钱包余额。开仓价或最大持仓数量为 0、杠杆无效等无法计算的情况，来源保存为 0，因此不能仅凭 `roe=0` 推断该笔盈亏为 0。

当前持仓没有实时标记价格、实时浮盈亏、实时 ROE 或实时强平价。它反映存储的持仓状态，不能用更新时刻和保证金字段自行声称当前实时风控结果。

## 7. 合约账变明细

接口：`account/ledger`。返回的 data 仅包含下面 11 个业务字段，不携带委托布尔标志。

| `data` 字段 | 类型 | 含义 |
| --- | --- | --- |
| `ledgerId` | string | 来源账变流水 ID；同一流水可能拆分多条明细 |
| `symbol` | string | 关联交易对，没有关联时可能省略 |
| `assetType` | string | 资产类型，按来源值保存；用于资产分类，不默认推断为 USDT |
| `changeType` | string | 账变业务类型，按来源值保存，例如手续费、盈亏、划转；可按同字段筛选 |
| `amount` | string | 本明细变动额度，正数表示增加，负数表示减少；保留精度 |
| `walletBalanceBefore` | string | 本明细记录的账变前钱包余额 |
| `walletBalanceAfter` | string | 本明细记录的账变后钱包余额 |
| `frozenBalanceBefore` | string | 本明细记录的账变前冻结余额 |
| `frozenBalanceAfter` | string | 本明细记录的账变后冻结余额 |
| `referenceId` | string | 关联业务编号；按 changeType 解释，不能统一当作委托 orderId；"0" 表示来源没有有效关联编号 |
| `createdAt` | integer | 账变明细创建时间，毫秒；本接口时间筛选使用该字段 |

使用接口返回的账变前后余额。不同 changeType 的语义不同，不能假定每条 amount 都等于 walletBalanceAfter − walletBalanceBefore。账变展示/汇总应保留类型和明细身份。

以下为**格式示例，使用虚构 ID 和金额**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "platformUserId": "1188041528",
        "entityType": "ACCOUNT_LEDGER",
        "entityId": "900001",
        "data": {
          "ledgerId": "ledger-example-001",
          "symbol": "BTCUSDT",
          "assetType": "USDT",
          "changeType": "HANDLING_FEE",
          "amount": "-0.10000000",
          "walletBalanceBefore": "100.00000000",
          "walletBalanceAfter": "99.90000000",
          "frozenBalanceBefore": "0.00000000",
          "frozenBalanceAfter": "0.00000000",
          "referenceId": "800001",
          "createdAt": 1791590400000
        }
      }
    ],
    "nextCursor": "",
    "hasMore": false
  }
}
```

## 8. 状态与保证金模式的区别

| 资源 | 当前返回值 |
| --- | --- |
| 当前普通委托 status | NEW、PENDING、PARTIALLY_FILLED |
| 历史普通委托 status | FILLED、CANCELED、REJECTED、PARTIAL_FILLED_CANCELED 或 PARTIALLY_FILLED_CANCELED；保持实际来源拼写 |
| 当前条件单 status / triggerStatus | PENDING_ACTIVATION、PENDING、TRIGGERING |
| 历史条件单 status / triggerStatus | TRIGGERED、CANCELLED、REJECTED、FIRED |
| 当前持仓 status | 数字字符串 "1" 持仓中、"3" 强平中 |
| 历史持仓 status | 数字字符串 "2" 已平仓、"4" 强平平仓 |

普通委托的 CANCELED 与条件单的 CANCELLED 拼写不同；条件单已触发不等于生成的普通委托已成交。不要复用同一套 status 翻译表处理所有资源。

条件委托 marginMode 返回 CROSS / ISOLATED；历史普通委托及持仓目前返回 "1" / "2"，分别为全仓/逐仓。当前普通委托不提供 marginMode。商户可以在自己的展示层按资源归一化，原始值仍需保存。

## 9. 模型中声明但本次查询不提供的字段

通用 RecordData / Java PerpetualData 模型还声明了下列字段，但当前六类正式读取逻辑不填充它们。**不应列为可用业务数据，也不能用字段默认值替代真实结果。**

| 字段 | 模型类型 | 当前边界 |
| --- | --- | --- |
| `closeReason` | string | 未提供历史持仓平仓原因 |
| `walletDelta` | string | 账变接口不返回钱包 delta |
| `frozenDelta` | string | 账变接口不返回冻结 delta |
| `usedDelta` | string | 账变接口不返回已用保证金 delta |
| `feeDelta` | string | 账变接口不返回费用 delta |
| `pnlDelta` | string | 账变接口不返回损益 delta |
| `accountVersion` | string | 账变接口不返回账户版本，不能用其做可靠增量同步 |
| `occurredAt` | integer | 账变接口使用 createdAt，不提供另一套事件发生时间 |

实际字段适用范围以上述各资源表为准；平台更新字段或枚举时，按新的接口说明和 SDK 版本适配。

## 10. 相关文档

- [商户接入指南](merchant-integration.md)。
- [Java SDK v0.3.0 中文参考](https://github.com/6mm-com/agent-java-sdk/blob/v0.3.0/README.zh-CN.md)。
