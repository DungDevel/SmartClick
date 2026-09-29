package com.example.smartclick.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.smartclick.app.domain.model.TvProtocol

/**
 * FIX-12: Room migrations thay cho `fallbackToDestructiveMigration()`.
 *
 * `fallbackToDestructiveMigration()` xoá sạch DB (mất toàn bộ TV đã ghép nối, credentialsRef...)
 * mỗi khi tăng schema version. Ở đây ta khai báo migration tường minh để giữ nguyên dữ liệu người dùng.
 *
 * Khi thêm/sửa entity trong tương lai: tăng version trong [AppDatabase], để Room export schema JSON mới,
 * rồi bổ sung một Migration(n, n+1) tương ứng vào [ALL_MIGRATIONS].
 */

/**
 * v1 -> v2 (FW-1): thêm cột `protocol` (NOT NULL) và `credentialsRef` (nullable) vào bảng `tv_devices`.
 *
 * `protocol` NOT NULL nên bắt buộc có DEFAULT khi ALTER trên các dòng đã tồn tại;
 * dùng UNKNOWN để khớp giá trị mặc định của entity, driver sẽ fingerprint lại khi nối.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tv_devices ADD COLUMN protocol TEXT NOT NULL DEFAULT '${TvProtocol.UNKNOWN.name}'"
        )
        db.execSQL("ALTER TABLE tv_devices ADD COLUMN credentialsRef TEXT")
    }
}

/** Danh sách toàn bộ migration, truyền vào `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2
)
