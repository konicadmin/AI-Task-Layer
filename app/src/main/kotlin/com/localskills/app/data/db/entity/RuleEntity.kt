package com.localskills.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A scheduled or event-driven rule attached to a skill or a specific result.
 * `expression` is parsed by the rule engine — never executed as code.
 */
@Entity(
    tableName = "rules",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("skillId"), Index("enabled")],
)
data class RuleEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val resultId: String?,
    val expression: String,
    val action: String,
    val title: String?,
    val body: String?,
    val scheduleHint: String?,
    val enabled: Boolean,
    val lastFiredAt: Instant?,
    val createdAt: Instant,
)
