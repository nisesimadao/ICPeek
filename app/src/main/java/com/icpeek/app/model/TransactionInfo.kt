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
            val rawChange = balance - previousBalance
            
            // 取引タイプに基づいて増減額の符号を調整
            when {
                // 改札・運賃支払系（残高が減少するはず）
                isFarePayment() -> -kotlin.math.abs(rawChange)
                
                // チャージ系（残高が増加するはず）
                isCharge() -> kotlin.math.abs(rawChange)
                
                // 物販系（残高が減少するはず）
                isShopping() -> -kotlin.math.abs(rawChange)
                
                // その他はそのまま
                else -> rawChange
            }
        } else {
            0
        }
    }
    
    private fun isFarePayment(): Boolean {
        return processId in listOf(
            1,   // 運賃支払(改札出場)
            4,   // 精算
            5,   // 精算 (入場精算)
            6,   // 窓出 (改札窓口処理)
            19,  // 支払 (新幹線利用)
            132, // 精算 (他社精算)
            133  // 精算 (他社入場精算)
        )
    }
    
    private fun isCharge(): Boolean {
        return when (processId) {
            2 -> true   // チャージ
            20 -> true  // 入A (入場時オートチャージ)
            21 -> true  // 出A (出場時オートチャージ)
            31 -> true  // 入金 (バスチャージ)
            72 -> true  // 特典 (特典チャージ)
            73 -> when (terminalId) {
                9, 18, 31 -> true   // 入金機での入金
                else -> false
            }
            70 -> when (terminalId) {
                9, 18, 31 -> true   // 入金機での処理（チャージ）
                else -> false       // その他は物販
            }
            198, 203 -> {
                // 現金併用物販の場合、残高が増加していればチャージと判定
                val rawChange = balance - previousBalance
                rawChange > 0
            }
            else -> false
        }
    }
    
    private fun isShopping(): Boolean {
        return when (processId) {
            70 -> when (terminalId) {
                199, 200 -> true  // 物販端末、自販機
                else -> false
            }
            74 -> true   // 物販取消
            75 -> true   // 入物 (入場物販)
            198, 203 -> {
                // 現金併用物販の場合、残高が減少していれば物販と判定
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
    
    fun getAmountChangeColor(): Int {
        val change = getAmountChange()
        return when {
            change > 0 -> 0xFF4CAF50.toInt() // Green for increase
            change < 0 -> 0xFFF44336.toInt() // Red for decrease
            else -> 0xFF757575.toInt()  // Gray for no change
        }
    }
    
    // 駅名取得メソッド（検索用）
    fun getInStationName(): String {
        // TODO: 駅名のマッピングを実装
        return "駅$inStation"
    }
    
    fun getOutStationName(): String {
        // TODO: 駅名のマッピングを実装
        return "駅$outStation"
    }
}
