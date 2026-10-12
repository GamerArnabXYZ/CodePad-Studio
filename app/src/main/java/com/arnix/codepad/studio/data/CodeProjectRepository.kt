package com.arnix.codepad.studio.data

import kotlinx.coroutines.flow.Flow

class CodeProjectRepository(private val dao: CodeProjectDao) {
    val activeProjectFlow: Flow<CodeProjectEntity?> = dao.observeActiveProject()

    suspend fun getOrInitProject(): CodeProjectEntity {
        val existing = dao.getActiveProjectOnce()
        if (existing != null) return existing
        val defaultProject = CodeProjectEntity()
        dao.upsertProject(defaultProject)
        return defaultProject
    }

    suspend fun saveProject(project: CodeProjectEntity) {
        dao.upsertProject(project.copy(lastSavedTimestamp = System.currentTimeMillis()))
    }
}
