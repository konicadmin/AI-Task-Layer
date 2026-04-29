package com.localskills.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Installed skill manifest. The full manifest is held as JSON so the
 * runtime can re-parse with the canonical codec; indexed columns stay
 * cheap to query for the library list.
 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val manifestJson: String,
    val enabled: Boolean,
    val source: SkillSource,
    val installedAt: Instant,
    val updatedAt: Instant,
)

enum class SkillSource {
    USER_AUTHORED,
    IMPORTED,
    SEED,
}
