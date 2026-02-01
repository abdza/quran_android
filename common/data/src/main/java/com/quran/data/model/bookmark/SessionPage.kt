package com.quran.data.model.bookmark

data class SessionPage(
  val id: Long,
  val page: Int,
  val sessionStart: Long,
  val endedAt: Long
)
