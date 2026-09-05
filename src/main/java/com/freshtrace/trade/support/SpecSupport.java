package com.freshtrace.trade.support;

import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规格快照处理：校验 + 规范序列化 + 按当前商品规格数据计算加价。
 * <p>
 * 客户端传入的快照中任何价格字段（extraPrice）都不被信任，
 * 加价一律以 t_product_attribute 的当前值为准。
 * 规范序列化（仅保留 name/value，按 name、value 升序）保证相同选择
 * 落库为同一字符串，购物车唯一索引 uk_user_product_spec 才能正确去重。
 */
@Component
@RequiredArgsConstructor
public class SpecSupport {

    private final ObjectMapper objectMapper;

    private record SpecEntry(String name, String value) {
    }

    public record ParsedSpec(String canonicalSnapshot, BigDecimal extraPrice) {
    }

    /**
     * 校验并规范化规格快照。
     *
     * @param specSnapshot  客户端传入的规格快照 JSON（可为空）
     * @param attrPriceMap  "attrName|attrValue" -> extraPrice（以数据库当前值为准）
     * @return 规范快照字符串 + 按当前规格数据计算的总加价
     * @throws BizException 快照格式错误 / 规格不存在 / 属性名重复
     */
    public ParsedSpec parse(String specSnapshot, Map<String, BigDecimal> attrPriceMap) {
        BigDecimal extraPrice = BigDecimal.ZERO;
        if (!StringUtils.hasText(specSnapshot)) {
            return new ParsedSpec("", extraPrice);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(specSnapshot);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规格参数格式错误");
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规格参数格式错误");
        }

        List<SpecEntry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode node : root) {
            JsonNode nameNode = node.get("name");
            JsonNode valueNode = node.get("value");
            if (nameNode == null || valueNode == null || !nameNode.isTextual() || !valueNode.isTextual()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "规格参数格式错误");
            }
            String name = nameNode.asString();
            String value = valueNode.asString();
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "规格参数格式错误");
            }
            if (!names.add(name)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "规格参数存在重复属性名: " + name);
            }
            BigDecimal attrExtra = attrPriceMap.get(name + "|" + value);
            if (attrExtra == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "规格不存在: " + name + "=" + value);
            }
            extraPrice = extraPrice.add(attrExtra);
            entries.add(new SpecEntry(name, value));
        }

        entries.sort(Comparator.comparing(SpecEntry::name).thenComparing(SpecEntry::value));
        ArrayNode canonical = objectMapper.createArrayNode();
        for (SpecEntry entry : entries) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("name", entry.name());
            item.put("value", entry.value());
            canonical.add(item);
        }
        try {
            return new ParsedSpec(objectMapper.writeValueAsString(canonical), extraPrice);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规格参数序列化失败");
        }
    }

    /**
     * 生成订单明细的规格快照：在规范快照基础上附带数据库当前的 extraPrice，
     * 使历史订单可还原成交构成。extraPrice 一律取自 t_product_attribute，不信任客户端值。
     */
    public String toOrderSnapshot(String specSnapshot, Map<String, BigDecimal> attrPriceMap) {
        ParsedSpec parsed = parse(specSnapshot, attrPriceMap);
        if (parsed.canonicalSnapshot().isEmpty()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(parsed.canonicalSnapshot());
            ArrayNode snapshot = objectMapper.createArrayNode();
            for (JsonNode node : root) {
                String name = node.get("name").asString();
                String value = node.get("value").asString();
                BigDecimal extraPrice = attrPriceMap.get(name + "|" + value);
                ObjectNode item = objectMapper.createObjectNode();
                item.put("name", name);
                item.put("value", value);
                item.put("extraPrice", extraPrice == null ? BigDecimal.ZERO : extraPrice);
                snapshot.add(item);
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规格参数序列化失败");
        }
    }
}
