package com.localskills.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A user correction captured during the review step. The contextHash
 * lets the router prefer the corrected mapping the next time a similar
 * input is seen, without keeping the raw input.
 */
@Entity(
    tableName = "corrections",
    indices = [Index("skillId"), Index("contextHash")],
)
data class CorrectionEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val fieldName: String,
    val originalValue: String?,
    val correctedValue: String,
    val contextHash: String,
    val createdAt: Instant,
)
