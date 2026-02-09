plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.notes"

dependencies {
  implementation(project(":common:audio"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:reading"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.annotation)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment.ktx)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
