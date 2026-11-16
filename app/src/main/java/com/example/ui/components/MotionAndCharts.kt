package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AnalyticsMetric
import com.example.domain.DailyChartPoint
import com.example.domain.FormatUtils.formatStepsTr
import com.example.domain.InAppGoalAlert
import com.example.ui.theme.LocalMetricPalette

@Composable
fun AnimatedStepRing(
  steps: Int,
  targetSteps: Int,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  val rawFraction =
    if (targetSteps <= 0) 0f else (steps.toFloat() / targetSteps.toFloat()).coerceIn(0f, 1f)
  val animatedSweep by
    animateFloatAsState(
      targetValue = rawFraction * 270f,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
      label = "step_ring_sweep",
    )
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val accentStart = palette.stepsAccent
  val accentEnd = palette.distanceAccent

  Box(modifier = modifier.size(236.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
      val strokePx = 18.dp.toPx()
      val inset = strokePx / 2f
      val arcSize = Size(size.width - strokePx, size.height - strokePx)
      val topLeft = Offset(inset, inset)

      // Background horseshoe track
      drawArc(
        color = trackColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )

      if (animatedSweep > 0.5f) {
        drawArc(
          brush = Brush.sweepGradient(listOf(accentStart, accentEnd, accentStart)),
          startAngle = 135f,
          sweepAngle = animatedSweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "BUGÜN",
        style = MaterialTheme.typography.labelMedium,
        color = palette.stepsAccent,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = formatStepsTr(steps),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("dashboard_step_count"),
      )
      Spacer(modifier = Modifier.height(4.dp))
      val pct = if (targetSteps <= 0) 0 else ((steps.toFloat() / targetSteps) * 100f).toInt()
      Text(
        text = "Hedef: ${formatStepsTr(targetSteps)} · %$pct",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun MonthlyProgressChart(
  points: List<DailyChartPoint>,
  metric: AnalyticsMetric,
  selectedIndex: Int?,
  onSelectIndex: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  val barColor =
    when (metric) {
      AnalyticsMetric.STEPS -> palette.stepsAccent
      AnalyticsMetric.DISTANCE -> palette.distanceAccent
      AnalyticsMetric.CALORIES -> palette.caloriesAccent
    }
  val mutedBarColor = barColor.copy(alpha = 0.35f)
  val trendLineColor = palette.goldAccent
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

  val values =
    remember(points, metric) {
      points.map { p ->
        when (metric) {
          AnalyticsMetric.STEPS -> p.steps.toFloat()
          AnalyticsMetric.DISTANCE -> p.distanceKm.toFloat()
          AnalyticsMetric.CALORIES -> p.caloriesKcal.toFloat()
        }
      }
    }
  val maxVal = remember(values) { (values.maxOrNull() ?: 0f).coerceAtLeast(1f) }
  val avgVal = remember(values) { if (values.isEmpty()) 0f else values.average().toFloat() }
  val linePath = remember { Path() }
  val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(
      modifier =
        Modifier.fillMaxWidth()
          .height(210.dp)
          .pointerInput(points) {
            detectTapGestures { offset ->
              if (points.isNotEmpty()) {
                val idx =
                  ((offset.x / size.width.coerceAtLeast(1)) * points.size)
                    .toInt()
                    .coerceIn(0, points.lastIndex)
                onSelectIndex(idx)
              }
            }
          }
    ) {
      if (points.isEmpty()) return@Canvas
      val chartHeight = size.height - 20.dp.toPx()
      val slotWidth = size.width / points.size.toFloat()
      val barWidth = (slotWidth * 0.56f).coerceIn(4.dp.toPx(), 18.dp.toPx())

      // Horizontal guide lines
      for (step in 1..3) {
        val y = chartHeight * (1f - step / 3f)
        drawLine(
          color = gridColor,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1.dp.toPx(),
        )
      }

      // Average reference line
      if (avgVal > 0f) {
        val avgY = chartHeight - ((avgVal / maxVal).coerceIn(0f, 1f) * (chartHeight - 8.dp.toPx()))
        drawLine(
          color = barColor.copy(alpha = 0.55f),
          start = Offset(0f, avgY),
          end = Offset(size.width, avgY),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = dashEffect,
        )
      }

      linePath.reset()
      var firstLinePoint = true

      points.forEachIndexed { index, _ ->
        val valNorm = (values[index] / maxVal).coerceIn(0f, 1f)
        val barHeight = (valNorm * (chartHeight - 8.dp.toPx())).coerceAtLeast(if (values[index] > 0f) 4.dp.toPx() else 2.dp.toPx())
        val cx = index * slotWidth + slotWidth / 2f
        val top = chartHeight - barHeight
        val isSelected = selectedIndex == index

        drawRoundRect(
          color = if (isSelected || selectedIndex == null) barColor else mutedBarColor,
          topLeft = Offset(cx - barWidth / 2f, top),
          size = Size(barWidth, barHeight),
          cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        val point = Offset(cx, top)
        if (firstLinePoint) {
          linePath.moveTo(point.x, point.y)
          firstLinePoint = false
        } else {
          linePath.lineTo(point.x, point.y)
        }
      }

      drawPath(
        path = linePath,
        color = trendLineColor.copy(alpha = 0.85f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
      )

      selectedIndex?.let { idx ->
        if (idx in points.indices) {
          val valNorm = (values[idx] / maxVal).coerceIn(0f, 1f)
          val barHeight = (valNorm * (chartHeight - 8.dp.toPx())).coerceAtLeast(2.dp.toPx())
          val cx = idx * slotWidth + slotWidth / 2f
          val cy = chartHeight - barHeight
          drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
          drawCircle(color = barColor, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val firstLabel = points.firstOrNull()?.label ?: "1"
      val midLabel = points.getOrNull(points.size / 2)?.label ?: "15"
      val lastLabel = points.lastOrNull()?.label ?: "30"
      Text(
        text = firstLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = midLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = lastLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun InAppGoalBanner(
  alert: InAppGoalAlert?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMetricPalette.current
  AnimatedVisibility(
    visible = alert != null,
    enter = slideInVertically(animationSpec = spring(dampingRatio = 0.75f)) { -it } + fadeIn(),
    exit = slideOutVertically { -it } + fadeOut(),
    modifier = modifier,
  ) {
    if (alert != null) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.5.dp, palette.goldAccent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .testTag("in_app_goal_banner"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(CircleShape)
                .background(palette.goldAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = "Hedef tamamlandı",
              tint = palette.goldAccent,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = alert.title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = alert.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("dismiss_goal_banner_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Bildirimi kapat",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
