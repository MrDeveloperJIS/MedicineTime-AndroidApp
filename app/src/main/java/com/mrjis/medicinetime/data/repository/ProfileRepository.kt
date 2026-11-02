package com.mrjis.medicinetime.data.repository

import com.mrjis.medicinetime.data.local.dao.ProfileDao
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.util.newId
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {

    fun observeAll(): Flow<List<ProfileEntity>> = dao.observeAll()

    fun observe(id: String): Flow<ProfileEntity?> = dao.observe(id)

    suspend fun get(id: String): ProfileEntity? = dao.get(id)

    suspend fun count(): Int = dao.count()

    /** Creates a profile with default meal times and returns it. */
    suspend fun create(name: String, avatarColor: Int): ProfileEntity {
        val now = System.currentTimeMillis()
        val profile = ProfileEntity(
            id = newId(),
            name = name.trim(),
            avatarColor = avatarColor,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(profile)
        return profile
    }

    suspend fun update(profile: ProfileEntity) {
        dao.upsert(profile.copy(name = profile.name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}