# 商户 Webhook 事件对接

四类事件共用商户配置的一个回调地址（agents.webhook_url），按正文 event 分流；无需为预警单独配置 URL。下列均为结构示例，不用于向测试环境注入数据。

## 事件与幂等

| event | 含义 | targetStatus / 去重键 |
| --- | --- | --- |
| transfer.completed | 划转终态，包括原单被冲正后的更新；不一定成功 | SUCCESS / FAILED / REVERSED；orderType:orderId:targetStatus |
| reverse.completed | 冲正订单终态 | SUCCESS / FAILED；orderType:orderId:targetStatus |
| transfer.dead | 重试仍无法确认资金订单最终状态，需要人工核对；不代表确定失败 | DEAD；orderType:orderId:targetStatus |
| liquidation.warning | 强平风险预警，无订单终态字段 | liquidation.warning:notificationId |

商户预警 event 为 `liquidation.warning`，用户 WebSocket 的 data.event 仍为 `LIQUIDATION_WARNING`。不要混用两种通道。

兼容：历史商户通知曾使用 `LIQUIDATION_WARNING`。已冻结的发送正文及重试保持原样，接收端需同时识别两种名称。如果业务库原来使用大写前缀的预警键，必须继续使用原键或在迁移期查询两种键，避免再次处理。

**本仓库当前 `WebhookVerifier.idempotencyKey` 只提取资金事件的 `orderType:orderId:targetStatus`，对预警正文会返回空串。** 本次仅更新文档，没有发布包含预警辅助方法的新 SDK 制品。接收预警时，可继续使用现有 `verify` 方法验证原始正文签名；验签及归属校验通过后，在商户业务代码中识别上述两个事件名，校验 `notificationId` 为正整数十进制字符串，并构造 `liquidation.warning:<notificationId>`。不要把空串作为去重键，也不要要求预警具有资金订单字段。

去重必须在商户与 environment 范围内持久化，并与业务处理原子提交。不同 notificationId 的 FIRST/UPGRADE/REMINDER 都是独立通知；不要按用户、轮次或 eventType 合并丢弃。空幂等键表示无法提取有效身份，不能拿空串去重。SDK 只返回辅助键，不会保存或执行业务。相同身份不同正文应作为冲突记录，不覆盖已处理正文。

## 验签和接收

请求头为 `X-Agent-Timestamp`（Unix 秒）、`X-Agent-Nonce`、`X-Agent-Signature`。签名是商户 API Secret 对 `timestamp + nonce + rawBody` 计算的 HMAC-SHA256 十六进制字符串。必须用收到的原始字节验签，不能先格式化 JSON、改名或重序列化。

先验签，再校验时间窗口（建议 ±300 秒）、商户和环境，随后提取幂等键并可靠处理。未知事件须保留原值以便排查，不能当作资金成功。验签辅助方法本身不执行时间、归属或持久去重校验。

处理完成或确认同正文重复后返回 HTTP 2xx；失败返回非 2xx。预警发送端 HTTP 超时为 5 秒，最多 8 次尝试，旧正文固定，仅签名时间与 nonce 更新。可靠接管后尽快响应，耗时业务可由商户自己的持久任务异步处理。

商户通知与用户在线状态无关。平台用户推送仅在线尽力投递，离线不补发；第一期没有站内信。

### 响应成功判定与停止重试

资金回调和强平预警都只根据 **HTTP 状态码** 判断本次投递结果，不解析响应正文中的 `code`、`success`、`result` 或 `message`，也不要求固定返回 `OK` / `success`。

| 商户返回结果 | 平台行为 |
| --- | --- |
| HTTP 200–299，包括 200、202、204 | 判定本次投递成功；成功状态持久化后，不再自动重发这条通知 |
| 最终 HTTP 响应为非 2xx，包括 400、401、403、409、429、500、503 | 本次投递失败，按既有退避策略重试，达到尝试上限后标记 DEAD；不会因为 4xx 自动视为已确认 |
| 连接失败或等待响应超时，未取得可用响应 | 本次结果未确认，按失败策略重试 |

响应正文可以为空、文本或 JSON，以下 `result` 等字段只供商户自身阅读，并非平台协议要求。`202 Accepted` 同样会终止平台重试，因此只能在商户已经把通知可靠保存到自己的持久任务队列后返回，不能只启动一个内存异步任务就确认成功。

### 成功响应例子

业务处理完成，或已可靠保存到商户自己的待处理队列：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"result":"RECEIVED"}
```

无需响应正文时，也可以返回：

```http
HTTP/1.1 204 No Content
```

同一通知已成功处理，且确认正文一致时，跳过重复业务，仍然返回 2xx：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"result":"DUPLICATE"}
```

### 失败响应例子

例如数据库暂时不可用，商户既未完成处理，也未可靠保存通知，应返回非 2xx，让平台后续重试：

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{"result":"FAILED","message":"temporary storage failure"}
```

验签失败时拒绝处理，不更新业务或成功去重记录：

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{"result":"INVALID_SIGNATURE"}
```

这些响应正文不会改变平台的重试判断。不要期待返回 401、409 等状态即可永久停止重试；需要排查签名、通知内容或商户配置。

**错误示例：以下响应仍会被平台判定为投递成功，不会因 `success:false` 而重试。**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"success":false,"code":500,"message":"processing failed"}
```

如果 Web 框架或统一异常处理默认把业务错误包装成 HTTP 200，回调入口需要显式设置正确的 HTTP 失败状态。

### 可靠确认与重复投递

推荐顺序：验签、检查时间/商户/环境 → 提取幂等身份 → 在事务内完成业务和成功去重记录，或可靠保存待处理任务 → 提交成功后返回 200/204。数据库写入失败时不要提前返回 2xx，也不要先把通知标记为已处理。

商户处理成功后仍可能收到重复请求，例如响应在网络中丢失，或平台收到 2xx 后未能持久化 SUCCESS。HTTP 回调不保证只投递一次：必须在商户与 environment 范围内持久去重。资金使用 `orderType:orderId:targetStatus`，预警使用 `liquidation.warning:notificationId`；已处理的同正文通知返回 2xx，避免重复入账或重复提醒。同 ID 异正文应记录冲突并返回非 2xx，不覆盖原记录。

这里的“成功”指商户成功接收/处理通知，不代表通知所描述的资金业务成功。例如收到 `targetStatus=FAILED` 的合法资金结果并完成记录后，也应返回 2xx，不能因为订单失败就拒绝确认通知。

## 强平预警字段和示例

| 字段 | 类型 / 说明 |
| --- | --- |
| notificationId | 正整数十进制字符串，唯一通知 ID；不要转换为浮点数字 |
| eventType | FIRST：本轮首次；UPGRADE：本轮普通风险升级为高风险；REMINDER：持续风险再次提醒 |
| severity | NORMAL / HIGH。FIRST 可以直接为 HIGH；UPGRADE 对应 HIGH；REMINDER 可以为 NORMAL 或 HIGH |
| occurredAt | 数字，Unix 毫秒触发时间；不是 HTTP 接收时间 |
| object | type=CROSS 为全仓账户，symbol/side/positionId 为空串；ISOLATED 为逐仓仓位，携带对应合约、方向和仓位 ID |
| episode | 字符串，本轮预警轮次；FIRST 不是该用户历史第一次收到预警 |
| stateVersion / configVersion | 字符串，上游状态版本 / 配置版本；不据此自行丢弃通知 |
| riskRatio / minLargePositionValue / liquidationHighRiskThreshold | 十进制字符串，风险比率 / 普通预警阈值 / 高风险阈值，保留精度；阈值规则由上游计算 |
| environment | PRODUCTION / SIMULATED，按配置环境隔离；不是服务器名称 |
| agentId / agentCode / agentUserId | 字符串，代理 ID / 代理编码 / 商户侧用户 ID |
| platformUserId | 字符串，平台公开用户 ID，与绑定结果一致；不是服务内部 user_id |

```json
{
  "event": "liquidation.warning",
  "notificationId": "68460336",
  "eventType": "FIRST",
  "severity": "NORMAL",
  "occurredAt": 1791875870804,
  "object": {
    "type": "CROSS",
    "symbol": "",
    "side": "",
    "positionId": ""
  },
  "episode": "3",
  "stateVersion": "9",
  "riskRatio": "0.29133109",
  "minLargePositionValue": "0.20000000",
  "liquidationHighRiskThreshold": "0.40000000",
  "configVersion": "8",
  "environment": "PRODUCTION",
  "agentId": "16",
  "agentCode": "DEMO",
  "agentUserId": "merchant-user-001",
  "platformUserId": "6054354872"
}
```

## 资金划转结果示例

资金回调沿用既有类型：orderId 与 agentId 为 JSON 数字，amount 与 platformUserId 为字符串。不要套用预警的 agentId 字符串约定；读取大整数须使用无损整数/十进制类型。completedAt 为 UTC RFC3339，ts 为 Unix 秒。

```json
{
  "environment": "PRODUCTION",
  "event": "transfer.completed",
  "orderType": "transfer",
  "orderId": 10001,
  "orderNo": "merchant-order-001",
  "targetStatus": "SUCCESS",
  "agentId": 16,
  "agentCode": "DEMO",
  "currency": "USDT",
  "amount": "10.00",
  "direction": "IN",
  "transferMode": "FIXED_AMOUNT",
  "agentUserId": "merchant-user-001",
  "platformUserId": "6054354872",
  "rejectCode": 0,
  "rejectMessage": "",
  "failReason": "",
  "completedAt": "2026-10-13T07:17:50Z",
  "ts": 1791875870
}
```

## 冲正结果示例

```json
{
  "environment": "PRODUCTION",
  "event": "reverse.completed",
  "orderType": "transfer",
  "orderId": 10002,
  "orderNo": "merchant-reverse-001",
  "targetStatus": "SUCCESS",
  "agentId": 16,
  "agentCode": "DEMO",
  "currency": "USDT",
  "amount": "10.00",
  "direction": "IN",
  "transferMode": "FIXED_AMOUNT",
  "agentUserId": "merchant-user-001",
  "platformUserId": "6054354872",
  "rejectCode": 0,
  "rejectMessage": "",
  "failReason": "",
  "completedAt": "2026-10-13T07:17:50Z",
  "ts": 1791875870
}
```

## 资金订单终止示例

```json
{
  "environment": "PRODUCTION",
  "event": "transfer.dead",
  "orderType": "transfer",
  "orderId": 10003,
  "orderNo": "merchant-order-002",
  "targetStatus": "DEAD",
  "agentId": 16,
  "agentCode": "DEMO",
  "currency": "USDT",
  "amount": "10.00",
  "direction": "IN",
  "transferMode": "FIXED_AMOUNT",
  "agentUserId": "merchant-user-001",
  "platformUserId": "6054354872",
  "rejectCode": 0,
  "rejectMessage": "",
  "failReason": "order dead",
  "completedAt": "2026-10-13T07:17:50Z",
  "ts": 1791875870
}
```

上述示例仅展示协议；FAILED 的 rejectCode/rejectMessage/failReason 按实际拒绝结果返回，不能仅凭事件名判断资金已入账。
