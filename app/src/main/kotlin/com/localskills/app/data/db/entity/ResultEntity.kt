package com.localskills.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Structured output from a run. `payloadJson` matches the skill's
 * declared output_schema; `confidence` is computed deterministically
 * from validator pass-rate, evidence coverage, and source signals.
 */
@Entity(
    tableName = "results",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("skillId")],
)
data class ResultEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val skillId: String,
    val payloadJson: String,
    val confidence: Double,
    val confirmed: Boolean,
)
