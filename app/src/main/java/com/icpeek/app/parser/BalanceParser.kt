package com.icpeek.app.parser

class BalanceParser {

    /**
     * Extension function to convert ByteArray to hex string
     */
    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { "%02X".format(it) }
    }

    /**
     * Parses balance from FeliCa Read Without Encryption response
     * Based on CardReader-master implementation
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
            // Log full response for debugging
            android.util.Log.d("BalanceParser", "Full response: ${response.toHexString()}")
            android.util.Log.d("BalanceParser", "Response size: ${response.size}")
            
            // Check minimum response length
            if (response.size < 13) {
                android.util.Log.e("BalanceParser", "Response too short: ${response.size}")
                return -1
            }

            // Check response code
            if (response[1].toInt() and 0xFF != 0x07) {
                android.util.Log.e("BalanceParser", "Invalid response code: ${response[1].toInt() and 0xFF}")
                return -1
            }

            // Check error code
            if (response[10].toInt() and 0xFF != 0x00 || response[11].toInt() and 0xFF != 0x00) {
                android.util.Log.e("BalanceParser", "Error code: ${response[10].toInt() and 0xFF} ${response[11].toInt() and 0xFF}")
                return -1
            }

            // Get number of response blocks
            val blockCount = response[12].toInt() and 0xFF
            android.util.Log.d("BalanceParser", "Block count: $blockCount")
            
            if (blockCount == 0) {
                android.util.Log.e("BalanceParser", "No response blocks")
                return -1
            }

            // Parse first block for balance (most recent transaction)
            if (response.size >= 13 + 16) {
                val blockOffset = 13
                parseTransactionDetails(response, blockOffset, 0)
                
                // Balance is at bytes 10-11 of block (little endian) - bytes 23-24 of response
                val balance = ((response[blockOffset + 11].toInt() and 0xFF) shl 8) or 
                              (response[blockOffset + 10].toInt() and 0xFF)
                
                android.util.Log.d("BalanceParser", "Balance from first block: ¥$balance")
                
                if (balance > 0) {
                    return balance
                }
            }

            // Try other blocks if available
            for (i in 0 until blockCount) {
                val blockOffset = 13 + i * 16
                if (response.size >= blockOffset + 16) {
                    parseTransactionDetails(response, blockOffset, i)
                    
                    val balance = ((response[blockOffset + 11].toInt() and 0xFF) shl 8) or 
                                  (response[blockOffset + 10].toInt() and 0xFF)
                    
                    android.util.Log.d("BalanceParser", "Balance from block $i: ¥$balance")
                    
                    if (balance > 0) {
                        return balance
                    }
                }
            }

            android.util.Log.e("BalanceParser", "No valid balance found in $blockCount blocks")
            return -1

        } catch (e: Exception) {
            android.util.Log.e("BalanceParser", "Error parsing balance", e)
            return -1
        }
    }

    /**
     * Parses detailed transaction information from a 16-byte block
     * Based on CardReader-master FeliCa.java implementation
     */
    private fun parseTransactionDetails(response: ByteArray, blockOffset: Int, blockIndex: Int) {
        try {
            android.util.Log.d("BalanceParser", "=== Transaction Block $blockIndex ===")
            
            // Extract transaction details based on CardReader-master format
            val termId = response[blockOffset + 0].toInt() and 0xFF
            val procId = response[blockOffset + 1].toInt() and 0xFF
            
            // Date/time parsing (bytes 4-5)
            val mixInt = ((response[blockOffset + 4].toInt() and 0xFF) shl 8) or 
                        (response[blockOffset + 5].toInt() and 0xFF)
            val year = (mixInt shr 9) and 0x07F
            val month = (mixInt shr 5) and 0x00F
            val day = mixInt and 0x01F
            
            // Adjust year (assuming 2000s)
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
            
            // Log detailed information
            android.util.Log.d("BalanceParser", "Terminal ID: $termId (${getTerminalName(termId)})")
            android.util.Log.d("BalanceParser", "Process ID: $procId (${getProcessName(procId)})")
            android.util.Log.d("BalanceParser", "Date: $fullYear/$month/$day")
            android.util.Log.d("BalanceParser", "In Line: $inLine, In Station: $inStation")
            android.util.Log.d("BalanceParser", "Out Line: $outLine, Out Station: $outStation")
            android.util.Log.d("BalanceParser", "Balance: ¥$balance")
            android.util.Log.d("BalanceParser", "Sequence: $seqNo")
            android.util.Log.d("BalanceParser", "Region: $region")
            
            // Determine transaction type
            val transactionType = when {
                isShopping(procId) -> "物販"
                isBus(procId) -> "バス"
                inLine < 0x80 -> "JR"
                else -> "公営/私鉄"
            }
            android.util.Log.d("BalanceParser", "Transaction Type: $transactionType")
            
            // Log raw block data for reference
            val blockData = response.copyOfRange(blockOffset, blockOffset + 16)
            android.util.Log.d("BalanceParser", "Raw Block Data: ${blockData.toHexString()}")
            
        } catch (e: Exception) {
            android.util.Log.e("BalanceParser", "Error parsing transaction details for block $blockIndex", e)
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

    /**
     * Alternative parsing method for different card types
     * Some cards store balance at different positions
     */
    fun parseBalanceAlternative(response: ByteArray): Int {
        try {
            if (response.size < 22) {
                return -1
            }

            if (response[1] != 0x00.toByte()) {
                return -1
            }

            // Try different balance positions
            val blockDataOffset = 16
            
            // Position 0-1 (most common)
            var balance = parseBalanceAtPosition(response, blockDataOffset, 0)
            if (balance > 0) return balance

            // Position 2-3
            balance = parseBalanceAtPosition(response, blockDataOffset, 2)
            if (balance > 0) return balance

            // Position 4-5
            balance = parseBalanceAtPosition(response, blockDataOffset, 4)
            if (balance > 0) return balance

            return -1

        } catch (e: Exception) {
            return -1
        }
    }

    private fun parseBalanceAtPosition(response: ByteArray, blockDataOffset: Int, position: Int): Int {
        if (response.size < blockDataOffset + position + 2) {
            return -1
        }

        val byte1 = response[blockDataOffset + position].toInt() and 0xFF
        val byte2 = response[blockDataOffset + position + 1].toInt() and 0xFF

        return (byte2 shl 8) or byte1
    }
}
