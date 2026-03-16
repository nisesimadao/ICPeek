package com.icpeek.shared.platform

import com.icpeek.shared.nfc.NFCReader
import com.icpeek.shared.model.CardInfo
import com.icpeek.shared.nfc.NFCReaderCommon
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.CoreNFC.NFCFeliCaTag
import platform.CoreNFC.NFCPollingISO15693Request
import platform.CoreNFC.NFCFeliCaTagProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class NFCReader {
    
    private val nfcReaderCommon = NFCReaderCommon()
    
    actual suspend fun readCard(): CardInfo? {
        return suspendCancellableCoroutine { continuation ->
            val sessionDelegate = NFCSessionDelegate { cardInfo ->
                continuation.resume(cardInfo)
            }
            
            val session = NFCTagReaderSession(
                pollingOption = NFCPollingISO15693Request(),
                delegate = sessionDelegate,
                queue = null
            )
            
            sessionDelegate.session = session
            session?.beginSession()
            
            continuation.invokeOnCancellation {
                session?.invalidateSession()
            }
        }
    }
}

private class NFCSessionDelegate(
    private val onCardRead: (CardInfo?) -> Unit
) : NSObject(), NFCTagReaderSessionDelegateProtocol {
    
    var session: NFCTagReaderSession? = null
    
    override fun tagReaderSessionDidBecomeActive(session: NFCTagReaderSession) {
        // Session became active
    }
    
    override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
        onCardRead(null)
    }
    
    override fun tagReaderSession(session: NFCTagReaderSession, didDetect tags: List<*>) {
        for (tag in tags) {
            if (tag is NFCFeliCaTag) {
                readFeliCaTag(tag)
                break
            }
        }
    }
    
    private fun readFeliCaTag(felicaTag: NFCFeliCaTag) {
        felicaTag.requestService { data, error in
            if (error != null || data == null) {
                this.session?.invalidateSession()
                onCardRead(null)
                return@requestService
            }
            
            val idm = data.toByteArray()
            
            felicaTag.sendFeliCaCommand(
                commandPacket = createReadCommand(idm).toNSData()
            ) { responseData, responseError in
                if (responseError != null || responseData == null) {
                    this.session?.invalidateSession()
                    onCardRead(null)
                    return@sendFeliCaCommand
                }
                
                val response = responseData.toByteArray()
                
                // Process the response using common logic
                val nfcReaderCommon = com.icpeek.shared.nfc.NFCReaderCommon()
                
                // Create a simple read function for iOS
                val readFunction = suspend { command: ByteArray ->
                    felicaTag.sendFeliCaCommand(
                        commandPacket = command.toNSData()
                    ) { response, error ->
                        // This callback will be handled differently in actual implementation
                    }
                    null // Placeholder - actual async implementation needed
                }
                
                this.session?.invalidateSession()
                // For now, return null - actual implementation needs proper async handling
                onCardRead(null)
            }
        }
    }
    
    private fun createReadCommand(idm: ByteArray): ByteArray {
        val felicaService = com.icpeek.shared.felica.FelicaService()
        return felicaService.createReadWithoutEncryptionCommand(idm, 0x090F)
    }
}

// Extension functions for data conversion
private fun ByteArray.toNSData(): NSData {
    return NSData(bytes = this.toCValues(), length = this.size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(this.length.toInt())
    this.getBytes(bytes, NSMakeRange(0UL, this.length))
    return bytes
}
