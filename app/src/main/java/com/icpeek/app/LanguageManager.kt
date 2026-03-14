package com.icpeek.app

import android.content.Context
import java.util.*

/**
 * カスタム言語リソース管理クラス
 * languages/ ディレクトリ構造に対応
 */
class LanguageManager(private val context: Context) {
    
    companion object {
        private const val LANGUAGE_PREF = "language_preference"
        private const val DEFAULT_LANGUAGE = "en"
        
        // サポート言語リスト
        val SUPPORTED_LANGUAGES = listOf("ja", "en", "zh")
    }
    
    /**
     * 現在の言語設定を取得
     */
    fun getCurrentLanguage(): String {
        return context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
            .getString(LANGUAGE_PREF, getDefaultSystemLanguage())
            ?: DEFAULT_LANGUAGE
    }
    
    /**
     * 言語を設定
     */
    fun setLanguage(language: String) {
        if (language in SUPPORTED_LANGUAGES) {
            context.getSharedPreferences(LANGUAGE_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(LANGUAGE_PREF, language)
                .apply()
        }
    }
    
    /**
     * システムのデフォルト言語を取得
     */
    private fun getDefaultSystemLanguage(): String {
        val systemLocale = Locale.getDefault()
        return when (systemLocale.language) {
            "ja" -> "ja"
            "zh" -> "zh"
            else -> "en"
        }
    }
    
    /**
     * 言語コードからロケールを取得
     */
    fun getLocale(language: String): Locale {
        return when (language) {
            "ja" -> Locale.JAPANESE
            "zh" -> Locale.CHINESE
            else -> Locale.ENGLISH
        }
    }
    
    /**
     * カスタム文字列リソースを取得
     */
    fun getString(resourceName: String): String {
        val language = getCurrentLanguage()
        return getCustomString(language, resourceName) ?: getFallbackString(resourceName)
    }
    
    /**
     * カスタム文字列リソースを取得（言語指定）
     */
    private fun getCustomString(language: String, resourceName: String): String? {
        return try {
            // languages/ ディレクトリから直接リソースを読み込む
            val resourceId = getResourceId(resourceName)
            if (resourceId != 0) {
                context.resources.getString(resourceId)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * リソースIDを取得
     */
    private fun getResourceId(resourceName: String): Int {
        return try {
            val packageName = context.packageName
            context.resources.getIdentifier(resourceName, "string", packageName)
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * フォールバック文字列
     */
    private fun getFallbackString(resourceName: String): String {
        return when (resourceName) {
            "app_title" -> "ICPeek"
            "instruction_nfc" -> "Hold IC card near device"
            "history_title" -> "Transaction History"
            "copy_button" -> "Copy"
            "export_csv_button" -> "CSV"
            "balance_label" -> "Balance"
            "status_ready" -> "Ready to read"
            "status_reading" -> "Processing IC card..."
            "status_success" -> "IC card read successfully"
            "status_failed" -> "Failed to read balance"
            "status_not_felica" -> "Not a FeliCa card"
            "no_history" -> "No transaction history found"
            "copy_success" -> "Transaction history copied"
            "copy_no_history" -> "No history to copy"
            "export_success" -> "CSV file created"
            "export_no_history" -> "No history to export"
            "export_failed" -> "CSV export failed"
            "share_history" -> "Share transaction history"
            "detail_title" -> "Transaction Details"
            "back_button" -> "Back"
            "detail_info" -> "Detailed Information"
            "raw_data" -> "Raw Data"
            "tap_to_copy" -> "Tap to copy"
            "mock_data_toast" -> "Mock data loaded!"
            "debug_logs" -> "Debug Logs"
            "debug_logs_placeholder" -> "Debug logs will appear here..."
            "csv_subject" -> "IC Card Transaction History"
            "csv_description" -> "IC card transaction history data"
            "csv_filename_prefix" -> "IC_Card_History"
            else -> resourceName
        }
    }
}
