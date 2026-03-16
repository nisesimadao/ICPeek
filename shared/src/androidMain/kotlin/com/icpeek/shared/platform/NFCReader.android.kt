package com.icpeek.shared.platform

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.content.Context
import com.icpeek.shared.nfc.NFCReader
import com.icpeek.shared.nfc.NFCReaderCommon
import com.icpeek.shared.model.CardInfo
import kotlinx.coroutines.delay

actual class NFCReader(private val context: Context? = null) {
    
    private val nfcReaderCommon = NFCReaderCommon()
    
    actual suspend fun readCard(): CardInfo? {
        // This method will be called from Android NFC callback
        // The actual tag reading should be done from the Android side
        return null
    }
    
    /**
     * Read card from Android NFC tag (API 29+ ReaderMode)
     */
    suspend fun readCardFromTag(tag: Tag): CardInfo? {
        return try {
            val nfcF = NfcF.get(tag) ?: return null
            nfcF.connect()
            
            val result = nfcReaderCommon.processCardData(
                idm = getIdmFromTag(nfcF),
                readFunction = { command ->
                    try {
                        kotlinx.coroutines.delay(20) // Small delay between commands
                        nfcF.transceive(command)
                    } catch (e: Exception) {
                        null
                    }
                }
            )
            
            nfcF.close()
            result
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun getIdmFromTag(nfcF: NfcF): ByteArray {
        val pollingCommand = com.icpeek.shared.felica.FelicaService().createPollingCommand()
        val pollingResponse = nfcF.transceive(pollingCommand)
        
        return if (pollingResponse != null && pollingResponse.size >= 12) {
            val idm = ByteArray(8)
            System.arraycopy(pollingResponse, 2, idm, 0, 8)
            idm
        } else {
            ByteArray(8)
        }
    }
}
