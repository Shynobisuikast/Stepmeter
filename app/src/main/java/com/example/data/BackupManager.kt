package com.example.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.domain.LocalBackupInfo
import com.example.domain.ThemeMode
import com.example.domain.UserProfile
import com.example.security.CryptoManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports and restores ALL settings, goals, and activity history in a GZIP-compressed CSV format,
 * encrypted end-to-end with AES-256-GCM and stored strictly inside device memory.
 */
class BackupManager(
  private val context: Context,
  private val db: AppDatabase,
  private val preferences: PreferencesRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val backupDir: File
    get() = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

  suspend fun buildCsvPayload(): String =
    withContext(ioDispatcher) {
      val profile = preferences.getProfileSync()
      val theme = preferences.getThemeModeSync()
      val goals = db.goalDao().getAllGoalsSync()
      val activities = db.dailyActivityDao().getAllSync()

      buildString {
        appendLine("#STEPMETER_BACKUP_CSV_V2")
        appendLine("SECTION,KEY_OR_ID,COL1,COL2,COL3,COL4,COL5,COL6,COL7")
        appendLine(
          listOf(
              "SETTING",
              "PROFILE",
              escapeCsv(profile.displayName),
              profile.heightCm.toString(),
              profile.weightKg.toString(),
              profile.customStrideCm.toString(),
              profile.useCustomStride.toString(),
              theme.name,
              "",
            )
            .joinToString(",")
        )
        for (goal in goals) {
          appendLine(
            listOf(
                "GOAL",
                goal.id.toString(),
                escapeCsv(goal.periodType),
                escapeCsv(goal.metricType),
                goal.targetValue.toString(),
                goal.isActive.toString(),
                goal.createdAt.toString(),
                "",
                "",
              )
              .joinToString(",")
          )
        }
        for (act in activities) {
          appendLine(
            listOf(
                "ACTIVITY",
                act.epochDay.toString(),
                act.yearMonth.toString(),
                act.totalSteps.toString(),
                act.distanceKm.toString(),
                act.caloriesKcal.toString(),
                act.activeMinutes.toString(),
                act.updatedAt.toString(),
                "",
              )
              .joinToString(",")
          )
        }
      }
    }

  private fun compressGzip(rawBytes: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream(rawBytes.size / 2)
    GZIPOutputStream(bos).use { gzip -> gzip.write(rawBytes) }
    return bos.toByteArray()
  }

  private fun decompressIfGzip(rawBytes: ByteArray): ByteArray {
    val isGzip =
      rawBytes.size >= 2 &&
        (rawBytes[0].toInt() and 0xFF) == 0x1F &&
        (rawBytes[1].toInt() and 0xFF) == 0x8B
    if (!isGzip) return rawBytes
    return GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
  }

  suspend fun createLocalEncryptedBackup(password: String?): LocalBackupInfo =
    withContext(ioDispatcher) {
      val compressedBytes = compressGzip(buildCsvPayload().toByteArray(Charsets.UTF_8))
      val cleanPw = password?.trim().orEmpty()
      val encryptedBytes =
        if (cleanPw.isNotEmpty()) {
          CryptoManager.encryptWithPassword(compressedBytes, cleanPw)
        } else {
          CryptoManager.encryptWithKeystore(compressedBytes)
        }
      val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val suffix = if (cleanPw.isNotEmpty()) "_pw.csv.enc" else "_ks.csv.enc"
      val file = File(backupDir, "stepmeter_yedek_$stamp$suffix")
      file.writeBytes(encryptedBytes)
      LocalBackupInfo(
        fileName = file.name,
        createdAtMillis = file.lastModified(),
        sizeBytes = file.length(),
        isPasswordProtected = cleanPw.isNotEmpty(),
      )
    }

  suspend fun exportEncryptedCsvToUri(uri: Uri, password: String?) =
    withContext(ioDispatcher) {
      val compressedBytes = compressGzip(buildCsvPayload().toByteArray(Charsets.UTF_8))
      val cleanPw = password?.trim().orEmpty()
      val encryptedBytes =
        if (cleanPw.isNotEmpty()) {
          CryptoManager.encryptWithPassword(compressedBytes, cleanPw)
        } else {
          CryptoManager.encryptWithKeystore(compressedBytes)
        }
      context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(encryptedBytes)
        out.flush()
      } ?: throw IllegalStateException("Seçilen dosya konumuna yazılamadı.")
    }

  suspend fun listLocalBackups(): List<LocalBackupInfo> =
    withContext(ioDispatcher) {
      backupDir
        .listFiles { file -> file.isFile && file.name.endsWith(".csv.enc") }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
          val headerBytes =
            runCatching { file.inputStream().use { it.readNBytes(16) } }.getOrDefault(ByteArray(0))
          LocalBackupInfo(
            fileName = file.name,
            createdAtMillis = file.lastModified(),
            sizeBytes = file.length(),
            isPasswordProtected = CryptoManager.isPasswordProtectedPayload(headerBytes),
          )
        }
        ?: emptyList()
    }

  suspend fun restoreFromLocalFile(fileName: String, password: String?): RestoreResult =
    withContext(ioDispatcher) {
      val target = File(backupDir, fileName)
      require(target.exists()) { "Yedek dosyası bulunamadı." }
      restoreFromBytes(target.readBytes(), password)
    }

  suspend fun restoreFromUri(uri: Uri, password: String?): RestoreResult =
    withContext(ioDispatcher) {
      val bytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IllegalStateException("Seçilen yedek dosyası okunamadı.")
      restoreFromBytes(bytes, password)
    }

  suspend fun deleteLocalBackup(fileName: String) =
    withContext(ioDispatcher) {
      val target = File(backupDir, fileName)
      if (target.exists()) target.delete()
    }

  suspend fun restoreFromBytes(payload: ByteArray, password: String?): RestoreResult =
    withContext(ioDispatcher) {
      val decryptedBytes =
        when {
          CryptoManager.isPasswordProtectedPayload(payload) -> {
            val pw = password?.trim().orEmpty()
            require(pw.isNotEmpty()) { "Bu yedek dosyası parola korumalıdır. Lütfen parolayı girin." }
            CryptoManager.decryptWithPassword(payload, pw)
          }
          CryptoManager.isKeystorePayload(payload) -> CryptoManager.decryptWithKeystore(payload)
          else -> throw IllegalArgumentException("Dosya geçerli bir şifreli Stepmeter CSV yedeği değil.")
        }
      val plainBytes = decompressIfGzip(decryptedBytes)
      val csvText = String(plainBytes, Charsets.UTF_8)
      parseAndApplyCsv(csvText)
    }

  private suspend fun parseAndApplyCsv(csvText: String): RestoreResult {
    val lines = csvText.lines().filter { it.isNotBlank() }
    val header = lines.firstOrNull().orEmpty()
    val isV2 = header.startsWith("#STEPMETER_BACKUP_CSV_V2")
    val isV1 = header.startsWith("#ADIMIZI_BACKUP_CSV_V1")
    require(isV2 || isV1) { "Yedek dosya başlığı geçersiz." }

    var restoredProfile: UserProfile? = null
    var restoredTheme: ThemeMode? = null
    val parsedGoals = mutableListOf<GoalEntity>()
    val parsedActivities = mutableListOf<DailyActivityEntity>()

    for (line in lines.drop(2)) {
      val cols = parseCsvLine(line)
      if (cols.isEmpty()) continue
      when (cols[0]) {
        "SETTING" -> {
          if (cols.getOrNull(1) == "PROFILE" && cols.size >= 8) {
            restoredProfile =
              UserProfile(
                displayName = cols[2].ifBlank { "Yürüyüşçü" },
                heightCm = cols[3].toIntOrNull() ?: 172,
                weightKg = cols[4].toFloatOrNull() ?: 72f,
                customStrideCm = cols[5].toIntOrNull() ?: 72,
                useCustomStride = cols[6].toBooleanStrictOrNull() ?: false,
              )
            restoredTheme = runCatching { ThemeMode.valueOf(cols[7]) }.getOrNull()
          }
        }
        "GOAL" -> {
          if (cols.size >= 7) {
            parsedGoals.add(
              GoalEntity(
                periodType = cols[2],
                metricType = cols[3],
                targetValue = cols[4].toIntOrNull() ?: 8000,
                isActive = cols[5].toBooleanStrictOrNull() ?: true,
                createdAt = cols[6].toLongOrNull() ?: System.currentTimeMillis(),
              )
            )
          }
        }
        "ACTIVITY" -> {
          if (isV2 && cols.size >= 8) {
            val epochDay = cols[1].toLongOrNull() ?: continue
            parsedActivities.add(
              DailyActivityEntity(
                epochDay = epochDay,
                yearMonth = cols[2].toIntOrNull() ?: continue,
                totalSteps = cols[3].toIntOrNull() ?: 0,
                distanceKm = cols[4].toDoubleOrNull() ?: 0.0,
                caloriesKcal = cols[5].toDoubleOrNull() ?: 0.0,
                activeMinutes = cols[6].toIntOrNull() ?: 0,
                updatedAt = cols[7].toLongOrNull() ?: System.currentTimeMillis(),
              )
            )
          } else if (isV1 && cols.size >= 10) {
            val epochDay = cols[1].toLongOrNull() ?: continue
            parsedActivities.add(
              DailyActivityEntity(
                epochDay = epochDay,
                yearMonth = cols[2].toIntOrNull() ?: continue,
                totalSteps = cols[5].toIntOrNull() ?: 0,
                distanceKm = cols[6].toDoubleOrNull() ?: 0.0,
                caloriesKcal = cols[7].toDoubleOrNull() ?: 0.0,
                activeMinutes = cols[8].toIntOrNull() ?: 0,
                updatedAt = cols[9].toLongOrNull() ?: System.currentTimeMillis(),
              )
            )
          }
        }
      }
    }

    restoredProfile?.let { preferences.saveProfile(it) }
    restoredTheme?.let { preferences.setThemeMode(it) }

    db.withTransaction {
      if (parsedGoals.isNotEmpty()) {
        db.goalDao().clearAll()
        db.goalDao().upsertAll(parsedGoals)
      }
      if (parsedActivities.isNotEmpty()) {
        db.dailyActivityDao().clearAll()
        db.dailyActivityDao().upsertAll(parsedActivities)
      }
    }
    return RestoreResult(
      restoredActivitiesCount = parsedActivities.size,
      restoredGoalsCount = parsedGoals.size,
      profileRestored = restoredProfile != null,
    )
  }

  private fun escapeCsv(value: String): String {
    val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
    if (!needsQuotes) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
  }

  private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val ch = line[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < line.length && line[i + 1] == '"') {
            current.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          current.append(ch)
        }
      } else {
        when (ch) {
          ',' -> {
            result.add(current.toString())
            current.clear()
          }
          '"' -> inQuotes = true
          else -> current.append(ch)
        }
      }
      i++
    }
    result.add(current.toString())
    return result
  }

  data class RestoreResult(
    val restoredActivitiesCount: Int,
    val restoredGoalsCount: Int,
    val profileRestored: Boolean,
  )
}
