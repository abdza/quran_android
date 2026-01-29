plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.feature.wordbyword"

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))
  implementation(project(":common:reading"))
  implementation(project(":common:wordbyword"))

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material)
  implementation(libs.flexbox)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
