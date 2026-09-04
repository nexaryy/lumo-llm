package me.proton.android.lumo.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.android.lumo.data.db.dao.LumoDao
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.llm.model.ApiConfig
import me.proton.android.lumo.llm.model.ResolvedModelConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LumoRepository @Inject constructor(
    private val lumoDao: LumoDao,
    private val apiConfigRepository: ApiConfigRepository,
) {

    fun observeAll(): Flow<List<LumoEntity>> = lumoDao.observeAll()

    fun observeById(id: Long): Flow<LumoEntity?> = lumoDao.observeById(id)

    suspend fun getById(id: Long): LumoEntity? = lumoDao.getById(id)

    suspend fun create(
        name: String,
        systemPrompt: String = "",
        modelName: String? = null,
        temperature: Float? = null,
        accentColor: String = "#6D4AFF",
        avatarTag: String = "🐱",
        description: String = "",
    ): LumoEntity {
        val now = System.currentTimeMillis()
        val lumo = LumoEntity(
            name = name.ifBlank { "Untitled Lumo" },
            description = description,
            systemPrompt = systemPrompt,
            modelName = modelName,
            temperature = temperature,
            accentColor = accentColor,
            avatarTag = avatarTag,
            createdAt = now,
            updatedAt = now,
        )
        val id = lumoDao.insert(lumo)
        return lumo.copy(id = id)
    }

    suspend fun update(lumo: LumoEntity) {
        lumoDao.update(lumo.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(lumo: LumoEntity) = lumoDao.delete(lumo)

    suspend fun archive(id: Long) = lumoDao.archive(id)

    /**
     * Returns the effective model config for a Lumo: global defaults from [ApiConfigRepository]
     * overridden by anything the Lumo itself sets.
     */
    suspend fun resolveConfig(lumo: LumoEntity): ResolvedModelConfig {
        val api = apiConfigRepository.current()
        return ResolvedModelConfig(
            providerType = api.providerType,
            baseUrl = api.baseUrl,
            apiKey = api.apiKey,
            model = lumo.modelName ?: api.defaultModel,
            temperature = lumo.temperature ?: api.defaultTemperature,
            maxTokens = api.defaultMaxTokens,
            systemPrompt = lumo.systemPrompt,
            extraHeaders = parseExtraHeaders(api.extraHeadersRaw),
            customBodyTemplate = api.customBodyTemplate,
            customMethod = api.customMethod,
            customResponsePath = api.customResponsePath,
            requestTimeoutSeconds = api.requestTimeoutSeconds,
            connectTimeoutSeconds = api.connectTimeoutSeconds,
        )
    }

    private fun parseExtraHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .filter { it.first.isNotBlank() }
            .toMap()
    }
}
