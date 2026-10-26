package com.mewname.app.domain

// Shared precompiled patterns: the normalization helpers run inside catalog loops and on every
// search keystroke, so compiling these per call was a measurable cost.
internal val COMBINING_DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
internal val COMBINING_MARKS = Regex("\\p{M}+")
internal val NON_ALPHANUMERIC_RUNS = Regex("[^A-Za-z0-9]+")
internal val NON_UPPER_ALPHANUMERIC_RUNS = Regex("[^A-Z0-9]+")
