package com.icpeek.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
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
    val previousBalance: Int = -1  // 前の取引の残高
) : Parcelable {
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
            balance - previousBalance
        } else {
            0
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
    
    fun getAmountChangeColor(): Int {
        val change = getAmountChange()
        return when {
            change > 0 -> 0xFF4CAF50.toInt()  // 緑（増加）
            change < 0 -> 0xFFF44336.toInt()  // 赤（減少）
            else -> 0xFF757575.toInt()    // 灰色（変化なし）
        }
    }
}
