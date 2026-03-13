package com.icpeek.app.nfc

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.NfcF
import android.content.Context
import java.io.ByteArrayOutputStream
import com.icpeek.app.felica.FelicaService
import com.icpeek.app.parser.BalanceParser
import com.icpeek.app.parser.TransactionParser
import com.icpeek.app.model.TransactionInfo

class NFCReader(private val context: Context) {

    val pendingIntent: PendingIntent
    val intentFiltersArray: Array<IntentFilter>
    val techListsArray: Array<Array<String>>

    private val felicaService = FelicaService()
    private val balanceParser = BalanceParser()
    private val transactionParser = TransactionParser()
    
    // Expose felicaService for MainActivity access
    val felicaServiceInstance: FelicaService
        get() = felicaService
    
    // Callback to pass transaction data to MainActivity
    interface TransactionCallback {
        fun onTransactionsReceived(transactions: List<TransactionInfo>)
    }
    
    private var transactionCallback: TransactionCallback? = null
    
    fun setTransactionCallback(callback: TransactionCallback) {
        transactionCallback = callback
    }

    init {
        pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, context.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), 
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent filters for all NFC actions
        val techDiscovered = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagDiscovered = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val ndefDiscovered = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        
        try {
            ndefDiscovered.addDataType("*/*")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("fail", e)
        }
        
        intentFiltersArray = arrayOf(techDiscovered, tagDiscovered, ndefDiscovered)
        techListsArray = arrayOf(arrayOf("android.nfc.tech.NfcF"))
    }

    fun readBalance(nfcF: NfcF): Int {
        return try {
            android.util.Log.d("NFCReader", "Starting balance reading...")
            
            // Add delay to ensure stable connection
            Thread.sleep(50)
            
            // Get IDm from polling response
            val pollingCommand = felicaService.createPollingCommand()
            android.util.Log.d("NFCReader", "Polling command: ${pollingCommand.toHexString()}")
            
            val pollingResponse = nfcF.transceive(pollingCommand)
            android.util.Log.d("NFCReader", "Polling response: ${pollingResponse?.toHexString()}")
            
            if (pollingResponse == null || pollingResponse.size < 12) {
                android.util.Log.e("NFCReader", "Polling response invalid: null or too short (${pollingResponse?.size})")
                return -1
            }
            
            // Extract IDm (bytes 2-9 of polling response)
            val idm = ByteArray(8)
            System.arraycopy(pollingResponse, 2, idm, 0, 8)
            android.util.Log.d("NFCReader", "Extracted IDm: ${idm.toHexString()}")
            
            // Add small delay between commands
            Thread.sleep(20)
            
            // Based on CardReader-master, use service code 0x090F and read 10 blocks for history
            val readCommand = felicaService.createReadWithoutEncryptionCommand(idm, 0x090F)
            android.util.Log.d("NFCReader", "Read command: ${readCommand.toHexString()}")
            
            val response = nfcF.transceive(readCommand)
            android.util.Log.d("NFCReader", "Read response: ${response?.toHexString()}")
            
            if (response != null && response.size >= 13) {
                // Try to parse balance using CardReader-master format
                val balance = balanceParser.parseBalance(response)
                if (balance > 0) {
                    android.util.Log.d("NFCReader", "SUCCESS: Balance read: ¥$balance")
                    
                    // Try to read more history blocks if available
                    try {
                        val historyCommand = createHistoryReadCommand(idm, 10)
                        android.util.Log.d("NFCReader", "History command: ${historyCommand.toHexString()}")
                        
                        val historyResponse = nfcF.transceive(historyCommand)
                        android.util.Log.d("NFCReader", "History response: ${historyResponse?.toHexString()}")
                        
                        if (historyResponse != null && historyResponse.size >= 13) {
                        balanceParser.parseBalance(historyResponse)
                        
                        // Parse and send transactions to MainActivity
                        val blockCount = historyResponse[12].toInt() and 0xFF
                        val transactions = mutableListOf<TransactionInfo>()
                        var previousBalance = -1
                        
                        for (i in 0 until blockCount) {
                            val blockOffset = 13 + i * 16
                            if (historyResponse.size >= blockOffset + 16) {
                                val transaction = transactionParser.parseTransaction(historyResponse, blockOffset, i, previousBalance)
                                if (transaction != null) {
                                    transactions.add(transaction)
                                    previousBalance = transaction.balance
                                }
                            }
                        }
                        
                        // Send transactions to MainActivity via callback
                        transactionCallback?.onTransactionsReceived(transactions)
                    }
                    } catch (e: Exception) {
                        android.util.Log.w("NFCReader", "Failed to read extended history: ${e.message}")
                    }
                    
                    return balance
                } else {
                    android.util.Log.d("NFCReader", "Balance parsing failed, trying alternative service codes")
                    
                    // Try alternative service codes if primary fails
                    val serviceCodes = intArrayOf(
                        0x170F, 0x1A8B, 0x500B, 0x890B, 0x0900, 0x0A8B
                    )
                    
                    for (serviceCode in serviceCodes) {
                        android.util.Log.d("NFCReader", "Trying alternative service code: ${String.format("%04X", serviceCode)}")
                        
                        try {
                            val altCommand = felicaService.createReadWithoutEncryptionCommand(idm, serviceCode)
                            val altResponse = nfcF.transceive(altCommand)
                            android.util.Log.d("NFCReader", "Alt response: ${altResponse?.toHexString()}")
                            
                            if (altResponse != null && altResponse.size >= 13) {
                                val altBalance = balanceParser.parseBalance(altResponse)
                                if (altBalance > 0) {
                                    android.util.Log.d("NFCReader", "SUCCESS: Balance read with service code ${String.format("%04X", serviceCode)}: ¥$altBalance")
                                    return altBalance
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("NFCReader", "Exception with service code ${String.format("%04X", serviceCode)}: ${e.message}")
                        }
                        
                        Thread.sleep(30)
                    }
                }
            } else {
                android.util.Log.w("NFCReader", "Read response invalid: null or too short (${response?.size})")
            }
            
            android.util.Log.e("NFCReader", "Failed to read balance with all service codes")
            -1
        } catch (e: Exception) {
            android.util.Log.e("NFCReader", "Error reading balance", e)
            -1
        }
    }
    
    /**
     * Creates a command to read multiple history blocks
     * Based on CardReader-master implementation
     */
    private fun createHistoryReadCommand(idm: ByteArray, blockCount: Int): ByteArray {
        val command = ByteArrayOutputStream(100)
        
        command.write(0)                    // data length. change after all byte set.
        command.write(0x06)                 // Felica command, Read Without Encryption
        command.write(idm)                  // NFC ID (8byte)
        
        command.write(1)                    // service code length (2byte)
        command.write(0x0F)                 // low byte of service code (little endian)
        command.write(0x09)                 // high byte of service code (little endian)
        command.write(blockCount)           // number of blocks
        
        for (i in 0 until blockCount) {
            command.write(0x80)             // ブロックエレメント上位バイト
            command.write(i)                // ブロック番号
        }
        
        val result = command.toByteArray()
        result[0] = (result.size).toByte()  // 先頭１バイトはデータ長
        return result
    }
    
    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { "%02X".format(it) }
    }
}
