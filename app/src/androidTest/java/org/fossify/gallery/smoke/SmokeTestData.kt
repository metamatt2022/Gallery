package org.fossify.gallery.smoke

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.fossify.gallery.databases.GalleryDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shared fixtures for Gallery instrumented smoke tests: app-state reset,
 * all-files access grant, and generated on-device media. Every helper is
 * self-contained so no test depends on another test's leftovers.
 */
object SmokeTestData {
    const val PREFS_NAME = "Prefs"
    const val DATABASE_NAME = "gallery.db"
    const val SEED_FOLDER_NAME = "swebench-smoke"
    const val SEED_IMAGE_COUNT = 3

    // First-run suppression flags. Gallery passes an empty release list to
    // commons' checkWhatsNew so no what's-new dialog can appear; the sideload
    // dialog is suppressed by pre-seeding the dismissed status instead.
    private const val KEY_APP_SIDELOADING_STATUS = "app_sideloading_status"
    private const val SIDELOADING_STATUS_DISMISSED = 2
    private const val KEY_LAST_VERSION = "last_version"

    private const val SCAN_TIMEOUT_SECONDS = 60L

    fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Wipes preferences and the Room database, drops the Glide disk cache, and
     * pre-seeds first-run suppression flags. Safe to call before any activity
     * has launched.
     */
    fun resetAppState() {
        val context = targetContext()
        GalleryDatabase.destroyInstance()
        context.deleteDatabase(DATABASE_NAME)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        prefs.edit()
            .putInt(KEY_APP_SIDELOADING_STATUS, SIDELOADING_STATUS_DISMISSED)
            .putInt(KEY_LAST_VERSION, currentVersionCode(context))
            .commit()

        deleteRecursively(File(context.cacheDir, "image_manager_disk_cache"))
    }

    /** Clears the app-private recycle bin (used by tests that delete media). */
    fun clearRecycleBin() {
        val context = targetContext()
        val bin = File(context.filesDir, "recycle_bin")
        deleteRecursively(bin)
    }

    /**
     * Grants MANAGE_EXTERNAL_STORAGE via the shell-backed appops command so
     * tests exercise the deterministic direct-file-access path instead of the
     * all-files prompt. Returns whether the grant is in effect.
     */
    fun grantAllFilesAccess(): Boolean {
        val context = targetContext()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
        return Environment.isExternalStorageManager()
    }

    /**
     * Generates [count] solid-color JPEGs under Pictures/[folderName] and
     * blocks until MediaStore has scanned every file. Returns absolute paths.
     */
    fun seedImages(
        folderName: String = SEED_FOLDER_NAME,
        count: Int = SEED_IMAGE_COUNT
    ): List<String> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            folderName
        )
        dir.mkdirs()
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN)
        val paths = (0 until count).map { index ->
            val file = File(dir, "seed_%02d.jpg".format(index))
            writeTestJpeg(file, colors[index % colors.size], index, 640 + index, 480)
            file.absolutePath
        }
        scanAndAwait(paths)
        return paths
    }

    /** Removes a folder previously created by [seedImages] from disk and MediaStore. */
    fun clearSeededMedia(folderName: String = SEED_FOLDER_NAME) {
        val context = targetContext()
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            folderName
        )
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
        try {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("${dir.absolutePath}%")
            )
        } catch (_: Exception) {
        }
        MediaScannerConnection.scanFile(context, arrayOf(dir.absolutePath), null, null)
    }

    private fun writeTestJpeg(file: File, color: Int, index: Int, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint().apply {
            this.color = Color.WHITE
            textSize = height / 3f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(index.toString(), width / 2f, height / 1.7f, paint)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }

    private fun scanAndAwait(paths: List<String>) {
        val context = targetContext()
        val latch = CountDownLatch(paths.size)
        val mimeTypes = paths.map { "image/jpeg" }.toTypedArray()
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), mimeTypes) { _, _ ->
            latch.countDown()
        }
        check(latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "MediaStore scan did not finish for $paths"
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    }
}
