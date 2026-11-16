package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DecryptedFeedbackItem
import com.example.domain.FeedbackCategory
import com.example.domain.LocalBackupInfo
import com.example.domain.ThemeMode
import com.example.domain.UserProfile
import com.example.ui.theme.LocalMetricPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsScreen(
  themeMode: ThemeMode,
  userProfile: UserProfile,
  localBackups: List<LocalBackupInfo>,
  feedbacks: List<DecryptedFeedbackItem>,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSaveProfile: (UserProfile) -> Unit,
  onCreateLocalBackup: (String?) -> Unit,
  onExportBackupToUri: (Uri, String?) -> Unit,
  onRestoreLocalBackup: (String, String?) -> Unit,
  onRestoreUri: (Uri, String?) -> Unit,
  onDeleteLocalBackup: (String) -> Unit,
  onSubmitFeedback: (FeedbackCategory, Int, String) -> Unit,
  onDeleteFeedback: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  val context = LocalContext.current

  var nameInput by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
  var heightInput by remember(userProfile.heightCm) { mutableStateOf(userProfile.heightCm.toString()) }
  var weightInput by remember(userProfile.weightKg) { mutableStateOf(userProfile.weightKg.toInt().toString()) }
  var useCustomStride by remember(userProfile.useCustomStride) { mutableStateOf(userProfile.useCustomStride) }
  var customStrideInput by remember(userProfile.customStrideCm) { mutableStateOf(userProfile.customStrideCm.toString()) }

  var backupPassword by remember { mutableStateOf("") }
  var pendingRestoreFile by remember { mutableStateOf<String?>(null) }
  var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
  var restorePasswordPrompt by remember { mutableStateOf("") }

  var feedbackCategory by remember { mutableStateOf(FeedbackCategory.SUGGESTION) }
  var feedbackRating by remember { mutableIntStateOf(5) }
  var feedbackMessage by remember { mutableStateOf("") }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
      if (uri != null) {
        onExportBackupToUri(uri, backupPassword.takeIf { it.isNotBlank() })
      }
    }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        pendingRestoreUri = uri
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Text(
        text = stringResource(R.string.backup_title),
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
      )
    }

    // Theme Mode
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.theme_mode_title), style = MaterialTheme.typography.titleMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = themeMode == mode,
                onClick = { onSetThemeMode(mode) },
                label = { Text(mode.labelTr) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("theme_mode_${mode.name.lowercase()}"),
              )
            }
          }
        }
      }
    }

    // Profile
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it.take(28) },
            label = { Text("İsim") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = heightInput,
              onValueChange = { heightInput = it.filter(Char::isDigit).take(3) },
              label = { Text("Boy (cm)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("profile_height_input"),
            )
            OutlinedTextField(
              value = weightInput,
              onValueChange = { weightInput = it.filter(Char::isDigit).take(3) },
              label = { Text("Kilo (kg)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("profile_weight_input"),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Özel adım uzunluğu", style = MaterialTheme.typography.bodyLarge)
            Switch(
              checked = useCustomStride,
              onCheckedChange = { useCustomStride = it },
              modifier = Modifier.testTag("profile_custom_stride_switch"),
            )
          }
          if (useCustomStride) {
            OutlinedTextField(
              value = customStrideInput,
              onValueChange = { customStrideInput = it.filter(Char::isDigit).take(3) },
              label = { Text("Adım uzunluğu (cm)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("profile_stride_cm_input"),
            )
          }
          Button(
            onClick = {
              onSaveProfile(
                UserProfile(
                  displayName = nameInput.ifBlank { "Yürüyüşçü" },
                  heightCm = heightInput.toIntOrNull() ?: 172,
                  weightKg = (weightInput.toIntOrNull() ?: 72).toFloat(),
                  customStrideCm = customStrideInput.toIntOrNull() ?: 72,
                  useCustomStride = useCustomStride,
                )
              )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_profile_button"),
          ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kaydet")
          }
        }
      }
    }

    // Encrypted Local CSV Backup inside Ayarlar
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(R.string.encrypted_backup_title), style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
            value = backupPassword,
            onValueChange = { backupPassword = it.take(40) },
            label = { Text("Parola (isteğe bağlı)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("backup_password_input"),
          )
          Button(
            onClick = { onCreateLocalBackup(backupPassword.takeIf { it.isNotBlank() }) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create_local_backup_button"),
          ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Şifreli CSV Yedek Al")
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                exportLauncher.launch("stepmeter_yedek_$stamp.csv.enc")
              },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("export_saf_backup_button"),
            ) {
              Text("Dışa Aktar")
            }
            OutlinedButton(
              onClick = { importLauncher.launch(arrayOf("*/*")) },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_saf_backup_button"),
            ) {
              Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Geri Yükle")
            }
          }
        }
      }
    }

    items(localBackups, key = { "backup_${it.fileName}" }) { backup ->
      val dateStr =
        remember(backup.createdAtMillis) {
          SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(backup.createdAtMillis))
        }
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = backup.fileName,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "$dateStr · ${if (backup.isPasswordProtected) "Parolalı" else "Keystore"}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = {
              if (backup.isPasswordProtected) {
                pendingRestoreFile = backup.fileName
              } else {
                onRestoreLocalBackup(backup.fileName, null)
              }
            },
            modifier = Modifier.size(48.dp).testTag("restore_backup_${backup.fileName}"),
          ) {
            Icon(Icons.Filled.Restore, contentDescription = "Yedekten geri yükle", tint = palette.stepsAccent)
          }
          IconButton(
            onClick = { onDeleteLocalBackup(backup.fileName) },
            modifier = Modifier.size(48.dp).testTag("delete_backup_${backup.fileName}"),
          ) {
            Icon(Icons.Filled.Delete, contentDescription = "Yedeği sil")
          }
        }
      }
    }

    // Compact Feedback
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FeedbackCategory.entries.forEach { cat ->
              FilterChip(
                selected = feedbackCategory == cat,
                onClick = { feedbackCategory = cat },
                label = { Text(cat.labelTr) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("feedback_cat_${cat.name.lowercase()}"),
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
              IconButton(
                onClick = { feedbackRating = star },
                modifier = Modifier.size(48.dp).testTag("feedback_star_$star"),
              ) {
                Icon(
                  imageVector =
                    if (star <= feedbackRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                  contentDescription = "$star yıldız",
                  tint = palette.goldAccent,
                )
              }
            }
          }
          OutlinedTextField(
            value = feedbackMessage,
            onValueChange = { feedbackMessage = it.take(400) },
            label = { Text("Mesajınız") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("feedback_message_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = {
                if (feedbackMessage.isNotBlank()) {
                  onSubmitFeedback(feedbackCategory, feedbackRating, feedbackMessage)
                  feedbackMessage = ""
                }
              },
              enabled = feedbackMessage.isNotBlank(),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("submit_feedback_button"),
            ) {
              Text("Kaydet")
            }
            OutlinedButton(
              onClick = {
                val subject = Uri.encode("Stepmeter Geri Bildirim (${feedbackCategory.labelTr} · $feedbackRating/5)")
                val body = Uri.encode(feedbackMessage)
                val emailIntent =
                  Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:?subject=$subject&body=$body")
                  }
                runCatching { context.startActivity(emailIntent) }
              },
              enabled = feedbackMessage.isNotBlank(),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("email_feedback_button"),
            ) {
              Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("E-posta")
            }
          }
        }
      }
    }

    items(feedbacks, key = { "fb_${it.id}" }) { fb ->
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${fb.category.labelTr} · ${fb.rating}/5 ★",
              style = MaterialTheme.typography.labelMedium,
              color = palette.goldAccent,
            )
            Text(text = fb.message, style = MaterialTheme.typography.bodyMedium)
          }
          IconButton(
            onClick = { onDeleteFeedback(fb.id) },
            modifier = Modifier.size(48.dp).testTag("delete_feedback_${fb.id}"),
          ) {
            Icon(Icons.Filled.Delete, contentDescription = "Geri bildirimi sil")
          }
        }
      }
    }
  }

  if (pendingRestoreFile != null || pendingRestoreUri != null) {
    AlertDialog(
      onDismissRequest = {
        pendingRestoreFile = null
        pendingRestoreUri = null
        restorePasswordPrompt = ""
      },
      title = { Text("Yedeği Çöz") },
      text = {
        OutlinedTextField(
          value = restorePasswordPrompt,
          onValueChange = { restorePasswordPrompt = it },
          label = { Text("Parola (Keystore ise boş bırakın)") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("restore_password_dialog_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val pw = restorePasswordPrompt.takeIf { it.isNotBlank() }
            pendingRestoreFile?.let { onRestoreLocalBackup(it, pw) }
            pendingRestoreUri?.let { onRestoreUri(it, pw) }
            pendingRestoreFile = null
            pendingRestoreUri = null
            restorePasswordPrompt = ""
          },
          modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_restore_password_button"),
        ) {
          Text("Geri Yükle")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pendingRestoreFile = null
            pendingRestoreUri = null
            restorePasswordPrompt = ""
          },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}
