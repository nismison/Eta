package io.github.mangi.eta.agent.mcp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSchemaSanitizerTest {

    @Test
    fun flattensNullableAnyOfAndFillsMissingArrayItems() {
        val rawSchema = """
        {
          "type": "object",
          "properties": {
            "argv": { "anyOf": [{ "type": "array" }, { "type": "null" }], "default": null },
            "cmd": { "type": "string", "default": "" }
          }
        }
        """.trimIndent()

        val sanitized = JSONObject(McpSchemaSanitizer.sanitize(rawSchema))
        val properties = sanitized.getJSONObject("properties")

        val argv = properties.getJSONObject("argv")
        assertEquals("array", argv.getString("type"))
        assertFalse("anyOf should be removed", argv.has("anyOf"))
        assertTrue("array without items should be given an empty items object", argv.has("items"))
        assertFalse("null default should be removed", argv.has("default"))

        val cmd = properties.getJSONObject("cmd")
        assertEquals("string", cmd.getString("type"))
        assertEquals("", cmd.getString("default"))
    }

    @Test
    fun sanitizesComplexToolsLikeTraceJava() {
        val rawSchema = """
        {
          "type": "object",
          "properties": {
            "package": { "type": "string" },
            "class_name": { "type": "string" },
            "method": { "type": "string" },
            "params": { "anyOf": [{ "type": "array" }, { "type": "null" }], "default": null },
            "args_render": { "type": "string", "default": "tostring" },
            "replace_return": { "anyOf": [{ "type": "object" }, { "type": "null" }], "default": null }
          },
          "required": ["package", "class_name", "method"]
        }
        """.trimIndent()

        val sanitized = JSONObject(McpSchemaSanitizer.sanitize(rawSchema))
        val properties = sanitized.getJSONObject("properties")

        val params = properties.getJSONObject("params")
        assertEquals("array", params.getString("type"))
        assertTrue(params.has("items"))
        assertFalse(params.has("anyOf"))
        assertFalse(params.has("default"))

        val replaceReturn = properties.getJSONObject("replace_return")
        assertEquals("object", replaceReturn.getString("type"))
        assertFalse(replaceReturn.has("anyOf"))
        assertFalse(replaceReturn.has("default"))

        val required = sanitized.getJSONArray("required")
        assertEquals(3, required.length())
    }

    @Test
    fun ensuresRootIsObjectAndHasProperties() {
        val emptySchema = "{}"
        val sanitized = JSONObject(McpSchemaSanitizer.sanitize(emptySchema))
        assertEquals("object", sanitized.getString("type"))
        assertNotNull(sanitized.getJSONObject("properties"))
    }
}
