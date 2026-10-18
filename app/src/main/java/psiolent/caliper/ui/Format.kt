package psiolent.caliper.ui

/** Whole numbers print whole; everything else gets one decimal. */
internal fun round(value: Double): String =
    if (value >= 10 || value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
