package com.testscanner.core.scanner

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

private const val MILLIS_PER_SECOND = 1_000.0

actual object SystemTimeProvider : TimeProvider {
    actual override fun nowMillis(): Long =
        (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
}
