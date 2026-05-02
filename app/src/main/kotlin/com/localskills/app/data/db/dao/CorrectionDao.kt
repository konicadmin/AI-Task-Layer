package com.localskills.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.localskills.app.data.db.entity.CorrectionEntity

@Dao
interface CorrectionDao {
    @Query("SELECT * FROM corrections WHERE skillId = :skillId AND contextHash = :contextHash")
    suspend fun findMatches(skillId: String, contextHash: String): List<CorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(correction: CorrectionEntity)
}
