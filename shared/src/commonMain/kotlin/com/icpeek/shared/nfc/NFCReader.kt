package com.icpeek.shared.nfc

import com.icpeek.shared.felica.FelicaService
import com.icpeek.shared.model.CardInfo
import com.icpeek.shared.model.TransactionInfo
import com.icpeek.shared.parser.BalanceParser
import kotlinx.coroutines.delay
import kotlin.ExperimentalStdlibApi

@OptIn(ExperimentalStdlibApi::class)
expect class NFCReader {
    suspend fun readCard(): CardInfo?
}

class NFCReaderCommon {
    private val felicaService = FelicaService()
    private val balanceParser = BalanceParser()
    
    suspend fun processCardData(idm: ByteArray, readFunction: suspend (ByteArray) -> ByteArray?): CardInfo? {
        try {
            kotlinx.coroutines.delay(50) // Add delay to ensure stable connection
            
            // Based on CardReader-master, use service code 0x090F and read 10 blocks for history
            val readCommand = felicaService.createReadWithoutEncryptionCommand(idm, 0x090F)
            
            val response = readFunction(readCommand)
            
            if (response != null && response.size >= 13) {
                // Try to parse balance using CardReader-master format
                val balance = balanceParser.parseBalance(response)
                if (balance > 0) {
                    
                    // Try to read more history blocks if available
                    val transactions = try {
                        val historyCommand = felicaService.createHistoryReadCommand(idm, 10)
                        val historyResponse = readFunction(historyCommand)
                        
                        if (historyResponse != null && historyResponse.size >= 13) {
                            balanceParser.parseTransactions(historyResponse)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    
                    val cardType = detectCardType(idm)
                    val systemCode = "0003" // Default system code for common cards
                    
                    return CardInfo(
                        cardType = cardType,
                        balance = balance,
                        transactions = transactions,
                        idm = idm.toHexString(),
                        systemCode = systemCode
                    )
                } else {
                    // Try alternative service codes if primary fails
                    val serviceCodes = intArrayOf(
                        0x170F, 0x1A8B, 0x500B, 0x890B, 0x0900, 0x0A8B
                    )
                    
                    for (serviceCode in serviceCodes) {
                        try {
                            val altCommand = felicaService.createReadWithoutEncryptionCommand(idm, serviceCode)
                            val altResponse = readFunction(altCommand)
                           
                            if (altResponse != null && altResponse.size >= 13) {
                                val altBalance = balanceParser.parseBalance(altResponse)
                                if (altBalance > 0) {
                                    return CardInfo(
                                        cardType = detectCardType(idm),
                                        balance = altBalance,
                                        transactions = emptyList(),
                                        idm = idm.toHexString(),
                                        systemCode = String.format("%04X", serviceCode)
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Continue to next service code
                        }
                        
                        kotlinx.coroutines.delay(30)
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun detectCardType(idm: ByteArray): String {
        // Simple card type detection based on IDm patterns
        val idmString = idm.toHexString()
        
        return when {
            idmString.startsWith("01") -> "Suica/PASMO"
            idmString.startsWith("03") -> "ICOCA"
            idmString.startsWith("04") -> "Edy"
            idmString.startsWith("05") -> "WAON"
            idmString.startsWith("30") -> "nanaco"
            else -> "FeliCa Card"
        }
    }
}
