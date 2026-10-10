package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeProjectDao {
    @Query("SELECT * FROM code_projects WHERE id = 1 LIMIT 1")
    fun observeActiveProject(): Flow<CodeProjectEntity?>

    @Query("SELECT * FROM code_projects WHERE id = 1 LIMIT 1")
    suspend fun getActiveProjectOnce(): CodeProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: CodeProjectEntity)
}
