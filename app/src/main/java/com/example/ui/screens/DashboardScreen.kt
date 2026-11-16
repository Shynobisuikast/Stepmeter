package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DailyActivityEntity
import com.example.domain.FormatUtils.formatStepsTr
import com.example.domain.GoalMetric
import com.example.domain.GoalPeriod
import com.example.domain.GoalProgress
import com.example.domain.UserProfile
import com.example.ui.components.AnimatedStepRing
import com.example.ui.theme.LocalMetricPalette
import java.util.Locale

@Composable
fun DashboardScreen(
  todayActivity: DailyActivityEntity?,
  userProfile: UserProfile,
  goals: List<GoalProgress>,
  hasHardwareSensor: Boolean,
  isBackgroundTracking: Boolean,
  needsActivityPermission: Boolean,
  onRequestActivityPermission: () -> Unit,
  onToggleBackgroundTracking: (Boolean) -> Unit,
  onNavigateToShare: () -> Unit,
  onNavigateToGoals: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  val steps = todayActivity?.totalSteps ?: 0
  val distanceKm = todayActivity?.distanceKm ?: 0.0
  val caloriesKcal = todayActivity?.caloriesKcal ?: 0.0
  val activeMinutes = todayActivity?.activeMinutes ?: 0

  val primaryDailyGoal =
    goals.firstOrNull { it.period == GoalPeriod.DAILY && it.metric == GoalMetric.STEPS && it.isActive }
      ?.targetValue ?: 8000

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Minimal Header
    item {
      Row(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Merhaba, ${userProfile.displayName}",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = palette.stepsAccent.copy(alpha = 0.14f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Sensors,
              contentDescription = null,
              tint = palette.stepsAccent,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text =
                if (hasHardwareSensor && !needsActivityPermission)
                  stringResource(R.string.sensor_active_badge)
                else stringResource(R.string.sensor_pending_badge),
              style = MaterialTheme.typography.labelMedium,
              color = palette.stepsAccent,
            )
          }
        }
      }
    }

    if (needsActivityPermission && hasHardwareSensor) {
      item {
        Card(
          modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          shape = MaterialTheme.shapes.medium,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.sensor_permission_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
              onClick = onRequestActivityPermission,
              modifier = Modifier.heightIn(min = 48.dp).testTag("grant_activity_permission_button"),
            ) {
              Text(stringResource(R.string.sensor_permission_allow))
            }
          }
        }
      }
    }

    // Hero Step Ring
    item {
      Box(
        modifier =
          Modifier.widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
              Brush.verticalGradient(
                listOf(
                  MaterialTheme.colorScheme.surface,
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
              )
            )
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.large)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          AnimatedStepRing(steps = steps, targetSteps = primaryDailyGoal)
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = onNavigateToShare,
            modifier =
              Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_share_screen_button"),
          ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_share_progress))
          }
        }
      }
    }

    // Distance, Calories, Minutes Row
    item {
      Row(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        MetricBentoCard(
          title = stringResource(R.string.distance_label),
          value = "${String.format(Locale.US, "%.2f", distanceKm)} km",
          icon = Icons.Filled.Route,
          accent = palette.distanceAccent,
          modifier = Modifier.weight(1f).testTag("metric_distance_card"),
        )
        MetricBentoCard(
          title = stringResource(R.string.calories_label),
          value = "${String.format(Locale.US, "%.0f", caloriesKcal)} kcal",
          icon = Icons.Filled.LocalFireDepartment,
          accent = palette.caloriesAccent,
          modifier = Modifier.weight(1f).testTag("metric_calories_card"),
        )
        MetricBentoCard(
          title = stringResource(R.string.active_minutes_label),
          value = "$activeMinutes dk",
          icon = Icons.Filled.Schedule,
          accent = palette.goldAccent,
          modifier = Modifier.weight(1f).testTag("metric_minutes_card"),
        )
      }
    }

    // Compact Background Live Tracking Toggle
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 720.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (isBackgroundTracking) stringResource(R.string.bg_tracking_active)
              else stringResource(R.string.bg_tracking_inactive),
            style = MaterialTheme.typography.titleMedium,
            color = if (isBackgroundTracking) palette.stepsAccent else MaterialTheme.colorScheme.onSurface,
          )
          Switch(
            checked = isBackgroundTracking,
            onCheckedChange = onToggleBackgroundTracking,
            modifier = Modifier.testTag("background_tracking_switch"),
          )
        }
      }
    }

    // Compact Active Goals
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 720.dp)
            .fillMaxWidth()
            .border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Hedefler",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
              onClick = onNavigateToGoals,
              modifier = Modifier.heightIn(min = 48.dp).testTag("dashboard_manage_goals_button"),
            ) {
              Text("Düzenle")
            }
          }
          val activeGoals = goals.filter { it.isActive }.take(3)
          if (activeGoals.isEmpty()) {
            Text(
              text = "Aktif hedef yok.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              activeGoals.forEach { goal ->
                val accent =
                  if (goal.metric == GoalMetric.STEPS) palette.stepsAccent else palette.caloriesAccent
                Column {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "${goal.period.labelTr} ${goal.metric.labelTr}",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text = "${formatStepsTr(goal.currentValue)} / ${formatStepsTr(goal.targetValue)}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = accent,
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
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
          }
        }
      }
    }
  }
}

@Composable
private fun MetricBentoCard(
  title: String,
  value: String,
  icon: ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  Card(
    modifier = modifier.border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(16.dp))
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
