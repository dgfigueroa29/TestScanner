package com.whyscan.core.scanner

actual object SystemTimeProvider : TimeProvider {
    actual override fun nowMillis(): Long = System.currentTimeMillis()
}
