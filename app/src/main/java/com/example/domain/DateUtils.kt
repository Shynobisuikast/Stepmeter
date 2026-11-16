package com.example.domain

import java.util.Calendar
import java.util.Locale

/**
 * Pure API 24+ safe date math (Howard Hinnant's civil-from-days / days-from-civil)
 * so all date queries, indexing, and Robolectric tests run reliably without API 26 desugaring.
 */
object DateUtils {
  data class CivilDate(val year: Int, val month: Int, val day: Int)

  fun todayEpochDay(): Long {
    val cal = Calendar.getInstance()
    return civilToEpochDay(
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
    )
  }

  fun civilToEpochDay(year: Int, month: Int, day: Int): Long {
    var y = year.toLong()
    val m = month.toLong()
    val d = day.toLong()
    if (m <= 2) y -= 1
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  fun epochDayToCivil(epochDay: Long): CivilDate {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    if (m <= 2) y += 1
    return CivilDate(y.toInt(), m.toInt(), d.toInt())
  }

  fun epochDayToYearMonth(epochDay: Long): Int {
    val c = epochDayToCivil(epochDay)
    return c.year * 100 + c.month
  }

  fun yearMonthOf(year: Int, month: Int): Int = year * 100 + month

  fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
      1, 3, 5, 7, 8, 10, 12 -> 31
      4, 6, 9, 11 -> 30
      2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
      else -> 30
    }
  }

  fun monthRange(year: Int, month: Int): Pair<Long, Long> {
    val start = civilToEpochDay(year, month, 1)
    val end = civilToEpochDay(year, month, daysInMonth(year, month))
    return start to end
  }

  /**
   * Monday-start week bounds for the given epochDay.
   * 1970-01-01 (epochDay = 0) was a Thursday (index 3 in 0=Mon..6=Sun).
   */
  fun dayOfWeekIndexMon0(epochDay: Long): Int {
    val mod = ((epochDay + 3) % 7).toInt()
    return if (mod < 0) mod + 7 else mod
  }

  fun weekRange(epochDay: Long): Pair<Long, Long> {
    val dow = dayOfWeekIndexMon0(epochDay)
    val start = epochDay - dow
    return start to (start + 6)
  }

  fun isoWeekKey(epochDay: Long): String {
    val (start, _) = weekRange(epochDay)
    val c = epochDayToCivil(start)
    return String.format(Locale.US, "W-%04d-%02d-%02d", c.year, c.month, c.day)
  }

  fun formatShortDateTr(epochDay: Long): String {
    val c = epochDayToCivil(epochDay)
    val months =
      arrayOf(
        "Oca",
        "Şub",
        "Mar",
        "Nis",
        "May",
        "Haz",
        "Tem",
        "Ağu",
        "Eyl",
        "Eki",
        "Kas",
        "Ara",
      )
    return "${c.day} ${months[(c.month - 1).coerceIn(0, 11)]}"
  }

  fun formatFullDateTr(epochDay: Long): String {
    val c = epochDayToCivil(epochDay)
    val months =
      arrayOf(
        "Ocak",
        "Şubat",
        "Mart",
        "Nisan",
        "Mayıs",
        "Haziran",
        "Temmuz",
        "Ağustos",
        "Eylül",
        "Ekim",
        "Kasım",
        "Aralık",
      )
    return "${c.day} ${months[(c.month - 1).coerceIn(0, 11)]} ${c.year}"
  }

  fun formatMonthYearTr(year: Int, month: Int): String {
    val months =
      arrayOf(
        "Ocak",
        "Şubat",
        "Mart",
        "Nisan",
        "Mayıs",
        "Haziran",
        "Temmuz",
        "Ağustos",
        "Eylül",
        "Ekim",
        "Kasım",
        "Aralık",
      )
    return "${months[(month - 1).coerceIn(0, 11)]} $year"
  }

  fun dayOfWeekShortTr(epochDay: Long): String {
    val names = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    return names[dayOfWeekIndexMon0(epochDay)]
  }
}
