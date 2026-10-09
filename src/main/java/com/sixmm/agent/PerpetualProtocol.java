package com.sixmm.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sixmm.agent.model.PerpetualData;
import com.sixmm.agent.model.PerpetualResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 六类只读查询独立校验，不改变已有资金接口协议。
final class PerpetualProtocol {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ENTITY_TYPES = new HashSet<>(Arrays.asList(
            "ORDER", "CONDITION_ORDER", "TRADE_FILL", "POSITION", "ACCOUNT_LEDGER"));

    private PerpetualProtocol() { }

    static void validateRequest(String resource, Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String text = (String) value;
                int max = entry.getKey().equals("cursor") ? 8192 : 128;
                if ((!text.isEmpty() && text.trim().isEmpty())
                        || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > max
                        || text.indexOf('\0') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
                    invalid("invalid " + entry.getKey());
                }
            }
        }
        Object limit = body.get("limit");
        if (limit != null && (((Number) limit).intValue() < 1 || ((Number) limit).intValue() > 1000)) {
            invalid("limit must be between 1 and 1000");
        }
        long start = time(body.get("startTime"));
        long end = time(body.get("endTime"));
        if (end != 0 && start >= end) {
            invalid("startTime must be before endTime");
        }
        if (resource.startsWith("orders/")) {
            Object category = body.get("orderCategory");
            if (!"NORMAL".equals(category) && !"CONDITIONAL".equals(category)) {
                invalid("orderCategory must be NORMAL or CONDITIONAL");
            }
            if (("NORMAL".equals(category) && present(body.get("conditionId")))
                    || ("CONDITIONAL".equals(category) && present(body.get("orderId")))) {
                invalid("order selector does not match category");
            }
        }
        for (String name : Arrays.asList("orderId", "conditionId")) {
            if (present(body.get(name))) {
                String id = (String) body.get(name);
                try {
                    if (Long.parseLong(id) <= 0 || !id.matches("[1-9][0-9]*")) {
                        invalid("invalid " + name);
                    }
                } catch (NumberFormatException e) {
                    invalid("invalid " + name);
                }
            }
        }
        boolean requiresPlayers = resource.equals("trades")
                || (resource.startsWith("orders/") && "CONDITIONAL".equals(body.get("orderCategory")));
        // 省略才使用 Agent 范围；显式空列表或非法元素不能降级为全范围查询。
        if (requiresPlayers || body.get("platformUserIds") != null) {
            Object value = body.get("platformUserIds");
            if (!(value instanceof List<?>)) {
                invalid("platformUserIds is required");
            }
            List<?> ids = (List<?>) value;
            if (ids.isEmpty() || ids.size() > 100) {
                invalid("platformUserIds must contain 1 to 100 public user IDs");
            }
            Set<Object> unique = new HashSet<>();
            for (Object id : ids) {
                if (!(id instanceof String) || !((String) id).matches("[1-9][0-9]{9}") || !unique.add(id)) {
                    invalid("platformUserIds must contain unique 10-digit public user IDs");
                }
            }
        }
    }

    static PerpetualResponse readResponse(String body) throws IOException {
        JsonNode root = MAPPER.readTree(body);
        require(root != null && root.isObject() && root.path("code").isIntegralNumber()
                && root.path("code").canConvertToInt() && root.path("message").isTextual(), "invalid response envelope");
        if (root.get("code").intValue() == 0) {
            JsonNode page = root.path("data");
            require(page.isObject() && page.path("items").isArray() && page.path("hasMore").isBoolean()
                    && page.path("nextCursor").isTextual(), "invalid query page");
            require(!page.get("hasMore").booleanValue() || !page.get("nextCursor").textValue().isEmpty(), "missing nextCursor");
            for (JsonNode record : page.get("items")) {
                require(record.isObject() && record.path("entityType").isTextual()
                        && ENTITY_TYPES.contains(record.get("entityType").textValue())
                        && record.path("entityId").isTextual() && !record.get("entityId").textValue().isEmpty()
                        && record.path("platformUserId").isTextual()
                        && record.get("platformUserId").textValue().matches("[1-9][0-9]{9}")
                        && record.path("data").isObject(), "invalid query record");
                // 显式类型校验避免 Jackson 把数值金额默默转换为字符串。
                for (Field field : PerpetualData.class.getFields()) {
                    JsonNode value = record.get("data").get(field.getName());
                    if (value == null) {
                        continue;
                    }
                    Class<?> type = field.getType();
                    require((type == String.class && value.isTextual())
                            || (type == Boolean.class && value.isBoolean())
                            || (type == Integer.class && value.isIntegralNumber() && value.canConvertToInt())
                            || (type == Long.class && value.isIntegralNumber() && value.canConvertToLong()),
                            "invalid query data field: " + field.getName());
                }
            }
        }
        return MAPPER.treeToValue(root, PerpetualResponse.class);
    }

    private static long time(Object value) {
        long time = value == null ? 0 : ((Number) value).longValue();
        if (time < 0 || time > 253402300799999L) {
            invalid("invalid millisecond timestamp");
        }
        return time;
    }

    private static boolean present(Object value) { return value != null && !value.equals(""); }
    private static void require(boolean valid, String message) {
        if (!valid) { invalid(message); }
    }
    private static void invalid(String message) { throw new AgentSdkException(message); }
}
