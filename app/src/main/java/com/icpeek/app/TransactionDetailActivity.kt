package com.icpeek.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.icpeek.app.R
import com.icpeek.shared.model.TransactionInfo

class TransactionDetailActivity : AppCompatActivity() {
    
    private lateinit var transaction: TransactionInfo
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)
        
        // Get transaction from intent
        transaction = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("TRANSACTION", TransactionInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("TRANSACTION")
            }
        } catch (e: Exception) {
            android.util.Log.e("TransactionDetailActivity", "Error getting transaction from intent", e)
            finish()
            return
        } ?: run {
            android.util.Log.e("TransactionDetailActivity", "No transaction provided in intent")
            finish()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        // Set transaction details
        findViewById<TextView>(R.id.transactionDateTextView).text = transaction.getFormattedDate()
        findViewById<TextView>(R.id.transactionTypeTextView).text = transaction.getDisplayInfo()
        findViewById<TextView>(R.id.transactionBalanceTextView).text = "残高: ${transaction.getFormattedBalance()}"
        
        // Set amount change with color
        val amountChangeTextView = findViewById<TextView>(R.id.transactionAmountChangeTextView)
        val amountChange = transaction.getFormattedAmountChange()
        if (amountChange.isNotEmpty()) {
            amountChangeTextView.text = amountChange
            amountChangeTextView.setTextColor(transaction.getAmountChangeColor())
        } else {
            amountChangeTextView.visibility = android.view.View.GONE
        }
        
        // Set detailed information
        val details = buildString {
            appendLine("連番: ${transaction.sequence}")
            appendLine("リージョン: ${transaction.region}")
            appendLine("入線区: ${transaction.inLine}")
            appendLine("入駅: ${transaction.inStation}")
            appendLine("出線区: ${transaction.outLine}")
            appendLine("出駅: ${transaction.outStation}")
            appendLine("端末ID: ${transaction.terminalId}")
            appendLine("処理ID: ${transaction.processId}")
        }
        findViewById<TextView>(R.id.transactionDetailsTextView).text = details
        
        // Set raw data
        findViewById<TextView>(R.id.rawDataTextView).text = transaction.rawData
        
        // Set up back button
        findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }
        
        // Set title
        title = "取引詳細 - ${transaction.getFormattedDate()}"
    }
}
