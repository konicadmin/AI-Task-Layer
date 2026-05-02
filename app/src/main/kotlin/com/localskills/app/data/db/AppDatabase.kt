package com.localskills.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.localskills.app.data.db.dao.CorrectionDao
import com.localskills.app.data.db.dao.ResultDao
import com.localskills.app.data.db.dao.RuleDao
import com.localskills.app.data.db.dao.RunDao
import com.localskills.app.data.db.dao.SkillDao
import com.localskills.app.data.db.entity.CorrectionEntity
import com.localskills.app.data.db.entity.ResultEntity
import com.localskills.app.data.db.entity.RuleEntity
import com.localskills.app.data.db.entity.RunEntity
import com.localskills.app.data.db.entity.SkillEntity

@Database(
    entities = [
        SkillEntity::class,
        RunEntity::class,
        ResultEntity::class,
        RuleEntity::class,
        CorrectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun skillDao(): SkillDao
    abstract fun runDao(): RunDao
    abstract fun resultDao(): ResultDao
    abstract fun ruleDao(): RuleDao
    abstract fun correctionDao(): CorrectionDao

    companion object {
        const val NAME: String = "local-skills.db"
    }
}
