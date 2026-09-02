package io.github.mangi.eta.agent.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * 清洗与规范化 MCP 工具的 JSON Schema，以兼容各大主流 LLM 接口规范（OpenAI、Anthropic Claude、Gemini 等）。
 *
 * 核心处理规则：
 * 1. 确保顶层为 `type: "object"`，缺失 `properties` 时补齐 `{}`。
 * 2. 拍平形如 `anyOf: [{type: T}, {type: "null"}]` 或 `oneOf` 的可空包装，提取有效分支类型并移除 `null` 分支。
 * 3. 规范化 `type: "array"`：主流模型提供商要求 array 必须包含 `items`，若缺失则补齐默认 `items: {}`。
 * 4. 移除 `default: null` 等容易引起部分网关报错的值。
 * 5. 递归处理嵌套的 `properties`、`items` 以及组合字段。
 */
internal object McpSchemaSanitizer {

    fun sanitize(schemaJson: String): String = runCatching {
        val root = JSONObject(schemaJson.ifBlank { "{}" })
        sanitizeNode(root, isRoot = true).toString()
    }.getOrDefault(schemaJson)

    fun sanitize(schema: JSONObject): JSONObject =
        sanitizeNode(JSONObject(schema.toString()), isRoot = true)

    private fun sanitizeNode(node: JSONObject, isRoot: Boolean = false): JSONObject {
        // 1. 顶层必须声明 type: "object"
        if (isRoot) {
            val rootType = node.optString("type")
            if (rootType.isBlank() || rootType != "object") {
                node.put("type", "object")
            }
            if (!node.has("properties")) {
                node.put("properties", JSONObject())
            }
        }

        // 2. 拍平 anyOf / oneOf 的 nullable 结构：例如 [{"type": "array"}, {"type": "null"}]
        flattenNullableUnion(node, "anyOf")
        flattenNullableUnion(node, "oneOf")

        // 3. 处理 array 缺失 items 的情况
        if (node.optString("type") == "array" && !node.has("items")) {
            node.put("items", JSONObject())
        }

        // 4. 清理 default: null
        if (node.has("default") && node.isNull("default")) {
            node.remove("default")
        }

        // 5. 递归处理 properties
        node.optJSONObject("properties")?.let { properties ->
            val keys = properties.keys().asSequence().toList()
            for (key in keys) {
                val prop = properties.optJSONObject(key) ?: continue
                properties.put(key, sanitizeNode(prop, isRoot = false))
            }
        }

        // 6. 递归处理 items（如果是 JSONObject）
        node.optJSONObject("items")?.let { items ->
            node.put("items", sanitizeNode(items, isRoot = false))
        }

        // 7. 如果 anyOf/oneOf/allOf 仍然存在，递归清洗每个分支
        listOf("anyOf", "oneOf", "allOf").forEach { compositionKey ->
            node.optJSONArray(compositionKey)?.let { array ->
                for (i in 0 until array.length()) {
                    val branch = array.optJSONObject(i) ?: continue
                    array.put(i, sanitizeNode(branch, isRoot = false))
                }
            }
        }

        // 8. 递归处理 $defs / definitions
        listOf("\$defs", "definitions").forEach { defsKey ->
            node.optJSONObject(defsKey)?.let { defs ->
                val keys = defs.keys().asSequence().toList()
                for (key in keys) {
                    val def = defs.optJSONObject(key) ?: continue
                    defs.put(key, sanitizeNode(def, isRoot = false))
                }
            }
        }

        return node
    }

    /**
     * 检查并拍平形如 anyOf/oneOf: [{type: X}, {type: "null"}] 的结构。
     * 如果匹配到这种“非 null 有效分支 + null 分支”的模式，直接将有效分支合并到当前 node 并删除 composition key。
     */
    private fun flattenNullableUnion(node: JSONObject, key: String) {
        val array = node.optJSONArray(key) ?: return
        val validBranches = mutableListOf<JSONObject>()
        var hasNull = false

        for (i in 0 until array.length()) {
            val branch = array.optJSONObject(i) ?: continue
            val branchType = branch.optString("type")
            if (branchType == "null" || (branch.length() == 1 && branch.opt("type") == JSONObject.NULL)) {
                hasNull = true
            } else {
                validBranches += branch
            }
        }

        // 当包含 null 且只有一个有效分支时，如 [{ "type": "array" }, { "type": "null" }]
        if (hasNull && validBranches.size == 1) {
            val target = validBranches.first()
            node.remove(key)
            // 将 target 的所有属性合并到当前 node
            target.keys().forEach { propKey ->
                if (!node.has(propKey)) {
                    node.put(propKey, target.get(propKey))
                }
            }
            // 确保如果 target 是 array 且没有 items，补齐 items
            if (node.optString("type") == "array" && !node.has("items")) {
                node.put("items", JSONObject())
            }
        }
    }
}
