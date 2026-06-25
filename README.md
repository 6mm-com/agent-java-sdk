# 6MM Agent Java SDK

Java SDK for the 6MM Agent REST API.

This SDK is intended for partner backend services. It wraps request signing,
timestamp and nonce generation, amount formatting, HTTP calls, business
exceptions, entry URL creation, Trading Widget embed token creation, and
webhook signature verification.

Chinese documentation: [README.zh-CN.md](README.zh-CN.md)

## Requirements

- Java 8+
- Maven 3.9+

## What The SDK Covers

| Scenario | SDK API | Description |
| --- | --- | --- |
| Bind user | `bind` | Create or fetch the binding between a partner user and a 6MM platform user |
| Fixed amount transfer | `transfer` | Transfer funds into or out of 6MM |
| Transfer all out | `transferAllOut` | Move all withdrawable user balance back to the partner platform |
| Reverse order | `reverse` | Reverse a successful transfer when the backend supports a compensation flow |
| Query order | `queryOrder` | Query one transfer order by partner order number |
| List orders | `listOrders` | Page through transfer orders for reconciliation |
| Query partner account | `queryAccount` | Query the partner margin or funding account |
| Query user assets | `queryUserAssets` | Query a bound user's 6MM-side assets |
| Direct entry URL | `createEntryUrl` | Create a one-time SSO URL for redirect mode |
| Embed token | `createEmbedToken` | Create a short-lived token for Trading Widget `tokenProvider` mode |
| Service version | `version` | Query Agent service version information |
| Webhook verification | `WebhookVerifier.verify` | Verify transfer final-state callbacks from 6MM |

## Configuration

Obtain the following values from 6MM or your internal operations system before
integrating.

| Item | Example | Description |
| --- | --- | --- |
| `baseUrl` | `https://agent-api.6mm.com` | Agent API base URL, without a trailing slash |
| `agentCode` | `AGENT001` | Partner code |
| `apiSecret` | `your-api-secret` | API signing secret. Keep it on the backend only |
| `defaultCurrency` | `USDT` | Default currency, usually `USDT` |
| `webhookUrl` | `https://partner.example.com/6mm/webhook` | Partner endpoint for 6MM callbacks |

Production recommendations:

- Use HTTPS for Agent API and webhook endpoints.
- Store `apiSecret` in a secret manager, configuration center, or environment variable.
- Keep server time synchronized, preferably with NTP.
- Persist all partner order numbers and keep them globally unique.
- Never send `apiSecret` to browsers, apps, mini programs, or frontend source code.

## Maven Coordinates

Current source coordinates:

```xml
<dependency>
    <groupId>com.sixmm.exchange.sdk</groupId>
    <artifactId>agent</artifactId>
    <version>0.1.1</version>
</dependency>
```

GitHub is source hosting, not a Maven dependency repository. If the SDK has not
been published to a Maven repository yet, install it into your local Maven
repository first:

```bash
git clone https://github.com/6mm-com/agent-java-sdk.git
cd agent-java-sdk
git checkout v0.1.1
mvn install
```

After local installation, your business project can resolve
`com.sixmm.exchange.sdk:agent:0.1.1` from the local Maven repository.

For team builds and test environments, publish the SDK to a Maven repository
such as Nexus, Artifactory, GitHub Packages, Maven Central, or use a GitHub
based build service such as JitPack.

## Client Initialization

### Plain Java

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

### Spring Boot

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

Example application properties:

```properties
agent.sdk.base-url=https://agent-api.6mm.com
agent.sdk.agent-code=AGENT001
agent.sdk.api-secret=${AGENT_API_SECRET}
```

## Automatic Signing Fields

For signed Agent API calls, the SDK automatically injects:

| Field | Source | Description |
| --- | --- | --- |
| `agentCode` | `AgentClientConfig.agentCode` | Partner code |
| `timestamp` | Current UTC Unix timestamp | Seconds |
| `nonce` | Secure random generator | Replay protection |
| `sign` | HMAC-SHA256 | Request signature |
| `currency` | `defaultCurrency` | Filled when transfer/account requests omit currency |

Signing rules:

1. Exclude `sign`.
2. Exclude empty values.
3. Sort keys by ASCII order.
4. Join as `k=v&k2=v2`.
5. Calculate HMAC-SHA256 hex with `apiSecret`.

Normal business code should not calculate signatures or manually set
`agentCode`, `timestamp`, `nonce`, or `sign`.

## User ID Rules

`platformUserId` is the 6MM external user ID. It is currently a 10-digit numeric
string, for example:

```text
1188041528
```

Rules:

- Store it as a string, not as an integer.
- Do not generate, decode, or infer meaning from it.
- Use your own partner-side user ID as `agentUserId`.
- Persist the relationship among `agentUserId`, `platformUserId`, and `agentOrderNo`.

## Bind User

Bind creates or fetches the relationship between a partner user and a 6MM
platform user. Call it when a user first enters the trading flow or before the
first transfer into 6MM.

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

Request fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `agentUserId` | string | Yes | Unique partner-side user ID |
| `username` | string | No | Partner-side display name. When provided, 6MM writes it to `users.nick_name` |
| `ext` | string | No | Extension field. JSON string is recommended |

Response fields:

| Field | Type | Description |
| --- | --- | --- |
| `platformUserId` | string | 6MM external user ID |
| `bindStatus` | string | Usually `BOUND` |
| `isSimulatedUser` | boolean | Whether this is a simulated user |

## Fixed Amount Transfer

Use `transfer` for fixed amount transfers. `Direction.IN` means transferring
from the partner platform into 6MM. `Direction.OUT` means transferring from 6MM
back to the partner platform.

Use strings or `BigDecimal` for amounts. Do not use `double` or `float`.

### Transfer By `agentUserId`

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

### Transfer By `platformUserId`

```java
TransferResponse resp = client.transfer(TransferRequest.fixedByPlatformUserId(
        "AGT-ORDER-1002",
        "1188041528",
        Direction.OUT,
        "USDT",
        "5.00"));
```

### Provide Both IDs

If both IDs are provided, the service validates that they point to the same
bound user.

```java
TransferRequest req = TransferRequest
        .fixed("AGT-ORDER-1003", "agent-user-001", Direction.IN, "USDT", "20.00")
        .withPlatformUserId("1188041528");

TransferResponse resp = client.transfer(req);
```

Request fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `agentOrderNo` | string | Yes | Globally unique partner order number |
| `agentUserId` | string | Conditionally | Required when `platformUserId` is absent |
| `platformUserId` | string | Conditionally | Required when `agentUserId` is absent |
| `direction` | enum | Yes | `IN` or `OUT` |
| `currency` | string | No | Filled from `defaultCurrency` when absent |
| `amount` | string | Yes | Decimal amount string |

Response fields:

| Field | Type | Description |
| --- | --- | --- |
| `orderNo` | string | Platform order number |
| `orderStatus` | string | `SUCCESS`, `FAILED`, `PROCESSING`, etc. |
| `agentUserId` | string | Partner-side user ID |
| `platformUserId` | string | 6MM external user ID |

## Order Confirmation And Idempotency

After `transfer` is called, the Agent service synchronously calls the trading
core on the main path. In most cases, the API response already contains the
synchronous trading-core result.

| Result | Meaning | Partner action |
| --- | --- | --- |
| `SUCCESS` | Transfer succeeded | Mark local order as successful |
| `FAILED` | Transfer was rejected or failed | Save the failure reason and show it when appropriate |
| `PROCESSING` | Trading core timeout or network uncertainty | Do not retry with a new order number. Wait for webhook or call `queryOrder` |

Idempotency requirements:

- Use one `agentOrderNo` for one business transaction.
- If HTTP times out or the network fails, query the same `agentOrderNo` first.
- Webhooks may be retried. Deduplicate them by idempotency key.

## Transfer All Out

`transferAllOut` moves the user's withdrawable 6MM-side balance back to the
partner platform.

```java
import com.sixmm.agent.model.TransferAllOutRequest;
import com.sixmm.agent.model.TransferAllOutResponse;

TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.of("AGT-ORDER-2001", "agent-user-001", "USDT"));

System.out.println(resp.orderStatus);
System.out.println(resp.amount);
```

By `platformUserId`:

```java
TransferAllOutResponse resp = client.transferAllOut(
        TransferAllOutRequest.byPlatformUserId("AGT-ORDER-2002", "1188041528", "USDT"));
```

`amount` in the response is the actual transferred amount.

## Reverse Order

Reverse is used to compensate a successful transfer order when the backend
supports the reverse flow. Typical cases include partner local order exceptions
or manual reconciliation rollback.

```java
import com.sixmm.agent.model.ReverseOrderRequest;
import com.sixmm.agent.model.ReverseOrderResponse;

ReverseOrderResponse resp = client.reverse(ReverseOrderRequest.of(
        "AGT-ORDER-1001",
        "REV-ORDER-1001",
        "partner order rollback"));

System.out.println(resp.orderStatus);
```

Fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `origOrderNo` | string | Yes | Original transfer order number |
| `reverseOrderNo` | string | Yes | Globally unique reverse order number |
| `reverseReason` | string | Yes | Reverse reason |

Notes:

- Reverse is not a normal cancel operation.
- Reverse orders must also be idempotent by order number.
- After an original order is reversed, webhook may push the original order's
  `REVERSED` final state.

## Query Order

```java
import com.sixmm.agent.model.OrderQueryType;
import com.sixmm.agent.model.QueryOrderRequest;
import com.sixmm.agent.model.QueryOrderResponse;

QueryOrderResponse resp = client.queryOrder(
        QueryOrderRequest.of("AGT-ORDER-1001", OrderQueryType.TRANSFER_IN));

System.out.println(resp.status);
System.out.println(resp.failReason);
```

Supported `orderType` values:

- `TRANSFER_IN`
- `TRANSFER_OUT`

Common response fields:

| Field | Type | Description |
| --- | --- | --- |
| `orderType` | string | Order type |
| `orderNo` | string | Partner order number |
| `status` | string | Order status |
| `direction` | string | `IN` or `OUT` |
| `transferMode` | string | `FIXED_AMOUNT` or `ALL_OUT` |
| `currency` | string | Currency |
| `amount` | string | Amount |
| `agentUserId` | string | Partner-side user ID |
| `platformUserId` | string | 6MM external user ID |
| `failReason` | string | Failure reason |
| `createdAt` | string | Creation time |
| `completedAt` | string | Completion time |

## List Orders

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

Pagination recommendations:

- `page` starts from 1.
- Keep `pageSize` at or below 100.
- For reconciliation jobs, page by time window and deduplicate by `orderNo`.

## Query Partner Account

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

When `currency` is absent, the SDK uses `defaultCurrency`.

## Query User Assets

```java
import com.sixmm.agent.model.QueryUserAssetsRequest;
import com.sixmm.agent.model.QueryUserAssetsResponse;

QueryUserAssetsResponse resp = client.queryUserAssets(
        QueryUserAssetsRequest.of("1188041528"));

System.out.println(resp.walletBalance);
System.out.println(resp.availableBalance);
System.out.println(resp.version);
```

Fields:

| Field | Type | Description |
| --- | --- | --- |
| `walletBalance` | string | Wallet balance |
| `frozenMargin` | string | Frozen margin |
| `usedMargin` | string | Used margin |
| `availableBalance` | string | Available balance |
| `isolatedMargin` | string | Isolated margin |
| `version` | int64 | Asset version |
| `isSimulatedUser` | boolean | Whether this is a simulated user |

## Direct Entry URL

Direct entry URL is for redirect mode. A partner user clicks a button on the
partner site, opens 6MM frontend, and is automatically logged in.

```java
import com.sixmm.agent.model.CreateEntryUrlRequest;
import com.sixmm.agent.model.CreateEntryUrlResponse;

CreateEntryUrlResponse resp = client.createEntryUrl(
        CreateEntryUrlRequest.of("agent-user-001")
                .withRedirectPath("/trade/BTCUSDT")
                .withReturnUrl("https://partner.example.com/return#markets"));

System.out.println(resp.webUrl);
System.out.println(resp.expireAt);
```

Field rules:

| Field | Description |
| --- | --- |
| `redirectPath` | 6MM frontend relative path. It must start with `/`, for example `/trade/BTCUSDT` |
| `returnUrl` | Partner absolute URL used when the user exits 6MM frontend. Only `http` and `https` are allowed |
| `expireAt` | Entry ticket expiration time as a Unix timestamp in seconds |

Security requirements:

- `webUrl` contains a one-time ticket. Use it immediately and do not store it long term.
- If the entry URL expires or has been used, generate a new one from the partner site.
- `returnUrl` must not be `javascript:`, a relative path, or a URL with userinfo.

## Trading Widget Embed Token

Embed token is for Trading Widget SDK `partner-token + tokenProvider` mode. The
browser calls the partner backend, and the partner backend calls this Java SDK
to create an `embedToken`.

```java
import com.sixmm.agent.model.CreateEmbedTokenRequest;
import com.sixmm.agent.model.CreateEmbedTokenResponse;

CreateEmbedTokenResponse resp = client.createEmbedToken(
        CreateEmbedTokenRequest.of("agent-user-001", "tw_abc123")
                .withSymbol("ETHUSDT"));

System.out.println(resp.embedToken);
System.out.println(resp.expireAt);
```

Integration requirements:

- `channelId` must use the value passed by the Trading Widget SDK.
- `symbol` is optional, but it is recommended to pass the current initial symbol.
- `embedToken` is short-lived and one-time. Do not put it into URL query strings.
- The partner frontend must never hold `apiSecret`.
- `expireAt` is a Unix timestamp in seconds, not a TTL.

Partner backend endpoint example:

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

## Webhook Verification

6MM pushes a webhook to the configured `webhookUrl` when a transfer order reaches
a final state. The SDK provides signature verification and idempotency key
helpers.

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

Webhook headers:

| Header | Description |
| --- | --- |
| `X-Agent-Timestamp` | Unix timestamp in seconds |
| `X-Agent-Nonce` | Nonce |
| `X-Agent-Signature` | HMAC-SHA256 signature |

Signature payload:

```text
timestamp + nonce + rawBody
```

Partner-side recommendations:

- Reject requests whose timestamp differs from server time by more than 5 minutes.
- Cache nonce values to prevent replay.
- Deduplicate by `WebhookVerifier.idempotencyKey(rawBody)`.
- Return HTTP 2xx after successful processing. Non-2xx responses may trigger retries.

Webhook events:

| `event` | Trigger | Common `targetStatus` |
| --- | --- | --- |
| `transfer.completed` | Fixed amount transfer or transfer-all-out reaches final state. Original order may also emit this after reverse | `SUCCESS` / `FAILED` / `REVERSED` |
| `reverse.completed` | Reverse order reaches final state | `SUCCESS` / `FAILED` |
| `transfer.dead` | System retries cannot confirm the final state and manual intervention is required | `DEAD` |

`platformUserId` in webhook payload is the same value returned by `bind`.
`agentUserId` is the partner-side user ID. Use
`orderType + orderId + targetStatus` or the SDK-generated `idempotencyKey` for
deduplication.

## Error Handling

SDK exceptions are split into two categories:

| Exception | Scenario | Suggested handling |
| --- | --- | --- |
| `AgentApiException` | HTTP status is not 2xx, or Agent response `code != 0` | Read `getHttpStatus()`, `getCode()`, and `getResponseBody()` and treat it as a business/API failure |
| `AgentSdkException` | Network, serialization, configuration, or signing failure | Log it and treat it as a system exception |

Example:

```java
import com.sixmm.agent.AgentApiException;
import com.sixmm.agent.AgentSdkException;

try {
    TransferResponse resp = client.transfer(TransferRequest.fixed(
            "AGT-ORDER-1001", "agent-user-001", Direction.IN, "USDT", "10.00"));
    // Update the local order according to resp.orderStatus.
} catch (AgentApiException e) {
    // Business rejection or HTTP error.
    log.warn("6mm agent api rejected request, httpStatus={}, code={}, body={}",
            e.getHttpStatus(), e.getCode(), e.getResponseBody());
} catch (AgentSdkException e) {
    // Local SDK error or network error.
    log.error("6mm agent sdk request failed", e);
}
```

## Recommended Business Flows

### First Trading Entry

```text
1. User logs in to the partner site.
2. Partner backend calls bind with agentUserId.
3. Partner stores the agentUserId and platformUserId relationship.
4. User clicks the trading entry.
5. Redirect mode: call createEntryUrl and open webUrl in the browser.
6. Embed mode: Trading Widget calls tokenProvider, and partner backend calls createEmbedToken.
```

### Deposit Into 6MM

```text
1. Partner creates a unique agentOrderNo.
2. Partner calls transfer(Direction.IN).
3. If SUCCESS is returned, mark the local order as successful.
4. If PROCESSING is returned, wait for webhook or poll queryOrder.
5. If FAILED is returned or AgentApiException is thrown, handle it as failed.
```

### Withdraw Back To Partner

```text
1. Partner creates a unique agentOrderNo.
2. Fixed amount withdraw: call transfer(Direction.OUT).
3. Full withdraw: call transferAllOut.
4. Confirm the final result by orderStatus, webhook, or queryOrder.
```

## Logging And Troubleshooting

Partner backend logs should include at least:

- `agentOrderNo`
- `agentUserId`
- `platformUserId`
- `direction`
- `currency`
- `amount`
- `orderStatus`
- `AgentApiException.code`
- `requestId` if present in the response body

Do not log full `apiSecret`, full signatures, or sensitive user information.

## Integration Checklist

| Check | Expected result |
| --- | --- |
| `client.version()` | Returns service name, version, and commit information |
| `bind` | Returns `platformUserId` and `BOUND` |
| Small `transfer IN` | Returns `SUCCESS` or can be confirmed successful through `queryOrder` |
| Small `transfer OUT` | Returns `SUCCESS` or can be confirmed successful through `queryOrder` |
| Duplicate `agentOrderNo` | Does not create duplicate fund movement |
| Webhook signature verification | Valid signature passes; tampered body fails |
| Webhook idempotency | Duplicate webhook delivery does not create duplicate accounting |
| `createEntryUrl` | Browser can open and enter the 6MM frontend |
| `createEmbedToken` | Trading Widget can complete embedded authentication |

## FAQ

### Can `platformUserId` be stored as `long`?

No. Store it as a string. It is currently numeric-looking, but the external
contract is string.

### Can I retry with a new order number after timeout?

Do not do that immediately. Query the original `agentOrderNo` first. Only create
a new order after the original order is confirmed absent or failed and your
business flow explicitly decides to retry.

### Is `PROCESSING` a failure?

No. It means the final result is still uncertain. Wait for webhook or query the
order actively.

### Why should amounts be strings?

To avoid floating-point precision issues. Java business code may use
`BigDecimal`; the SDK serializes it as a plain decimal string.

### What if webhook is received multiple times?

That is expected retry behavior. Deduplicate by `orderType:orderId:targetStatus`
or the SDK `idempotencyKey`.

### Can Trading Widget call Agent API directly from the browser?

No. Browsers must not hold `apiSecret`. The correct flow is browser -> partner
backend -> `createEmbedToken`.

## Local Test Commands

Run from the SDK repository root:

```bash
mvn test
mvn package
```

If a business project uses local installation:

```bash
mvn clean install
```
