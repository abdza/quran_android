package com.quran.data.model.note

data class Note(
  val id: Long,
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val text: String,
  val parentNoteId: Long? = null,
  val createdDate: Long = System.currentTimeMillis(),
  val updatedDate: Long = System.currentTimeMillis(),
  val labels: List<Long> = emptyList()
)
