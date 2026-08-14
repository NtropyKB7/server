package com.ntropy.common.util;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** JSON 객체와 배열의 snake_case 필드명을 재귀적으로 camelCase로 변환한다. */
public final class JsonNamingConverter {

    private JsonNamingConverter() {
    }

    public static JsonNode toCamelCase(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                result.add(toCamelCase(child));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(snakeToCamel(field.getKey()), toCamelCase(field.getValue()));
            }
            return result;
        }
        return node;
    }

    private static String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperCaseNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperCaseNext = true;
            } else if (upperCaseNext) {
                result.append(Character.toUpperCase(character));
                upperCaseNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
