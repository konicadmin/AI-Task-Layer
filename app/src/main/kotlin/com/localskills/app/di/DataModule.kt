package com.localskills.app.di

import android.content.Context
import androidx.room.Room
import com.localskills.app.data.db.AppDatabase
import com.localskills.app.data.db.dao.CorrectionDao
import com.localskills.app.data.db.dao.ResultDao
import com.localskills.app.data.db.dao.RuleDao
import com.localskills.app.data.db.dao.RunDao
import com.localskills.app.data.db.dao.SkillDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun provideSkillDao(db: AppDatabase): SkillDao = db.skillDao()
    @Provides fun provideRunDao(db: AppDatabase): RunDao = db.runDao()
    @Provides fun provideResultDao(db: AppDatabase): ResultDao = db.resultDao()
    @Provides fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()
    @Provides fun provideCorrectionDao(db: AppDatabase): CorrectionDao = db.correctionDao()
}
