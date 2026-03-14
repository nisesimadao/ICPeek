package com.icpeek.app

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
        val textMatch = getDisplayInfo().lowercase().contains(searchLower) ||
                      inStation.lowercase().contains(searchLower) ||
                      outStation.lowercase().contains(searchLower) ||
                      getFormattedDate().contains(searchLower)
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
    
    // 日付範囲フィルター
    // Note: TransactionInfoに日付情報を追加する必要がある場合
    // 現在はフォーマットされた日付文字列で簡易的に判定
    
    return true
}
