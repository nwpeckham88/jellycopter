package org.jellycopter.core.repository

import kotlinx.coroutines.flow.Flow
import org.jellycopter.core.database.ChannelDao
import org.jellycopter.core.database.ChannelEntity

interface ChannelRepository {
    fun getAllChannels(): Flow<List<ChannelEntity>>
    suspend fun getChannel(channelId: String): ChannelEntity?
    suspend fun saveChannel(channel: ChannelEntity)
    suspend fun deleteChannel(channelId: String)
}

class RoomChannelRepository(
    private val channelDao: ChannelDao
) : ChannelRepository {

    override fun getAllChannels(): Flow<List<ChannelEntity>> {
        return channelDao.getAllChannelsFlow()
    }

    override suspend fun getChannel(channelId: String): ChannelEntity? {
        return channelDao.getChannelById(channelId)
    }

    override suspend fun saveChannel(channel: ChannelEntity) {
        channelDao.upsertChannel(channel)
    }

    override suspend fun deleteChannel(channelId: String) {
        channelDao.deleteChannel(channelId)
    }
}
