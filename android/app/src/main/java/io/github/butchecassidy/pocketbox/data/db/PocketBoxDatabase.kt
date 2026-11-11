// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-device library database. It is never backed up or transferred (see data_extraction_rules.xml).
 * Every schema change ships a migration so playlists and history are never wiped.
 */
@Database(
    entities = [
        FolderEntity::class,
        ExcludedFolderEntity::class,
        HeldFileEntity::class,
        TrackEntity::class,
        TrackFtsEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ReleaseOverrideEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        PlayStatsEntity::class,
        RecentSearchEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
        EqPresetEntity::class,
        LyricOffsetEntity::class,
        BookProgressEntity::class,
        BookmarkEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 1 → 2: audiobook folders, bookmarks and listening progress, lyrics timing. Only adds; keeps everything.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class PocketBoxDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun scanDao(): ScanDao
    abstract fun userDataDao(): UserDataDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun bookDao(): BookDao

    companion object {
        private const val NAME = "pocketbox.db"

        fun create(context: Context): PocketBoxDatabase =
            Room.databaseBuilder(context.applicationContext, PocketBoxDatabase::class.java, NAME).build()
    }
}
