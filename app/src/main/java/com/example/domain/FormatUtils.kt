package com.example.domain

import java.util.Locale

object FormatUtils {
  fun formatStepsTr(steps: Int): String {
    return String.format(Locale.US, "%,d", steps).replace(',', '.')
  }
}
