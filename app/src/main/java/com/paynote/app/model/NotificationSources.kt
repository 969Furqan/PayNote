package com.paynote.app.model

/** Source classification stays independent of the bank inferred from an SMS body. */
object NotificationSources {
    fun isSmsPackage(packageName: String): Boolean =
        packageName == "com.android.mms" ||
            packageName.contains("messaging", ignoreCase = true) ||
            packageName.contains("mms", ignoreCase = true)
}
