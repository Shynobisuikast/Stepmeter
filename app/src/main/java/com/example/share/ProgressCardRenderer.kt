package com.example.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.FormatUtils
import com.example.domain.ShareCardTheme
import com.example.domain.SharePeriod
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a crisp high-resolution (1080x1350) progress summary graphic card with mini bar chart,
 * goal badge, distance, and calories, then exposes a FileProvider URI for Android Share Sheet.
 */
object ProgressCardRenderer {
  data class ShareCardData(
    val period: SharePeriod,
    val theme: ShareCardTheme,
    val dateRangeLabel: String,
    val totalSteps: Int,
    val distanceKm: Double,
    val caloriesKcal: Double,
    val goalBadgeText: String,
    val dailyBars: List<Pair<String, Int>>,
  )

  @Volatile private var cachedOutfitTypeface: Typeface? = null
  @Volatile private var cachedJakartaTypeface: Typeface? = null

  private fun getOutfitTypeface(context: Context): Typeface {
    return cachedOutfitTypeface
      ?: (runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull()
          ?: Typeface.DEFAULT_BOLD)
        .also { cachedOutfitTypeface = it }
  }

  private fun getJakartaTypeface(context: Context): Typeface {
    return cachedJakartaTypeface
      ?: (runCatching { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) }.getOrNull()
          ?: Typeface.DEFAULT)
        .also { cachedJakartaTypeface = it }
  }

  suspend fun renderAndGetUri(context: Context, data: ShareCardData): Uri =
    withContext(Dispatchers.Default) {
      val width = 1080
      val height = 1350
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)

      val (bgStart, bgEnd, accentColor) =
        when (data.theme) {
          ShareCardTheme.MIDNIGHT_MINT -> Triple(0xFF0B121B.toInt(), 0xFF132A27.toInt(), 0xFF2CE69B.toInt())
          ShareCardTheme.CORAL_PULSE -> Triple(0xFF180D14.toInt(), 0xFF331819.toInt(), 0xFFFF6B4A.toInt())
          ShareCardTheme.SKY_HORIZON -> Triple(0xFF0A1424.toInt(), 0xFF102A4A.toInt(), 0xFF4DA6FF.toInt())
        }

      val outfitTypeface = getOutfitTypeface(context)
      val jakartaTypeface = getJakartaTypeface(context)

      // Background gradient
      val bgPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader =
            LinearGradient(
              0f,
              0f,
              width.toFloat(),
              height.toFloat(),
              bgStart,
              bgEnd,
              Shader.TileMode.CLAMP,
            )
        }
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

      // Subtle glow circle
      val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          alpha = 28
        }
      canvas.drawCircle(width * 0.84f, height * 0.16f, 260f, glowPaint)

      // Outer border card
      val cardPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0x22FFFFFF
          style = Paint.Style.STROKE
          strokeWidth = 3f
        }
      canvas.drawRoundRect(RectF(48f, 48f, width - 48f, height - 48f), 56f, 56f, cardPaint)

      // Header brand
      val brandPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          textSize = 44f
          typeface = Typeface.create(outfitTypeface, Typeface.BOLD)
        }
      canvas.drawText("Stepmeter", 96f, 148f, brandPaint)

      val periodPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFB8C7D9.toInt()
          textSize = 34f
          typeface = jakartaTypeface
        }
      canvas.drawText("${data.period.labelTr} · ${data.dateRangeLabel}", 96f, 208f, periodPaint)

      // Hero steps
      val heroNumberPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFFFFFFF.toInt()
          textSize = 156f
          typeface = Typeface.create(outfitTypeface, Typeface.BOLD)
        }
      canvas.drawText(FormatUtils.formatStepsTr(data.totalSteps), 96f, 410f, heroNumberPaint)

      val heroUnitPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          textSize = 40f
          typeface = Typeface.create(jakartaTypeface, Typeface.BOLD)
        }
      canvas.drawText("TOPLAM ADIM", 100f, 468f, heroUnitPaint)

      // Goal badge pill
      val pillRect = RectF(96f, 520f, 984f, 616f)
      val pillBg =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = accentColor
          alpha = 42
        }
      canvas.drawRoundRect(pillRect, 48f, 48f, pillBg)
      val pillTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFFFFFFF.toInt()
          textSize = 32f
          typeface = Typeface.create(jakartaTypeface, Typeface.BOLD)
        }
      canvas.drawText("★  ${data.goalBadgeText}", 132f, 580f, pillTextPaint)

      // Distance & Calories boxes
      val boxPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0x16FFFFFF
        }
      canvas.drawRoundRect(RectF(96f, 668f, 516f, 868f), 36f, 36f, boxPaint)
      canvas.drawRoundRect(RectF(564f, 668f, 984f, 868f), 36f, 36f, boxPaint)

      val metricLabelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFAAB8C8.toInt()
          textSize = 30f
          typeface = jakartaTypeface
        }
      val metricValuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFFFFFFF.toInt()
          textSize = 58f
          typeface = Typeface.create(outfitTypeface, Typeface.BOLD)
        }
      canvas.drawText("MESAFE", 132f, 730f, metricLabelPaint)
      canvas.drawText(String.format(Locale.US, "%.2f km", data.distanceKm), 132f, 816f, metricValuePaint)

      canvas.drawText("KALORİ", 600f, 730f, metricLabelPaint)
      canvas.drawText(String.format(Locale.US, "%.0f kcal", data.caloriesKcal), 600f, 816f, metricValuePaint)

      // Mini bar chart
      val chartBox = RectF(96f, 912f, 984f, 1200f)
      canvas.drawRoundRect(chartBox, 36f, 36f, boxPaint)
      val bars = data.dailyBars.ifEmpty { listOf("Bugün" to data.totalSteps) }
      val maxVal = (bars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
      val barAreaWidth = chartBox.width() - 96f
      val slotWidth = barAreaWidth / bars.size.coerceAtLeast(1)
      val barWidth = (slotWidth * 0.52f).coerceAtMost(72f)
      val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
      val barLabelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFFC0CEDE.toInt()
          textSize = 24f
          textAlign = Paint.Align.CENTER
          typeface = jakartaTypeface
        }
      bars.forEachIndexed { idx, (label, value) ->
        val cx = chartBox.left + 48f + slotWidth * idx + slotWidth / 2f
        val barMaxH = 175f
        val h = ((value.toFloat() / maxVal) * barMaxH).coerceAtLeast(12f)
        val top = chartBox.bottom - 64f - h
        val bottom = chartBox.bottom - 64f
        canvas.drawRoundRect(RectF(cx - barWidth / 2f, top, cx + barWidth / 2f, bottom), 14f, 14f, barPaint)
        canvas.drawText(label, cx, chartBox.bottom - 26f, barLabelPaint)
      }

      // Footer
      val footerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xFF8C9BAE.toInt()
          textSize = 26f
          typeface = jakartaTypeface
        }
      canvas.drawText("Stepmeter · Yerel ve Gizli Adımsayar", 96f, 1264f, footerPaint)

      val dir = File(context.cacheDir, "shared_cards").apply { if (!exists()) mkdirs() }
      val outFile = File(dir, "stepmeter_ozet.png")
      FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 96, fos)
        fos.flush()
      }
      bitmap.recycle()
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

  fun launchShareIntent(
    context: Context,
    imageUri: Uri,
    data: ShareCardData,
    asNewTask: Boolean = false,
  ) {
    val shareText =
      buildString {
        append("Stepmeter ${data.period.labelTr} (${data.dateRangeLabel}): ")
        append("${FormatUtils.formatStepsTr(data.totalSteps)} adım · ")
        append(String.format(Locale.US, "%.2f km · ", data.distanceKm))
        append(String.format(Locale.US, "%.0f kcal! ", data.caloriesKcal))
        append(data.goalBadgeText)
      }
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(intent, "İlerlemeyi Paylaş").apply {
        if (asNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooser)
  }
}
