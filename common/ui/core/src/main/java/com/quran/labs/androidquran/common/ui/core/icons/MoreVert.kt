package com.quran.labs.androidquran.common.ui.core.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

val MoreVert: ImageVector by lazy {
  materialIcon(name = "Filled.MoreVert") {
    materialPath {
      moveTo(12.0f, 8.0f)
      curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
      reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
      reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
      reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
      close()
      moveTo(12.0f, 10.0f)
      curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
      reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
      reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
      reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
      close()
      moveTo(12.0f, 16.0f)
      curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
      reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
      reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
      reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
      close()
    }
  }
}

@Preview
@Composable
private fun MoreVertIconPreview() {
  Icon(imageVector = MoreVert, contentDescription = null)
}
