package com.autokabala.listener

data class NameCandidate(val name: String, val source: String)

fun scoreNameCandidate(name: String): Int {
    val trimmed = name.trim()
    var score = 0

    val words = trimmed.split(" ").filter { it.length >= 2 }
    score += when (words.size) { 1 -> 5;  2 -> 20;  3 -> 15;  else -> 3 }

    score += trimmed.length.coerceIn(0, 20)

    if (words.any { it[0].isUpperCase() && it.drop(1).any { c -> c.isLowerCase() } }) score += 10

    val clean = trimmed.replace(" ", "")
    val dominant = clean.groupBy { it }.maxByOrNull { it.value.size }?.value?.size ?: 0
    if (clean.isNotEmpty() && dominant.toDouble() / clean.length > 0.70) score -= 60

    if (words.none { it.length >= 3 }) score -= 40

    return score
}

fun pickBestName(candidates: List<NameCandidate>): String? =
    candidates
        .filter { it.name.trim().length >= 2 }
        .maxByOrNull { scoreNameCandidate(it.name) }
        ?.name
