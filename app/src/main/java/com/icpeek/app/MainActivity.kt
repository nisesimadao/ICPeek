package com.icpeek.app

import android.os.Build
import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.icpeek.app.R
import com.icpeek.app.TransactionFilter
import com.icpeek.app.adapter.TransactionAdapter
import com.icpeek.shared.platform.NFCReader
import com.icpeek.shared.model.TransactionInfo
import com.icpeek.shared.model.CardInfo
import java.io.File
import java.io.FileWriter
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var balanceTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var cardTypeTextView: TextView
    private lateinit var instructionTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var logTextView: TextView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var copyHistoryButton: Button
    private lateinit var exportCsvButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var filterDateButton: Button
    private lateinit var filterAmountButton: Button
    private lateinit var filterTypeButton: Button
    private lateinit var searchHeader: LinearLayout
    private lateinit var searchContent: LinearLayout
    private lateinit var searchExpandIcon: ImageView
    private var isSearchExpanded = false
    private lateinit var transactionAdapter: TransactionAdapter
    private var nfcReader: NFCReader? = null
    private var currentTransactions: List<TransactionInfo> = emptyList()
    private var filteredTransactions: List<TransactionInfo> = emptyList()
    private var currentFilter = TransactionFilter(filterType = FilterType.CHARGE)
    
    // Easter egg for mock data
    private var titleTapCount = 0
    private var lastTitleTapTime = 0L
    
    // NFC Intent handling (kept for compatibility)
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private lateinit var techListsArray: Array<Array<String>>
    
    // API level check for ReaderMode (API 29+)
    private val isReaderModeSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    
    override fun onTagDiscovered(tag: Tag) {
        android.util.Log.d("MainActivity", "Tag discovered with reader mode: ${tag.javaClass.simpleName}")
        addLog("=== NFC Tag Discovered (Reader Mode) ===")
        addLog("Tag technologies: ${tag.techList.joinToString(", ")}")
        
        processNFCTag(tag)
    }

    private lateinit var languageManager: LanguageManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Language Manager
        languageManager = LanguageManager(this)
        
        // Initialize UI components
        balanceTextView = findViewById(R.id.balanceTextView)
        statusTextView = findViewById(R.id.statusTextView)
        cardTypeTextView = findViewById(R.id.cardTypeTextView)
        instructionTextView = findViewById(R.id.instructionTextView)
        progressBar = findViewById(R.id.progressBar)
        logTextView = findViewById(R.id.logTextView)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        copyHistoryButton = findViewById(R.id.copyHistoryButton)
        exportCsvButton = findViewById(R.id.exportCsvButton)
        titleTextView = findViewById(R.id.titleTextView)
        searchEditText = findViewById(R.id.searchEditText)
        filterDateButton = findViewById(R.id.filterDateButton)
        filterAmountButton = findViewById(R.id.filterAmountButton)
        filterTypeButton = findViewById(R.id.filterTypeButton)
        searchHeader = findViewById(R.id.searchHeader)
        searchContent = findViewById(R.id.searchContent)
        searchExpandIcon = findViewById(R.id.searchExpandIcon)
        
        // Setup RecyclerView
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(emptyList()) { transaction ->
            openTransactionDetail(transaction)
        }
        historyRecyclerView.adapter = transactionAdapter
        
        // Set dynamic height for RecyclerView based on screen size
        setRecyclerViewHeight()
        
        // Setup copy history button
        copyHistoryButton.setOnClickListener {
            copyHistoryToClipboard()
        }
        
        // Setup CSV export button
        exportCsvButton.setOnClickListener {
            exportHistoryToCsv()
        }
        
        // Setup search functionality
        setupSearchFunctionality()
        
        // Setup search expand/collapse
        setupSearchExpandCollapse()
        
        // Setup Easter egg for mock data
        titleTextView.setOnClickListener {
            handleTitleTap()
        }
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Check NFC availability
        if (nfcAdapter == null) {
            statusTextView.text = languageManager.getString("status_ready")
            addLog("NFC not available on this device")
            return
        }
        
        addLog("NFC adapter initialized successfully")
        
        // Initialize shared NFC reader
        nfcReader = NFCReader(this)
        
        // Initialize NFC Intent handling (kept for compatibility)
        setupNFCIntentHandling()

        // Handle NFC intent
        handleIntent(intent)
    }
    
    private fun setupNFCIntentHandling() {
        // Create PendingIntent for NFC intent
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0)
        
        // Create Intent filters for NFC
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("fail", e)
            }
        }
        
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        
        intentFiltersArray = arrayOf(ndef, tech, tag)
        
        // NFC technology list
        techListsArray = arrayOf(arrayOf("android.nfc.tech.NfcF"))
    }

    override fun onResume() {
        super.onResume()
        
        if (nfcAdapter != null) {
            if (isReaderModeSupported) {
                // Enable ReaderMode for API 29+
                nfcAdapter?.enableReaderMode(
                    this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
                addLog("NFC ReaderMode enabled (API 29+)")
            } else {
                // Fallback to intent-based NFC for older APIs
                enableNFCIntentDispatch()
                addLog("NFC Intent dispatch enabled (API < 29)")
            }
        }
        
        statusTextView.text = languageManager.getString("status_ready")
    }
    
    override fun onPause() {
        super.onPause()
        
        if (nfcAdapter != null) {
            if (isReaderModeSupported) {
                nfcAdapter?.disableReaderMode(this)
                addLog("NFC ReaderMode disabled")
            } else {
                disableNFCIntentDispatch()
                addLog("NFC Intent dispatch disabled")
            }
        }
    }
    
    private fun enableNFCIntentDispatch() {
        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFiltersArray,
            techListsArray
        )
    }
    
    private fun disableNFCIntentDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        val action = intent.action
        addLog("Intent received: $action")
        
        if (action == NfcAdapter.ACTION_TECH_DISCOVERED || 
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            
            tag?.let {
                addLog("Processing tag from intent...")
                processNFCTag(it)
            }
        }
    }
    
    private fun processNFCTag(tag: Tag) {
        lifecycleScope.launch {
            try {
                runOnUiThread {
                    statusTextView.text = languageManager.getString("status_reading")
                    progressBar.visibility = View.VISIBLE
                    addLog("Starting card reading...")
                }
                
                // Use shared NFC reader
                val cardInfo = nfcReader?.readCardFromTag(tag)
                
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    
                    if (cardInfo != null) {
                        displayCardInfo(cardInfo)
                        addLog("Card successfully read!")
                    } else {
                        statusTextView.text = languageManager.getString("status_error")
                        addLog("Failed to read card")
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusTextView.text = languageManager.getString("status_error")
                    addLog("Error reading card: ${e.message}")
                }
            }
        }
    }
    
    private fun displayCardInfo(cardInfo: CardInfo) {
        // Display card type
        cardTypeTextView.text = cardInfo.cardType
        cardTypeTextView.setTextColor(getCardTypeColor(cardInfo.cardType))
        
        // Display balance
        balanceTextView.text = cardInfo.balance.getFormattedBalance()
        
        // Display transactions
        currentTransactions = cardInfo.transactions
        filteredTransactions = currentTransactions
        updateTransactionDisplay()
        
        statusTextView.text = languageManager.getString("status_success")
        addLog("Card Type: ${cardInfo.cardType}")
        addLog("Balance: ¥${cardInfo.balance}")
        addLog("Transactions: ${cardInfo.transactions.size}")
    }
    
    private fun getCardTypeColor(cardType: String): Int {
        return when {
            cardType.contains("Suica") || cardType.contains("PASMO") -> 0xFF4CAF50.toInt()
            cardType.contains("ICOCA") -> 0xFF2196F3.toInt()
            cardType.contains("Edy") -> 0xFFFF9800.toInt()
            cardType.contains("WAON") -> 0xFFE91E63.toInt()
            cardType.contains("nanaco") -> 0xFF9C27B0.toInt()
            else -> 0xFF757575.toInt()
        }
    }
    
    private fun Int.getFormattedBalance(): String {
        return "¥$this"
    }
    
    private fun updateTransactionDisplay() {
        transactionAdapter.updateTransactions(filteredTransactions)
    }
    
    private fun openTransactionDetail(transaction: TransactionInfo) {
        val intent = Intent(this, TransactionDetailActivity::class.java).apply {
            putExtra("transaction", transaction)
        }
        startActivity(intent)
    }
    
    private fun setRecyclerViewHeight() {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val maxHeight = (screenHeight * 0.4).toInt()
        
        historyRecyclerView.layoutParams = historyRecyclerView.layoutParams.apply {
            height = maxHeight
        }
    }
    
    private fun copyHistoryToClipboard() {
        if (currentTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to copy", Toast.LENGTH_SHORT).show()
            return
        }
        
        val historyText = buildString {
            appendLine("=== IC Card Transaction History ===")
            appendLine("Card Type: ${cardTypeTextView.text}")
            appendLine("Current Balance: ${balanceTextView.text}")
            appendLine()
            
            currentTransactions.forEachIndexed { index, transaction ->
                appendLine("${index + 1}. ${transaction.getFormattedDate()} - ${transaction.processName}")
                appendLine("   Amount: ${transaction.getFormattedAmountChange()}")
                appendLine("   Balance: ${transaction.getFormattedBalance()}")
                appendLine("   Terminal: ${transaction.terminalName}")
                appendLine()
            }
        }
        
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IC Card History", historyText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "Transaction history copied to clipboard", Toast.LENGTH_SHORT).show()
        addLog("Transaction history copied to clipboard")
    }
    
    private fun exportHistoryToCsv() {
        if (currentTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val fileName = "icpeek_transactions_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            
            FileWriter(file).use { writer ->
                // Write CSV header
                writer.appendLine("Date,Process,Terminal,Amount Change,Balance,Raw Data")
                
                // Write transactions
                currentTransactions.forEach { transaction ->
                    writer.appendLine(
                        "${transaction.getFormattedDate()},${transaction.processName},${transaction.terminalName},${transaction.getFormattedAmountChange()},${transaction.getFormattedBalance()},${transaction.rawData}"
                    )
                }
            }
            
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "IC Card Transaction History")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Transaction History"))
            addLog("Transaction history exported to CSV: $fileName")
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export transactions", Toast.LENGTH_SHORT).show()
            addLog("Failed to export transactions: ${e.message}")
        }
    }
    
    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTransactions(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        filterDateButton.setOnClickListener { /* TODO: Implement date filter */ }
        filterAmountButton.setOnClickListener { /* TODO: Implement amount filter */ }
        filterTypeButton.setOnClickListener { /* TODO: Implement type filter */ }
    }
    
    private fun filterTransactions(searchText: String) {
        filteredTransactions = if (searchText.isEmpty()) {
            currentTransactions
        } else {
            currentTransactions.filter { transaction ->
                transaction.processName.contains(searchText, ignoreCase = true) ||
                transaction.terminalName.contains(searchText, ignoreCase = true) ||
                transaction.transactionType.contains(searchText, ignoreCase = true)
            }
        }
        updateTransactionDisplay()
    }
    
    private fun setupSearchExpandCollapse() {
        searchHeader.setOnClickListener {
            isSearchExpanded = !isSearchExpanded
            searchContent.visibility = if (isSearchExpanded) View.VISIBLE else View.GONE
            searchExpandIcon.rotation = if (isSearchExpanded) 180f else 0f
        }
    }
    
    private fun handleTitleTap() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTitleTapTime < 500) {
            titleTapCount++
            if (titleTapCount >= 5) {
                loadMockData()
                titleTapCount = 0
            }
        } else {
            titleTapCount = 1
        }
        lastTitleTapTime = currentTime
    }
    
    private fun loadMockData() {
        // Mock data for testing
        val mockTransactions = listOf(
            TransactionInfo(
                terminalId = 199,
                terminalName = "物販端末",
                processId = 70,
                processName = "物販",
                year = 2024,
                month = 3,
                day = 16,
                inLine = 0x00,
                inStation = 0x00,
                outLine = 0x00,
                outStation = 0x00,
                balance = 1230,
                sequence = 12345,
                region = 0x00,
                transactionType = "物販",
                rawData = "MOCK_DATA_001"
            ),
            TransactionInfo(
                terminalId = 9,
                terminalName = "入金機",
                processId = 2,
                processName = "チャージ",
                year = 2024,
                month = 3,
                day = 15,
                inLine = 0x00,
                inStation = 0x00,
                outLine = 0x00,
                outStation = 0x00,
                balance = 2000,
                sequence = 12344,
                region = 0x00,
                transactionType = "チャージ",
                rawData = "MOCK_DATA_002",
                previousBalance = 1230
            )
        )
        
        currentTransactions = mockTransactions
        filteredTransactions = mockTransactions
        
        cardTypeTextView.text = "Suica"
        cardTypeTextView.setTextColor(0xFF4CAF50.toInt())
        balanceTextView.text = "¥1230"
        
        updateTransactionDisplay()
        
        statusTextView.text = languageManager.getString("status_success")
        addLog("Mock data loaded (5 title taps)")
    }
    
    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message"
        
        runOnUiThread {
            val currentLog = logTextView.text.toString()
            logTextView.text = if (currentLog.isEmpty()) {
                logMessage
            } else {
                "$currentLog\n$logMessage"
            }
        }
        
        Log.d("MainActivity", message)
    }
}
