package io.github.mangi.eta.data.db

import io.github.mangi.eta.data.model.CustomHeader
import io.github.mangi.eta.data.model.McpAuthorizationType
import io.github.mangi.eta.data.model.McpProtocolMode
import io.github.mangi.eta.data.model.McpServerSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class McpEntitiesTest {
    @Test
    fun legacyUnavailableReasonIsIgnoredWhenLoadingCachedTools() {
        val entity = McpServerEntity(
            id = "server",
            name = "Server",
            url = "https://example.com/mcp",
            enabled = true,
            protocolMode = McpProtocolMode.AUTO,
            authorizationType = McpAuthorizationType.NONE,
            customHeadersJson = """[{"name":"X-Test","value":"123"}]""",
            toolsJson = """[{"name":"create_task","inputSchemaJson":"{\"type\":\"object\",\"anyOf\":[]}","unavailableReason":"???? schema ??? anyOf"}]""",
            enabledToolNamesJson = """["create_task"]""",
            createdAt = 1L,
            sortOrder = 0,
            lastRefreshedAt = null,
            lastProtocolVersion = null,
            toolsExpireAt = null,
        )

        val server = entity.toDomain()

        assertEquals(listOf("create_task"), server.tools.map { it.name })
        assertEquals(listOf("create_task"), server.activeTools.map { it.name })
        assertEquals(listOf(CustomHeader("X-Test", "123")), server.customHeaders)

        val convertedEntity = server.toEntity()
        assertEquals(entity.customHeadersJson, convertedEntity.customHeadersJson)
    }
}
