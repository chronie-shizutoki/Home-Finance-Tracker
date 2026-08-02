package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the members table.
 *
 * Note: The local database only stores the currently logged-in user.
 * Full member lists are managed on the server side. This DAO provides
 * read access via reactive Flow and CRUD operations for the local copy.
 */
@Dao
interface MemberDao {
    
    /** Observes the current logged-in member reactively. */
    @Query("SELECT * FROM members LIMIT 1")
    fun getCurrentMember(): Flow<MemberEntity?>
    
    /** Fetches a specific member by server ID. */
    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: String): MemberEntity?
    
    /** Creates or replaces a member record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)
    
    /** Updates an existing member. */
    @Update
    suspend fun updateMember(member: MemberEntity)
    
    /** Removes a member from the local database. */
    @Delete
    suspend fun deleteMember(member: MemberEntity)
    
    /** Purges all local member records. */
    @Query("DELETE FROM members")
    suspend fun deleteAllMembers()
}
