package com.localskills.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One execution of a skill on a single input. Inputs are kept by reference
 * to app-specific internal storage; raw text is inlined when small.
 */
@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("skillId"), Index("createdAt")],
)
data class RunEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val inputKind: String,
    val inputText: String?,
    val artifactPath: String?,
    val sourceAppHint: String?,
    val createdAt: Instant,
    val durationMs: Long?,
    val status: RunStatus,
    val errorMessage: String?,
)

enum class RunStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}
