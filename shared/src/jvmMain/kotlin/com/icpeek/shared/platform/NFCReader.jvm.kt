package com.icpeek.shared.platform

import com.icpeek.shared.nfc.NFCReader
import com.icpeek.shared.model.CardInfo

actual class NFCReader {
    actual suspend fun readCard(): CardInfo? {
        // JVM implementation delegates to common logic
        // This would be used for desktop JVM platforms
        return null
    }
}
