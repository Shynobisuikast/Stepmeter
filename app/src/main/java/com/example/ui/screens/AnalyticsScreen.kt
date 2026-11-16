package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AnalyticsMetric
import com.example.domain.DailyChartPoint
import com.example.domain.DateUtils
import com.example.domain.FormatUtils.formatStepsTr
import com.example.ui.components.MonthlyProgressChart
import com.example.ui.theme.LocalMetricPalette
import java.util.Locale

private enum class AnalyticsWindow(val labelTr: String) {
  LAST_30_DAYS("Son 30 Gün"),
  MONTHLY("Aylık"),
}

@Composable
fun AnalyticsScreen(
  selectedYearMonth: Pair<Int, Int>,
  selectedMetric: AnalyticsMetric,
  last30DaysPoints: List<DailyChartPoint>,
  monthlyPoints: List<DailyChartPoint>,
  onShiftMonth: (Int) -> Unit,
  onSelectMetric: (AnalyticsMetric) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  val (year, month) = selectedYearMonth
  var windowMode by remember { mutableStateOf(AnalyticsWindow.LAST_30_DAYS) }
  val activePoints = if (windowMode == AnalyticsWindow.LAST_30_DAYS) last30DaysPoints else monthlyPoints
  var selectedDayIndex by remember(windowMode, selectedYearMonth) { mutableStateOf<Int?>(null) }

  val totalSteps = remember(activePoints) { activePoints.sumOf { it.steps } }
  val totalDistance = remember(activePoints) { activePoints.sumOf { it.distanceKm } }
  val totalCalories = remember(activePoints) { activePoints.sumOf { it.caloriesKcal } }
  val activeDays = remember(activePoints) { activePoints.count { it.steps > 0 } }
  val avgSteps = if (activeDays == 0) 0 else totalSteps / activeDays

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
        Text(
          text = stringResource(R.string.analytics_title),
          style = MaterialTheme.typography.headlineLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AnalyticsWindow.entries.forEach { w ->
            FilterChip(
              selected = windowMode == w,
              onClick = { windowMode = w },
              label = { Text(w.labelTr) },
              modifier = Modifier.heightIn(min = 48.dp).testTag("analytics_window_${w.name.lowercase()}"),
            )
          }
        }

        if (windowMode == AnalyticsWindow.MONTHLY) {
          Spacer(modifier = Modifier.height(8.dp))
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier =
              Modifier.fillMaxWidth().border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = { onShiftMonth(-1) },
                modifier = Modifier.size(48.dp).testTag("analytics_prev_month_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki ay")
              }
              Text(
                text = DateUtils.formatMonthYearTr(year, month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("analytics_month_label"),
              )
              IconButton(
                onClick = { onShiftMonth(1) },
                modifier = Modifier.size(48.dp).testTag("analytics_next_month_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki ay")
              }
            }
          }
        }
      }
    }

    // Metric switcher
    item {
      Row(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AnalyticsMetric.entries.forEach { metric ->
          FilterChip(
            selected = selectedMetric == metric,
            onClick = { onSelectMetric(metric) },
            label = { Text(metric.labelTr) },
            modifier =
              Modifier.heightIn(min = 48.dp).testTag("analytics_metric_${metric.name.lowercase()}"),
          )
        }
      }
    }

    // Interactive 30-Day / Monthly Chart
    item {
      Card(
        modifier =
          Modifier.widthIn(max = 760.dp)
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
              text =
                if (windowMode == AnalyticsWindow.LAST_30_DAYS) "Son 30 Gün"
                else DateUtils.formatMonthYearTr(year, month),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "$activeDays aktif gün",
              style = MaterialTheme.typography.labelMedium,
              color = palette.stepsAccent,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          val selectedPoint = selectedDayIndex?.let { activePoints.getOrNull(it) }
          if (selectedPoint != null) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
              Text(
                text =
                  "${DateUtils.formatShortDateTr(selectedPoint.epochDay)}: " +
                    "${formatStepsTr(selectedPoint.steps)} adım · " +
                    String.format(Locale.US, "%.2f km · %.0f kcal", selectedPoint.distanceKm, selectedPoint.caloriesKcal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
            }
          }

          if (totalSteps == 0) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp),
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = stringResource(R.string.analytics_empty_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
          } else {
            MonthlyProgressChart(
              points = activePoints,
              metric = selectedMetric,
              selectedIndex = selectedDayIndex,
              onSelectIndex = { idx -> selectedDayIndex = idx },
              modifier = Modifier.testTag("analytics_monthly_chart"),
            )
          }
        }
      }
    }

    // Concise KPI Summary
    item {
      Row(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SummaryStatCard(
          label = "Toplam",
          primary = "${formatStepsTr(totalSteps)} adım",
          secondary = String.format(Locale.US, "%.1f km · %.0f kcal", totalDistance, totalCalories),
          modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
          label = "Günlük Ortalama",
          primary = "${formatStepsTr(avgSteps)} adım",
          secondary = "$activeDays aktif gün",
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun SummaryStatCard(
  label: String,
  primary: String,
  secondary: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  Card(
    modifier = modifier.border(1.dp, palette.subtleBorder, MaterialTheme.shapes.medium),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = primary,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = secondary,
        style = MaterialTheme.typography.bodyMedium,
        color = palette.stepsAccent,
      )
    }
  }
}
