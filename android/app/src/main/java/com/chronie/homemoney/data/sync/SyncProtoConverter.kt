package com.chronie.homemoney.data.sync

import com.chronie.homemoney.data.sync.generated.DeviceSyncData
import com.chronie.homemoney.data.sync.generated.SyncEntity
import com.chronie.homemoney.domain.sync.DeviceSyncData as DomainSyncData
import com.chronie.homemoney.domain.sync.SyncEntity as DomainSyncEntity

/**
 * Sync Proto Converter
 * Converts between Domain model and Protobuf model
 */
object SyncProtoConverter {
    fun toProto(data: DomainSyncData): DeviceSyncData {
        val builder = DeviceSyncData.newBuilder()
            .setDeviceId(data.deviceId)
            .setDeviceName(data.deviceName)
            .setSyncTimestamp(data.syncTimestamp)
        
        data.entities.forEach { 
            builder.addEntities(toProto(it))
        }
        
        return builder.build()
    }

    fun toProto(entity: DomainSyncEntity): SyncEntity {
        return SyncEntity.newBuilder()
            .setEntityType(entity.entityType)
            .setEntityId(entity.entityId)
            .setOperation(entity.operation)
            .setData(entity.data)
            .setTimestamp(entity.timestamp)
            .build()
    }

    fun toDomain(proto: DeviceSyncData): DomainSyncData {
        return DomainSyncData(
            deviceId = proto.deviceId,
            deviceName = proto.deviceName,
            syncTimestamp = proto.syncTimestamp,
            entities = proto.entitiesList.map { toDomain(it) }
        )
    }

    fun toDomain(proto: SyncEntity): DomainSyncEntity {
        return DomainSyncEntity(
            entityType = proto.entityType,
            entityId = proto.entityId,
            operation = proto.operation,
            data = proto.data,
            timestamp = proto.timestamp
        )
    }
}
