package com.arnix.codepad.studio.data

import kotlinx.coroutines.flow.Flow

class CodeProjectRepository(private val dao: CodeProjectDao) {
    val activeProjectFlow: Flow<CodeProjectEntity?> = dao.observeActiveProject()

    suspend fun getOrInitProject(): CodeProjectEntity {
        return try {
            val existing = dao.getActiveProjectOnce()
            if (existing != null) return existing
            val defaultProject = CodeProjectEntity()
            dao.upsertProject(defaultProject)
            defaultProject
        } catch (_: Throwable) {
            CodeProjectEntity()
        }
    }

    suspend fun saveProject(project: CodeProjectEntity) {
        try {
            dao.upsertProject(project.copy(lastSavedTimestamp = System.currentTimeMillis()))
        } catch (_: Throwable) {
        }
    }
}
