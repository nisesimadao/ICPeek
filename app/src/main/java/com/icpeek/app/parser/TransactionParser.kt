package com.icpeek.app.parser

import com.icpeek.app.model.TransactionInfo

class TransactionParser {
    
    /**
     * Parses detailed transaction information from a 16-byte block
     * Based on CardReader-master FeliCa.java implementation
     */
    fun parseTransaction(response: ByteArray, blockOffset: Int, blockIndex: Int, previousBalance: Int = -1): TransactionInfo? {
        try {
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
            
            // Determine transaction type
            val transactionType = when {
                isShopping(procId) -> "物販"
                isBus(procId) -> "バス"
                inLine < 0x80 -> "JR"
                else -> "公営/私鉄"
            }
            
            // Raw block data for reference
            val blockData = response.copyOfRange(blockOffset, blockOffset + 16)
            val rawData = blockData.joinToString("") { "%02X".format(it) }
            
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
                rawData = rawData,
                previousBalance = previousBalance
            )
            
        } catch (e: Exception) {
            android.util.Log.e("TransactionParser", "Error parsing transaction details for block $blockIndex", e)
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
