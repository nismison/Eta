package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestMessagesTest {

    @Test
    fun injectsContinuationUserMessageWhenFirstMessageIsAssistant() {
        val input = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "你是手机 Agent"))
            put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", "")
                    .put(
                        "tool_calls",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "call_1")
                                .put("type", "function")
                                .put("function", JSONObject().put("name", "terminal").put("arguments", "{}"))
                        )
                    )
            )
            put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", "call_1")
                    .put("content", "success")
            )
        }

        val output = OpenAiRequestMessages.forChatCompletions(input)

        // output[0] 应该为 system，output[1] 应该自动注入 user 引导消息，保护下游如 Gemini 的交互协议
        assertEquals("system", output.getJSONObject(0).getString("role"))
        assertEquals("user", output.getJSONObject(1).getString("role"))
        assertTrue(output.getJSONObject(1).getString("content").contains("继续执行"))
        assertEquals("assistant", output.getJSONObject(2).getString("role"))
        assertEquals("tool", output.getJSONObject(3).getString("role"))
    }

    @Test
    fun preservesNormalUserMessageAsFirstTurn() {
        val input = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "你是手机 Agent"))
            put(JSONObject().put("role", "user").put("content", "请帮我查天气"))
            put(JSONObject().put("role", "assistant").put("content", "正在查询"))
        }

        val output = OpenAiRequestMessages.forChatCompletions(input)

        assertEquals("system", output.getJSONObject(0).getString("role"))
        assertEquals("user", output.getJSONObject(1).getString("role"))
        assertEquals("请帮我查天气", output.getJSONObject(1).getString("content"))
        assertEquals("assistant", output.getJSONObject(2).getString("role"))
    }
}
