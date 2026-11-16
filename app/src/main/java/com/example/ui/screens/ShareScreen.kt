package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.FormatUtils.formatStepsTr
import com.example.domain.ShareCardTheme
import com.example.domain.SharePeriod
import com.example.share.ProgressCardRenderer
import java.util.Locale

@Composable
fun ShareScreen(
  buildCardData: (SharePeriod, ShareCardTheme) -> ProgressCardRenderer.ShareCardData,
  onShareCard: (ProgressCardRenderer.ShareCardData) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onBack() }

  var selectedPeriod by remember { mutableStateOf(SharePeriod.DAILY) }
  var selectedTheme by remember { mutableStateOf(ShareCardTheme.MIDNIGHT_MINT) }
  val cardData = remember(selectedPeriod, selectedTheme) { buildCardData(selectedPeriod, selectedTheme) }

  val (bgColors, accent) =
    when (selectedTheme) {
      ShareCardTheme.MIDNIGHT_MINT ->
        listOf(Color(0xFF0B121B), Color(0xFF132A27)) to Color(0xFF2CE69B)
      ShareCardTheme.CORAL_PULSE ->
        listOf(Color(0xFF180D14), Color(0xFF331819)) to Color(0xFFFF6B4A)
      ShareCardTheme.SKY_HORIZON ->
        listOf(Color(0xFF0A1424), Color(0xFF102A4A)) to Color(0xFF4DA6FF)
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      Row(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("share_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri dön")
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.share_screen_title),
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }

    item {
      Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Text("Paylaşım Aralığı", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SharePeriod.entries.forEach { period ->
            FilterChip(
              selected = selectedPeriod == period,
              onClick = { selectedPeriod = period },
              label = { Text(period.labelTr) },
              modifier =
                Modifier.heightIn(min = 48.dp).testTag("share_period_${period.name.lowercase()}"),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Kart Teması", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ShareCardTheme.entries.forEach { theme ->
            FilterChip(
              selected = selectedTheme == theme,
              onClick = { selectedTheme = theme },
              label = { Text(theme.labelTr) },
              modifier =
                Modifier.heightIn(min = 48.dp).testTag("share_theme_${theme.name.lowercase()}"),
            )
          }
        }
      }
    }

    // Live Visual Preview Card
    item {
      Box(
        modifier =
          Modifier.widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(bgColors))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
            .padding(22.dp)
            .testTag("share_preview_card"),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Stepmeter",
              style = MaterialTheme.typography.titleLarge,
              color = accent,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${cardData.period.labelTr} · ${cardData.dateRangeLabel}",
              style = MaterialTheme.typography.labelMedium,
              color = Color(0xFFB8C7D9),
            )
          }

          Column {
            Text(
              text = formatStepsTr(cardData.totalSteps),
              style = MaterialTheme.typography.displayLarge,
              color = Color.White,
            )
            Text(
              text = "TOPLAM ADIM",
              style = MaterialTheme.typography.labelLarge,
              color = accent,
            )
          }

          Surface(
            shape = RoundedCornerShape(999.dp),
            color = accent.copy(alpha = 0.18f),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = cardData.goalBadgeText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
              modifier =
                Modifier.weight(1f)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.08f))
                  .padding(14.dp)
            ) {
              Column {
                Text("MESAFE", style = MaterialTheme.typography.labelMedium, color = Color(0xFFAAB8C8))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = String.format(Locale.US, "%.2f km", cardData.distanceKm),
                  style = MaterialTheme.typography.titleLarge,
                  color = Color.White,
                )
              }
            }
            Box(
              modifier =
                Modifier.weight(1f)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.08f))
                  .padding(14.dp)
            ) {
              Column {
                Text("KALORİ", style = MaterialTheme.typography.labelMedium, color = Color(0xFFAAB8C8))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = String.format(Locale.US, "%.0f kcal", cardData.caloriesKcal),
                  style = MaterialTheme.typography.titleLarge,
                  color = Color.White,
                )
              }
            }
          }

          // Mini bar strip
          val bars = cardData.dailyBars
          val maxBar = (bars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 12.dp, vertical = 14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().height(74.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.Bottom,
            ) {
              bars.forEach { (label, value) ->
                val heightFraction = (value.toFloat() / maxBar).coerceIn(0.08f, 1f)
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Bottom,
                  modifier = Modifier.weight(1f),
                ) {
                  Box(
                    modifier =
                      Modifier.width(16.dp)
                        .height((48f * heightFraction).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent)
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFC0CEDE),
                  )
                }
              }
            }
          }

          Text(
            text = stringResource(R.string.share_card_footer),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8C9BAE),
          )
        }
      }
    }

    item {
      Button(
        onClick = { onShareCard(cardData) },
        modifier =
          Modifier.widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("share_now_button"),
      ) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.share_action_now))
      }
    }
  }
}
