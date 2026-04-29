package com.localskills.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.localskills.app.data.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE skillId = :skillId")
    fun observeForSkill(skillId: String): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity)

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("UPDATE rules SET lastFiredAt = :firedAt WHERE id = :id")
    suspend fun markFired(id: String, firedAt: Instant)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
