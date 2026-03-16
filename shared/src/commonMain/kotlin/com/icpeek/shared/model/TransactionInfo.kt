package com.icpeek.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionInfo(
    val terminalId: Int,
    val terminalName: String,
    val processId: Int,
    val processName: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val inLine: Int,
    val inStation: Int,
    val outLine: Int,
    val outStation: Int,
    val balance: Int,
    val sequence: Int,
    val region: Int,
    val transactionType: String,
    val rawData: String,
    val previousBalance: Int = -1
) {
    fun getFormattedDate(): String {
        return String.format("%04d/%02d/%02d", year, month, day)
    }
    
    fun getFormattedBalance(): String {
        return "¥$balance"
    }
    
    fun getDisplayInfo(): String {
        return when {
            processId == 70 -> "物販: $terminalName"
            processId == 2 -> "チャージ: $terminalName"
            processId == 1 -> "運賃支払: $terminalName"
            inLine < 0x80 -> "JR線利用"
            else -> "私鉄/公営利用"
        }
    }
    
    fun getAmountChange(): Int {
        return if (previousBalance > 0) {
            val rawChange = balance - previousBalance
            
            when {
                isFarePayment() -> -kotlin.math.abs(rawChange)
                isCharge() -> kotlin.math.abs(rawChange)
                isShopping() -> -kotlin.math.abs(rawChange)
                else -> rawChange
            }
        } else {
            0
        }
    }
    
    private fun isFarePayment(): Boolean {
        return processId in listOf(
            1, 4, 5, 6, 19, 132, 133
        )
    }
    
    private fun isCharge(): Boolean {
        return when (processId) {
            2, 20, 21, 31, 72 -> true
            73 -> when (terminalId) {
                9, 18, 31 -> true
                else -> false
            }
            70 -> when (terminalId) {
                9, 18, 31 -> true
                else -> false
            }
            198, 203 -> {
                val rawChange = balance - previousBalance
                rawChange > 0
            }
            else -> false
        }
    }
    
    private fun isShopping(): Boolean {
        return when (processId) {
            70 -> when (terminalId) {
                199, 200 -> true
                else -> false
            }
            74, 75 -> true
            198, 203 -> {
                val rawChange = balance - previousBalance
                rawChange < 0
            }
            else -> false
        }
    }
    
    fun getFormattedAmountChange(): String {
        val change = getAmountChange()
        return when {
            change > 0 -> "+¥$change"
            change < 0 -> "-¥${-change}"
            else -> ""
        }
    }
}

@Serializable
data class CardInfo(
    val cardType: String,
    val balance: Int,
    val transactions: List<TransactionInfo>,
    val idm: String,
    val systemCode: String
)
