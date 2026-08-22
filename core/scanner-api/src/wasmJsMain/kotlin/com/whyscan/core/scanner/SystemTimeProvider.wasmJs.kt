package com.whyscan.core.scanner

private fun jsNowMillis(): Double = js("Date.now()")

actual object SystemTimeProvider : TimeProvider {
    actual override fun nowMillis(): Long = jsNowMillis().toLong()
}
