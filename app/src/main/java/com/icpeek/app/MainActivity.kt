package com.icpeek.app

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Bundle
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.icpeek.app.model.TransactionInfo
import com.icpeek.app.nfc.NFCReader
import com.icpeek.app.parser.TransactionParser
import com.icpeek.app.util.CardTypeDetector

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback, NFCReader.TransactionCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var balanceTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var cardTypeTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private var nfcReader: NFCReader? = null
    private var transactionParser: TransactionParser? = null
    
    // API level check for ReaderMode (API 29+)
    private val isReaderModeSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    
    override fun onTagDiscovered(tag: Tag) {
        android.util.Log.d("MainActivity", "Tag discovered with reader mode: ${tag.javaClass.simpleName}")
        addLog("=== NFC Tag Discovered (Reader Mode) ===")
        addLog("Tag technologies: ${tag.techList.joinToString(", ")}")
        
        processNFCTag(tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        balanceTextView = findViewById(R.id.balanceTextView)
        statusTextView = findViewById(R.id.statusTextView)
        cardTypeTextView = findViewById(R.id.cardTypeTextView)
        logTextView = findViewById(R.id.logTextView)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        
        // Setup RecyclerView
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Check NFC availability
        if (nfcAdapter == null) {
            statusTextView.text = "NFC is not available on this device"
            addLog("ERROR: NFC not available on this device")
            return
        }
        
        addLog("NFC adapter initialized successfully")
        
        nfcReader = NFCReader(this)
        transactionParser = TransactionParser()
        
        // Set transaction callback
        nfcReader?.setTransactionCallback(this)

        // Handle NFC intent
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume called (API ${Build.VERSION.SDK_INT})")
        
        nfcAdapter?.let { adapter ->
            if (isReaderModeSupported) {
                android.util.Log.d("MainActivity", "Using enableReaderMode (API 29+)")
                try {
                    val flags = NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                    adapter.enableReaderMode(
                        this,           // Activity
                        this,           // Callback
                        flags,          // Flags
                        null             // Bundle options
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to enable reader mode", e)
                    fallbackToForegroundDispatch(adapter)
                }
            } else {
                android.util.Log.d("MainActivity", "Using enableForegroundDispatch (API < 29)")
                fallbackToForegroundDispatch(adapter)
            }
        } ?: run {
            android.util.Log.e("MainActivity", "NFC adapter is null")
        }
    }
    
    private fun fallbackToForegroundDispatch(adapter: NfcAdapter) {
        nfcReader?.let { reader ->
            android.util.Log.d("MainActivity", "Enabling foreground dispatch")
            adapter.enableForegroundDispatch(this, reader.pendingIntent, reader.intentFiltersArray, reader.techListsArray)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { adapter ->
            if (isReaderModeSupported) {
                adapter.disableReaderMode(this)
            } else {
                adapter.disableForegroundDispatch(this)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("MainActivity", "onNewIntent called with action: ${intent.action}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent) {
        addLog("=== Intent Received ===")
        addLog("Action: ${intent.action}")
        addLog("Extras: ${intent.extras?.keySet()?.joinToString(", ")}")
        
        // Always try to get the tag regardless of action
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            addLog("Tag found: ${tag.javaClass.simpleName}")
            addLog("Tag technologies: ${tag.techList.joinToString(", ")}")
            processNFCTag(tag)
        } else {
            addLog("No tag found in intent - checking all extras")
            intent.extras?.keySet()?.forEach { key ->
                addLog("Extra $key: ${intent.extras?.get(key)}")
            }
        }
        
        addLog("=== End Intent Processing ===")
    }

    private fun processNFCTag(tag: Tag) {
        addLog("=== NFC Tag Detected ===")
        addLog("Tag technologies: ${tag.techList.joinToString(", ")}")
        
        runOnUiThread {
            statusTextView.text = "ICカードを処理中..."
        }
        
        try {
            val nfcF = NfcF.get(tag)
            if (nfcF != null) {
                addLog("FeliCa technology found")
                addLog("System code: ${nfcF.systemCode.toHexString()}")
                addLog("Manufacturer: ${nfcF.manufacturer.toHexString()}")
                
                // Detect card type
                val cardType = CardTypeDetector.detectCardType(
                    nfcF.systemCode.toHexString(), 
                    nfcF.manufacturer.toHexString()
                )
                addLog("Card type detected: $cardType")
                
                runOnUiThread {
                    cardTypeTextView.text = cardType
                    cardTypeTextView.setTextColor(android.graphics.Color.parseColor(CardTypeDetector.getCardColor(cardType)))
                }
                
                nfcF.connect()
                addLog("FeliCa connected")
                
                val balance = nfcReader?.readBalance(nfcF) ?: -1
                
                runOnUiThread {
                    if (balance >= 0) {
                        balanceTextView.text = "Balance: ¥$balance"
                        statusTextView.text = "IC card read successfully"
                        addLog("SUCCESS: Balance read successfully: ¥$balance")
                    } else {
                        balanceTextView.text = "Balance: --"
                        statusTextView.text = "Failed to read balance"
                        addLog("ERROR: Failed to read balance")
                    }
                }
                
                // Close connection after all operations are done
                nfcF.close()
                addLog("FeliCa disconnected")
            } else {
                runOnUiThread {
                    statusTextView.text = "Not a FeliCa card"
                }
                addLog("ERROR: Not a FeliCa card")
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusTextView.text = "Error: ${e.message}"
                balanceTextView.text = "Balance: --"
            }
            addLog("ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        
        addLog("=== End NFC Processing ===")
    }
    
    private fun readTransactionHistory(nfcF: NfcF) {
        try {
            addLog("Reading transaction history...")
            
            // Get IDm from polling response
            val pollingCommand = nfcReader?.felicaServiceInstance?.createPollingCommand()
            if (pollingCommand != null) {
                val pollingResponse = nfcF.transceive(pollingCommand)
                if (pollingResponse != null && pollingResponse.size >= 12) {
                    val idm = ByteArray(8)
                    System.arraycopy(pollingResponse, 2, idm, 0, 8)
                    
                    // Read 10 history blocks
                    val historyCommand = createHistoryReadCommand(idm, 10)
                    val historyResponse = nfcF.transceive(historyCommand)
                    
                    if (historyResponse != null && historyResponse.size >= 13) {
                        parseAndDisplayHistory(historyResponse)
                    }
                }
            }
        } catch (e: Exception) {
            addLog("ERROR reading history: ${e.message}")
        }
    }
    
    private fun createHistoryReadCommand(idm: ByteArray, blockCount: Int): ByteArray {
        val command = java.io.ByteArrayOutputStream(100)
        
        command.write(0)                    // data length. change after all byte set.
        command.write(0x06)                 // Felica command, Read Without Encryption
        command.write(idm)                  // NFC ID (8byte)
        
        command.write(1)                    // service code length (2byte)
        command.write(0x0F)                 // low byte of service code (little endian)
        command.write(0x09)                 // high byte of service code (little endian)
        command.write(blockCount)           // number of blocks
        
        for (i in 0 until blockCount) {
            command.write(0x80)             // ブロックエレメント上位バイト
            command.write(i)                // ブロック番号
        }
        
        val result = command.toByteArray()
        result[0] = (result.size).toByte()  // 先頭１バイトはデータ長
        return result
    }
    
    private fun parseAndDisplayHistory(response: ByteArray) {
        try {
            val blockCount = response[12].toInt() and 0xFF
            addLog("Found $blockCount transaction blocks")
            
            val transactions = mutableListOf<TransactionInfo>()
            
            for (i in 0 until blockCount) {
                val blockOffset = 13 + i * 16
                if (response.size >= blockOffset + 16) {
                    val transaction = transactionParser?.parseTransaction(response, blockOffset, i)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                }
            }
            
            // Display transactions on UI
            runOnUiThread {
                displayTransactions(transactions)
            }
            
        } catch (e: Exception) {
            addLog("ERROR parsing history: ${e.message}")
        }
    }
    
    private fun displayTransactions(transactions: List<TransactionInfo>) {
        val transactionViews = mutableListOf<View>()
        
        if (transactions.isEmpty()) {
            val noHistoryView = TextView(this)
            noHistoryView.text = "取引履歴が見つかりません"
            noHistoryView.textSize = 14f
            noHistoryView.setTextColor(0xFF666666.toInt())
            noHistoryView.setPadding(16, 16, 16, 16)
            transactionViews.add(noHistoryView)
        } else {
            val inflater = LayoutInflater.from(this)
            
            for (transaction in transactions) {
                val transactionView = inflater.inflate(R.layout.transaction_item, null, false)
                
                // Set transaction data
                transactionView.findViewById<TextView>(R.id.transactionDateTextView).text = transaction.getFormattedDate()
                transactionView.findViewById<TextView>(R.id.transactionBalanceTextView).text = transaction.getFormattedBalance()
                transactionView.findViewById<TextView>(R.id.transactionTypeTextView).text = transaction.getDisplayInfo()
                transactionView.findViewById<TextView>(R.id.transactionDetailsTextView).text = 
                    "残高: ${transaction.getFormattedBalance()} | 連番: ${transaction.sequence}"
                
                // Set amount change with color
                val amountChangeTextView = transactionView.findViewById<TextView>(R.id.transactionAmountChangeTextView)
                val amountChange = transaction.getFormattedAmountChange()
                if (amountChange.isNotEmpty()) {
                    amountChangeTextView.text = amountChange
                    amountChangeTextView.setTextColor(transaction.getAmountChangeColor())
                    amountChangeTextView.visibility = View.VISIBLE
                } else {
                    amountChangeTextView.visibility = View.GONE
                }
                
                // Set click listener for transaction detail
                transactionView.setOnClickListener {
                    openTransactionDetail(transaction)
                }
                
                transactionViews.add(transactionView)
            }
        }
        
        // Create a simple adapter for the RecyclerView
        historyRecyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val inflater = LayoutInflater.from(this@MainActivity)
                val view = inflater.inflate(R.layout.transaction_item, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                // Update the view with transaction data
                val transaction = transactions.getOrNull(position)
                if (transaction != null) {
                    holder.itemView.findViewById<TextView>(R.id.transactionDateTextView).text = transaction.getFormattedDate()
                    holder.itemView.findViewById<TextView>(R.id.transactionBalanceTextView).text = transaction.getFormattedBalance()
                    holder.itemView.findViewById<TextView>(R.id.transactionTypeTextView).text = transaction.getDisplayInfo()
                    holder.itemView.findViewById<TextView>(R.id.transactionDetailsTextView).text = 
                        "残高: ${transaction.getFormattedBalance()} | 連番: ${transaction.sequence}"
                    
                    val amountChangeTextView = holder.itemView.findViewById<TextView>(R.id.transactionAmountChangeTextView)
                    val amountChange = transaction.getFormattedAmountChange()
                    if (amountChange.isNotEmpty()) {
                        amountChangeTextView.text = amountChange
                        amountChangeTextView.setTextColor(transaction.getAmountChangeColor())
                        amountChangeTextView.visibility = View.VISIBLE
                    } else {
                        amountChangeTextView.visibility = View.GONE
                    }
                    
                    holder.itemView.setOnClickListener {
                        openTransactionDetail(transaction)
                    }
                } else if (transactions.isEmpty()) {
                    // Show "no history" message
                    holder.itemView.findViewById<TextView>(R.id.transactionDateTextView).text = "取引履歴が見つかりません"
                    holder.itemView.findViewById<TextView>(R.id.transactionBalanceTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionTypeTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionDetailsTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionAmountChangeTextView).visibility = View.GONE
                }
            }
            
            override fun getItemCount(): Int {
                return if (transactions.isEmpty()) 1 else transactions.size
            }
        }
        
        addLog("Displayed ${transactions.size} transactions in UI")
    }
    
    override fun onTransactionsReceived(transactions: List<TransactionInfo>) {
        runOnUiThread {
            displayTransactions(transactions)
        }
        addLog("Received ${transactions.size} transactions from NFCReader")
    }
    
    private fun openTransactionDetail(transaction: TransactionInfo) {
        val intent = android.content.Intent(this, TransactionDetailActivity::class.java)
        intent.putExtra("TRANSACTION", transaction)
        startActivity(intent)
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        
        android.util.Log.d("ICPeek", logEntry)
        
        runOnUiThread {
            val currentLogs = logTextView.text.toString()
            val newLogs = if (currentLogs == "Debug logs will appear here...") {
                logEntry
            } else {
                "$currentLogs\n$logEntry"
            }
            
            // Keep only last 50 log lines to avoid memory issues
            val logLines = newLogs.split("\n")
            val limitedLogs = if (logLines.size > 50) {
                logLines.takeLast(50).joinToString("\n")
            } else {
                newLogs
            }
            
            logTextView.text = limitedLogs
        }
    }
    
    private fun ByteArray.toHexString(): String {
        return this.joinToString("") { "%02X".format(it) }
    }
}
