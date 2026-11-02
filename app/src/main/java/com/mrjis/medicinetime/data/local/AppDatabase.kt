package com.mrjis.medicinetime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.dao.MedicineDao
import com.mrjis.medicinetime.data.local.dao.ProfileDao
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.data.local.entity.MedicineEntity
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.data.local.entity.SchedulePhaseEntity
import com.mrjis.medicinetime.data.local.entity.ScheduleSlotEntity

@Database(
    entities = [
        ProfileEntity::class,
        MedicineEntity::class,
        SchedulePhaseEntity::class,
        ScheduleSlotEntity::class,
        DoseLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun medicineDao(): MedicineDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_time.db"
                ).build().also { instance = it }
            }
    }
}