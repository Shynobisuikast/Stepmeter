package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NotificationLogEntity
import com.example.domain.FormatUtils.formatStepsTr
import com.example.domain.GoalMetric
import com.example.domain.GoalPeriod
import com.example.domain.GoalProgress
import com.example.ui.theme.LocalMetricPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoalsScreen(
  goals: List<GoalProgress>,
  notifications: List<NotificationLogEntity>,
  onSaveGoal: (GoalPeriod, GoalMetric, Int) -> Unit,
  onToggleGoal: (Long, Boolean) -> Unit,
  onDeleteGoal: (Long) -> Unit,
  onMarkAllRead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  var showAddDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Row(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.goals_title),
          style = MaterialTheme.typography.headlineLarge,
        )
        Button(
          onClick = { showAddDialog = true },
          modifier = Modifier.heightIn(min = 48.dp).testTag("add_goal_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.goals_add_button))
        }
      }
    }

    items(goals, key = { it.id }) { goal ->
      val accent =
        when {
          goal.isCompleted -> palette.goldAccent
          goal.metric == GoalMetric.STEPS -> palette.stepsAccent
          else -> palette.caloriesAccent
        }
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium)
            .testTag("goal_card_${goal.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              if (goal.isCompleted) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Hedef tamamlandı",
                  tint = palette.goldAccent,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
              }
              Column {
                Text(
                  text = "${goal.period.labelTr} ${goal.metric.labelTr}",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text =
                    "${formatStepsTr(goal.currentValue)} / ${formatStepsTr(goal.targetValue)} ${goal.metric.unitTr}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = accent,
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Switch(
                checked = goal.isActive,
                onCheckedChange = { onToggleGoal(goal.id, it) },
                modifier = Modifier.testTag("goal_switch_${goal.id}"),
              )
              IconButton(
                onClick = { onDeleteGoal(goal.id) },
                modifier = Modifier.size(48.dp).testTag("goal_delete_${goal.id}"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "Hedefi sil",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { goal.fraction },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
          )
        }
      }
    }

    // Notifications Section
    item {
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.notifications_section_title),
          style = MaterialTheme.typography.titleLarge,
        )
        if (notifications.any { !it.isRead }) {
          TextButton(
            onClick = onMarkAllRead,
            modifier = Modifier.heightIn(min = 48.dp).testTag("mark_notifications_read_button"),
          ) {
            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.notifications_mark_all_read))
          }
        }
      }
    }

    if (notifications.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.notifications_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(vertical = 8.dp),
        )
      }
    } else {
      items(notifications, key = { "notif_${it.id}" }) { notif ->
        val dateText =
          remember(notif.createdAt) {
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(notif.createdAt))
          }
        Card(
          modifier =
            Modifier.widthIn(max = 760.dp)
              .fillMaxWidth()
              .border(
                width = if (notif.isRead) 1.dp else 1.5.dp,
                color = if (notif.isRead) palette.subtleBorder else palette.goldAccent,
                shape = MaterialTheme.shapes.medium,
              ),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium,
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = null,
              tint = palette.goldAccent,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = notif.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
                if (!notif.isRead) {
                  Box(
                    modifier =
                      Modifier.size(8.dp)
                        .clip(CircleShape)
                        .border(4.dp, palette.goldAccent, CircleShape)
                  )
                }
              }
              Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    AddGoalDialog(
      onDismiss = { showAddDialog = false },
      onSave = { period, metric, target ->
        onSaveGoal(period, metric, target)
        showAddDialog = false
      },
    )
  }
}

@Composable
private fun AddGoalDialog(
  onDismiss: () -> Unit,
  onSave: (GoalPeriod, GoalMetric, Int) -> Unit,
) {
  var period by remember { mutableStateOf(GoalPeriod.DAILY) }
  var metric by remember { mutableStateOf(GoalMetric.STEPS) }
  var targetInput by remember { mutableStateOf("10000") }
  val targetVal = targetInput.toIntOrNull() ?: 0

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Hedef Ekle") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GoalPeriod.entries.forEach { p ->
            FilterChip(
              selected = period == p,
              onClick = { period = p },
              label = { Text(p.labelTr) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("goal_period_${p.name.lowercase()}"),
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GoalMetric.entries.forEach { m ->
            FilterChip(
              selected = metric == m,
              onClick = { metric = m },
              label = { Text(m.labelTr) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("goal_metric_${m.name.lowercase()}"),
            )
          }
        }
        OutlinedTextField(
          value = targetInput,
          onValueChange = { input -> targetInput = input.filter { it.isDigit() }.take(7) },
          label = { Text("Hedef (${metric.unitTr})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("goal_target_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { if (targetVal >= 50) onSave(period, metric, targetVal) },
        enabled = targetVal >= 50,
        modifier = Modifier.heightIn(min = 48.dp).testTag("save_goal_confirm_button"),
      ) {
        Text("Kaydet")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
