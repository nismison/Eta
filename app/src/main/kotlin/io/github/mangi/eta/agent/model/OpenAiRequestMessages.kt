package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 将 Eta 会话消息投影为 OpenAI-compatible 请求所需的系统指令结构。 */
internal object OpenAiRequestMessages {
    private const val DEFAULT_CONTINUATION_PROMPT = "请基于当前任务背景与已执行的上下文继续执行。"

    fun forChatCompletions(source: JSONArray): JSONArray {
        val system = collectInstructions(source, SYSTEM_ROLES)
        return JSONArray().also { messages ->
            if (system.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", system))
            }
            val nonSystem = mutableListOf<JSONObject>()
            for (index in 0 until source.length()) {
                val message = source.optJSONObject(index) ?: continue
                if (message.optString("role") !in SYSTEM_ROLES) nonSystem.add(message)
            }
            // 确保对话的第一个非系统轮次必须是 user（Gemini、Claude、OpenRouter 等上游的强制协议约束）
            if (nonSystem.isNotEmpty() && nonSystem.first().optString("role") != "user") {
                messages.put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", DEFAULT_CONTINUATION_PROMPT)
                )
            }
            for (msg in nonSystem) {
                messages.put(msg)
            }
        }
    }

    fun responsesInstructions(source: JSONArray): String =
        collectInstructions(source, RESPONSES_INSTRUCTION_ROLES)

    private fun collectInstructions(source: JSONArray, roles: Set<String>): String =
        buildList {
            for (index in 0 until source.length()) {
                val message = source.optJSONObject(index) ?: continue
                if (message.optString("role") !in roles) continue
                providerMessageText(message.opt("content"))
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(::add)
            }
        }.joinToString("\n\n")

    private val SYSTEM_ROLES = setOf("system")
    private val RESPONSES_INSTRUCTION_ROLES = setOf("system", "developer")
}
