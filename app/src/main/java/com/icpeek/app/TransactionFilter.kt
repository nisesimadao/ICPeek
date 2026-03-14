package com.icpeek.app

import com.icpeek.app.model.TransactionInfo
import java.util.Calendar

/**
 * 取引検索フィルターデータクラス
 */
data class TransactionFilter(
    val searchText: String = "",
    val filterType: FilterType = FilterType.ALL,
    val minAmount: Int? = null,
    val maxAmount: Int? = null,
    val startDate: Long? = null,
    val endDate: Long? = null
) {
    /**
     * フィルターが有効かどうか
     */
    fun isActive(): Boolean {
        return searchText.isNotEmpty() || 
               filterType != FilterType.ALL ||
               minAmount != null ||
               maxAmount != null ||
               startDate != null ||
               endDate != null
    }
}

/**
 * フィルター種別
 */
enum class FilterType {
    ALL,
    CHARGE,
    PURCHASE,
    TRANSIT
}

/**
 * 取引情報の拡張関数
 */
fun TransactionInfo.matchesFilter(filter: TransactionFilter): Boolean {
    // テキスト検索
    if (filter.searchText.isNotEmpty()) {
        val searchLower = filter.searchText.lowercase()
        
        // 検索対象を一度にまとめて処理
        val searchTargets = listOf(
            getDisplayInfo(),
            getInStationName(),
            getOutStationName(),
            getFormattedDate()
        )
        
        val textMatch = searchTargets.any { target ->
            target.lowercase().contains(searchLower)
        }
        if (!textMatch) return false
    }
    
    // 種別フィルター
    when (filter.filterType) {
        FilterType.CHARGE -> {
            if (!transactionType.contains("チャージ") && !transactionType.contains("Charge")) return false
        }
        FilterType.PURCHASE -> {
            if (!transactionType.contains("物販") && !transactionType.contains("Purchase")) return false
        }
        FilterType.TRANSIT -> {
            if (!transactionType.contains("乗車") && !transactionType.contains("Transit") && 
                !transactionType.contains("運賃") && !transactionType.contains("Fare")) return false
        }
        FilterType.ALL -> { /* 何もしない */ }
    }
    
    // 金額範囲フィルター
    filter.minAmount?.let { min ->
        if (balance < min) return false
    }
    
    filter.maxAmount?.let { max ->
        if (balance > max) return false
    }
    
    // マイナス残高の特別処理
    if (balance < 0) {
        // マイナス残高の場合、最小金額フィルターがなければ除外しない
        filter.minAmount ?: return true
        // 最小金額が設定されている場合、マイナス値も考慮
        if (balance >= filter.minAmount) return true
        return false
    }
    
    // 日付範囲フィルター
    filter.startDate?.let { start ->
        val transactionDate = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (transactionDate < start) return false
    }
    
    filter.endDate?.let { end ->
        val transactionDate = Calendar.getInstance().apply {
            set(year, month - 1, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        if (transactionDate > end) return false
    }
    
    return true
}
