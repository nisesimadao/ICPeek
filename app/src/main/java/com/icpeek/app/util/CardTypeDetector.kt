package com.icpeek.app.util

object CardTypeDetector {
    
    fun detectCardType(systemCode: String, manufacturer: String): String {
        return when {
            // ICOCA cards typically use system code 0003
            systemCode == "0003" -> {
                when {
                    manufacturer.startsWith("0136428247") -> "ICOCA"
                    else -> "FeliCa Card"
                }
            }
            // Suica cards
            systemCode == "0003" && manufacturer.startsWith("0136") -> "Suica/PASMO"
            // Edy cards
            systemCode == "FE00" -> "Edy"
            // nanaco cards
            systemCode == "564F" -> "nanaco"
            // WAON cards
            systemCode == "680B" -> "WAON"
            else -> "Unknown FeliCa Card"
        }
    }
    
    fun getCardColor(cardType: String): String {
        return when (cardType) {
            "ICOCA" -> "#4A90E2"  // ICOCA blue
            "Suica/PASMO" -> "#FF6B35"  // Suica orange
            "Edy" -> "#00A9E6"  // Edy blue
            "nanaco" -> "#E60012"  // nanaco red
            "WAON" -> "#00B900"  // WAON green
            else -> "#757575"  // Default gray
        }
    }
}
