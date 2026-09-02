package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.ConversationContextCheckpointEntity
import io.github.mangi.eta.data.db.ConversationEntity
import io.github.mangi.eta.data.db.ConversationMessageEntity
import io.github.mangi.eta.data.db.ConversationStateEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.CustomHeader
import io.github.mangi.eta.data.model.McpAuthorizationType
import io.github.mangi.eta.data.model.McpProtocolMode
import io.github.mangi.eta.data.model.McpServerSetting
import io.github.mangi.eta.data.model.McpToolDefinition
import io.github.mangi.eta.data.model.ModelSource
import io.github.mangi.eta.data.model.withApiKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EtaBackupRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        SettingsDataStore.init(context)
        ProviderRepository.init(context)
        AgentMemoryRepository.init(context)
        McpServerRepository.init(context)
    }

    @Test
    fun exportAndImportRestoresProvidersConversationsAndMemory() = runBlocking {
        ProviderRepository.ensureBuiltInsMerged()
        val provider = ProviderRepository.allProviders().first().withApiKey("sk-backup-test")
        ProviderRepository.updateProvider(provider)
        SettingsDataStore.setSelection(provider.id, provider.models.first().id)
        AgentMemoryRepository.replaceAll("# 核心记忆\n喜欢 Kotlin")

        val mcpServer = McpServerSetting(
            id = "mcp-server-1",
            name = "Test MCP",
            url = "http://127.0.0.1:8080/mcp",
            enabled = true,
            protocolMode = McpProtocolMode.LATEST,
            authorizationType = McpAuthorizationType.BEARER,
            customHeaders = listOf(CustomHeader("X-Custom", "Value")),
            tools = listOf(
                McpToolDefinition(
                    name = "search_tool",
                    title = "Search",
                    description = "Search description",
                    inputSchemaJson = "{}",
                ),
            ),
            enabledToolNames = setOf("search_tool"),
        )
        McpServerRepository.add(mcpServer, bearerToken = "secret-token-123")

        val conversation = ConversationEntity(
            id = "conversation-backup",
            title = "备份会话",
            thinkingEnabled = true,
            createdAt = 1L,
            updatedAt = 2L,
        )
        EtaDatabase.get(context).conversationDao().replaceAll(
            conversations = listOf(conversation),
            messages = listOf(
                ConversationMessageEntity(
                    id = "message-backup",
                    conversationId = conversation.id,
                    sortIndex = 0,
                    type = "user",
                    content = "保留这条消息",
                ),
            ),
            contextCheckpoints = listOf(
                ConversationContextCheckpointEntity(
                    conversationId = conversation.id,
                    historyJson = "[]",
                ),
            ),
            state = ConversationStateEntity(selectedConversationId = conversation.id),
        )

        val output = ByteArrayOutputStream()
        val exported = EtaBackupRepository.export(context, output)
        assertEquals(1, exported.conversationCount)
        assertEquals(1, exported.mcpServerCount)
        assertTrue(exported.providerCount > 0)
        assertEquals("# 核心记忆\n喜欢 Kotlin", AgentMemoryRepository.snapshot().content)

        ProviderRepository.updateProvider(provider.withApiKey("changed"))
        AgentMemoryRepository.replaceAll("changed")
        EtaDatabase.get(context).conversationDao().replaceAll(
            conversations = emptyList(),
            messages = emptyList(),
            contextCheckpoints = emptyList(),
            state = null,
        )
        McpServerRepository.delete(mcpServer.id)
        assertTrue(McpServerRepository.servers().isEmpty())
        assertNull(McpServerRepository.bearerToken(mcpServer.id))

        val imported = EtaBackupRepository.import(
            context,
            ByteArrayInputStream(output.toByteArray()),
        )
        assertEquals(1, imported.conversationCount)
        assertEquals(1, imported.mcpServerCount)
        assertEquals("# 核心记忆\n喜欢 Kotlin", AgentMemoryRepository.snapshot().content)
        assertEquals(
            "保留这条消息",
            EtaDatabase.get(context).conversationDao().messages().single().content,
        )
        val restoredSettings = SettingsDataStore.settings()
        assertEquals(provider.id, restoredSettings.selectedProviderId)
        assertEquals(provider.models.first().id, restoredSettings.selectedModelId)
        assertEquals("sk-backup-test", ProviderRepository.providerById(provider.id)?.apiKey)
        assertEquals(ModelSource.CATALOG, ProviderRepository.providerById(provider.id)?.models?.first()?.source)

        val restoredMcpServers = McpServerRepository.servers()
        assertEquals(1, restoredMcpServers.size)
        val restoredMcp = restoredMcpServers.first()
        assertEquals("mcp-server-1", restoredMcp.id)
        assertEquals("Test MCP", restoredMcp.name)
        assertEquals("http://127.0.0.1:8080/mcp", restoredMcp.url)
        assertEquals(McpAuthorizationType.BEARER, restoredMcp.authorizationType)
        assertEquals("secret-token-123", McpServerRepository.bearerToken(restoredMcp.id))
        assertEquals(listOf(CustomHeader("X-Custom", "Value")), restoredMcp.customHeaders)
        assertEquals(1, restoredMcp.tools.size)
        assertEquals("search_tool", restoredMcp.tools.first().name)
        assertEquals(setOf("search_tool"), restoredMcp.enabledToolNames)
    }

    @Test(expected = EtaBackupException::class)
    fun rejectsUnknownBackupFormatBeforeChangingData(): Unit = runBlocking {
        EtaBackupRepository.inspect(
            ByteArrayInputStream("{\"format\":\"other\",\"schemaVersion\":1,\"exportedAt\":0}".toByteArray())
        )
    }

    @Test
    fun compatibleWithV1BackupWithoutMcpServers(): Unit = runBlocking {
        val legacyJson = """
            {
                "format": "eta-backup",
                "schemaVersion": 1,
                "exportedAt": 123456789,
                "providers": [],
                "conversations": [],
                "messages": [],
                "contextCheckpoints": [],
                "memoryMd": "旧版备份无 MCP"
            }
        """.trimIndent()
        val summary = EtaBackupRepository.inspect(ByteArrayInputStream(legacyJson.toByteArray()))
        assertEquals(0, summary.mcpServerCount)
        assertEquals(0, summary.providerCount)
        assertEquals(0, summary.conversationCount)

        val imported = EtaBackupRepository.import(context, ByteArrayInputStream(legacyJson.toByteArray()))
        assertEquals(0, imported.mcpServerCount)
        assertEquals("旧版备份无 MCP", AgentMemoryRepository.snapshot().content)
        assertTrue(McpServerRepository.servers().isEmpty())
    }

    @Test
    fun restoresMcpServerWithCustomHeaders(): Unit = runBlocking {
        val jsonWithHeaders = """
            {
                "format": "eta-backup",
                "schemaVersion": 1,
                "exportedAt": 123456789,
                "providers": [],
                "conversations": [],
                "messages": [],
                "contextCheckpoints": [],
                "memoryMd": "",
                "mcpServers": [
                    {
                        "server": {
                            "id": "mcp-headers-test",
                            "name": "Header Server",
                            "url": "https://example.com/mcp",
                            "enabled": true,
                            "protocolMode": "auto",
                            "authorizationType": "bearer",
                            "customHeaders": [
                                {"name": "X-Custom-Auth", "value": "custom-val"},
                                {"name": "X-Region", "value": "ap-east"}
                            ],
                            "tools": [],
                            "enabledToolNames": []
                        },
                        "bearerToken": "token-xyz"
                    }
                ]
            }
        """.trimIndent()

        val imported = EtaBackupRepository.import(context, ByteArrayInputStream(jsonWithHeaders.toByteArray()))
        assertEquals(1, imported.mcpServerCount)

        val server = McpServerRepository.serverById("mcp-headers-test")
        org.junit.Assert.assertNotNull(server)
        assertEquals(2, server!!.customHeaders.size)
        assertEquals(CustomHeader("X-Custom-Auth", "custom-val"), server.customHeaders[0])
        assertEquals(CustomHeader("X-Region", "ap-east"), server.customHeaders[1])
        assertEquals("token-xyz", McpServerRepository.bearerToken("mcp-headers-test"))
    }
}
