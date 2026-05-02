package com.localskills.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localskills.app.data.db.entity.ResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Query("SELECT * FROM results WHERE runId = :runId")
    suspend fun findForRun(runId: String): ResultEntity?

    @Query("SELECT * FROM results WHERE skillId = :skillId ORDER BY id DESC")
    fun observeForSkill(skillId: String): Flow<List<ResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: ResultEntity)

    @Query("UPDATE results SET confirmed = :confirmed WHERE id = :id")
    suspend fun setConfirmed(id: String, confirmed: Boolean)
}
