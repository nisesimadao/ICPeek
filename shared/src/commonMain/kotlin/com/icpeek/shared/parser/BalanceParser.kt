package com.icpeek.shared.parser

import com.icpeek.shared.model.TransactionInfo

class BalanceParser {

    /**
     * Parses balance from FeliCa Read Without Encryption response
     * 
     * Response format:
     * [0]     - Length
     * [1]     - Response code (0x07 = success)
     * [2..9]  - IDm (8 bytes)
     * [10..11] - Error code (0x00 = success)
     * [12]    - Number of response blocks
     * [13+n*16] - History data (16 bytes per block)
     * 
     * Balance is at bytes 10-11 of each 16-byte block (little endian)
     */
    fun parseBalance(response: ByteArray): Int {
        try {
            if (response.size < 13) {
                return -1
            }

            if (response[1].toInt() and 0xFF != 0x07) {
                return -1
            }

            if (response[10].toInt() and 0xFF != 0x00 || response[11].toInt() and 0xFF != 0x00) {
                return -1
            }

            val blockCount = response[12].toInt() and 0xFF
            
            if (blockCount == 0) {
                return -1
            }

            // Parse first block for balance
            if (response.size >= 13 + 16) {
                val blockOffset = 13
                
                val balance = ((response[blockOffset + 11].toInt() and 0xFF) shl 8) or 
                              (response[blockOffset + 10].toInt() and 0xFF)
                
                if (balance > 0) {
                    return balance
                }
            }

            // Try other blocks if available
            for (i in 0 until blockCount) {
                val blockOffset = 13 + i * 16
                if (response.size >= blockOffset + 16) {
                    val balance = ((response[blockOffset + 11].toInt() and 0xFF) shl 8) or 
                                  (response[blockOffset + 10].toInt() and 0xFF)
                    
                    if (balance > 0) {
                        return balance
                    }
                }
            }

            return -1

        } catch (e: Exception) {
            return -1
        }
    }

    /**
     * Parses transactions from response data
     */
    fun parseTransactions(response: ByteArray): List<TransactionInfo> {
        val transactions = mutableListOf<TransactionInfo>()
        
        try {
            if (response.size < 13) return emptyList()
            
            val blockCount = response[12].toInt() and 0xFF
            var previousBalance = -1
            
            for (i in 0 until blockCount) {
                val blockOffset = 13 + i * 16
                if (response.size >= blockOffset + 16) {
                    val transaction = parseTransaction(response, blockOffset, i, previousBalance)
                    if (transaction != null) {
                        transactions.add(transaction)
                        previousBalance = transaction.balance
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on error
        }
        
        return transactions
    }

    /**
     * Parses a single transaction from a 16-byte block
     */
    private fun parseTransaction(response: ByteArray, blockOffset: Int, blockIndex: Int, previousBalance: Int): TransactionInfo? {
        try {
            val termId = response[blockOffset + 0].toInt() and 0xFF
            val procId = response[blockOffset + 1].toInt() and 0xFF
            
            // Date/time parsing (bytes 4-5)
            val mixInt = ((response[blockOffset + 4].toInt() and 0xFF) shl 8) or 
                        (response[blockOffset + 5].toInt() and 0xFF)
            val year = (mixInt shr 9) and 0x07F
            val month = (mixInt shr 5) and 0x00F
            val day = mixInt and 0x01F
            
            val fullYear = if (year < 80) 2000 + year else 1900 + year
            
            // Line and station codes
            val inLine = response[blockOffset + 6].toInt() and 0xFF
            val inStation = response[blockOffset + 7].toInt() and 0xFF
            val outLine = response[blockOffset + 8].toInt() and 0xFF
            val outStation = response[blockOffset + 9].toInt() and 0xFF
            
            // Balance (bytes 10-11, little endian)
            val balance = ((response[blockOffset + 11].toInt() and 0xFF) shl 8) or 
                         (response[blockOffset + 10].toInt() and 0xFF)
            
            // Sequence number (bytes 12-14)
            val seqNo = ((response[blockOffset + 14].toInt() and 0xFF) shl 16) or 
                       ((response[blockOffset + 13].toInt() and 0xFF) shl 8) or 
                       (response[blockOffset + 12].toInt() and 0xFF)
            
            // Region (byte 15)
            val region = response[blockOffset + 15].toInt() and 0xFF
            
            val blockData = response.copyOfRange(blockOffset, blockOffset + 16)
            
            val transactionType = when {
                isShopping(procId) -> "物販"
                isBus(procId) -> "バス"
                inLine < 0x80 -> "JR"
                else -> "公営/私鉄"
            }
            
            return TransactionInfo(
                terminalId = termId,
                terminalName = getTerminalName(termId),
                processId = procId,
                processName = getProcessName(procId),
                year = fullYear,
                month = month,
                day = day,
                inLine = inLine,
                inStation = inStation,
                outLine = outLine,
                outStation = outStation,
                balance = balance,
                sequence = seqNo,
                region = region,
                transactionType = transactionType,
                rawData = blockData.toHexString(),
                previousBalance = previousBalance
            )
            
        } catch (e: Exception) {
            return null
        }
    }

    private fun getTerminalName(termId: Int): String {
        return when (termId) {
            3 -> "精算機"
            4 -> "携帯型端末"
            5 -> "車載端末"
            7 -> "券売機"
            8 -> "券売機"
            9 -> "入金機"
            18 -> "券売機"
            20 -> "券売機等"
            21 -> "券売機等"
            22 -> "改札機"
            23 -> "簡易改札機"
            24 -> "窓口端末"
            25 -> "窓口端末"
            26 -> "改札端末"
            27 -> "携帯電話"
            28 -> "乗継精算機"
            29 -> "連絡改札機"
            31 -> "簡易入金機"
            70 -> "VIEW ALTTE"
            72 -> "VIEW ALTTE"
            199 -> "物販端末"
            200 -> "自販機"
            else -> "Unknown ($termId)"
        }
    }

    private fun getProcessName(procId: Int): String {
        return when (procId) {
            1 -> "運賃支払(改札出場)"
            2 -> "チャージ"
            3 -> "券購(磁気券購入)"
            4 -> "精算"
            5 -> "精算 (入場精算)"
            6 -> "窓出 (改札窓口処理)"
            7 -> "新規 (新規発行)"
            8 -> "控除 (窓口控除)"
            13 -> "バス (PiTaPa系)"
            15 -> "バス (IruCa系)"
            17 -> "再発 (再発行処理)"
            19 -> "支払 (新幹線利用)"
            20 -> "入A (入場時オートチャージ)"
            21 -> "出A (出場時オートチャージ)"
            31 -> "入金 (バスチャージ)"
            35 -> "券購 (バス路面電車企画券購入)"
            70 -> "物販"
            72 -> "特典 (特典チャージ)"
            73 -> "入金 (レジ入金)"
            74 -> "物販取消"
            75 -> "入物 (入場物販)"
            198 -> "物現 (現金併用物販)"
            203 -> "入物 (入場現金併用物販)"
            132 -> "精算 (他社精算)"
            133 -> "精算 (他社入場精算)"
            else -> "Unknown ($procId)"
        }
    }

    private fun isShopping(procId: Int): Boolean {
        return procId == 70 || procId == 73 || procId == 74 || procId == 75 || procId == 198 || procId == 203
    }

    private fun isBus(procId: Int): Boolean {
        return procId == 13 || procId == 15 || procId == 31 || procId == 35
    }
}
