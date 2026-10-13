package pl.rytm.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: String,
    val mode: String,
    @ColumnInfo(name = "flexible_timeout_sec") val flexibleTimeoutSec: Int,
    @ColumnInfo(name = "interruption_behavior") val interruptionBehavior: String,
    @ColumnInfo(name = "target_duration_sec") val targetDurationSec: Int?,
    @ColumnInfo(name = "wake_time_minutes") val wakeTimeMinutes: Int?,
    @ColumnInfo(name = "leave_time_minutes") val leaveTimeMinutes: Int?,
    @ColumnInfo(name = "alarm_enabled") val alarmEnabled: Boolean,
    @ColumnInfo(name = "alarm_days") val alarmDays: Int,
    @ColumnInfo(name = "music_url") val musicUrl: String?,
    @ColumnInfo(name = "music_title") val musicTitle: String?,
    @ColumnInfo(name = "music_artist") val musicArtist: String?,
    @ColumnInfo(name = "music_artwork_url") val musicArtworkUrl: String?,
    @ColumnInfo(name = "music_kind") val musicKind: String?,
    @ColumnInfo(name = "music_embed_url") val musicEmbedUrl: String? = null,
    @ColumnInfo(name = "music_playback") val musicPlayback: String,
    @ColumnInfo(name = "music_volume") val musicVolume: Int,
    @ColumnInfo(name = "music_loop") val musicLoop: Boolean,
    @ColumnInfo(name = "voice_enabled") val voiceEnabled: Boolean,
    @ColumnInfo(name = "voice_volume") val voiceVolume: Int,
    @ColumnInfo(name = "voice_include_duration") val voiceIncludeDuration: Boolean,
    @ColumnInfo(name = "voice_warning_sec") val voiceWarningSec: Int,
    @ColumnInfo(name = "voice_start_message") val voiceStartMessage: String,
    @ColumnInfo(name = "voice_end_message") val voiceEndMessage: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routine_id")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val position: Int,
    val title: String,
    @ColumnInfo(name = "spoken_text") val spokenText: String,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "gap_before_sec") val gapBeforeSec: Int,
    val mandatory: Boolean,
    @ColumnInfo(name = "min_duration_sec") val minDurationSec: Int?,
    val notes: String,
)

data class RoutineWithTasks(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routine_id")
    val tasks: List<TaskEntity>,
)

/** Lekki odczyt rutyny (bez zadań) – np. do aktualizacji przykładów. */
data class RoutineMeta(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "alarm_enabled") val alarmEnabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
