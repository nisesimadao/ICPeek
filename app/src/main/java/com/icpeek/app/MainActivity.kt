package com.icpeek.app

import android.os.Build
import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.nfc.NdefMessage
import android.nfc.NdefRecord
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
import com.icpeek.app.R
import com.icpeek.app.model.TransactionInfo
import com.icpeek.app.nfc.NFCReader
import com.icpeek.app.parser.TransactionParser
import com.icpeek.app.util.CardTypeDetector
import com.icpeek.app.TransactionFilter
import com.icpeek.app.FilterType
import java.io.File
import java.io.FileWriter
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback, NFCReader.TransactionCallback {

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
    private var nfcReader: NFCReader? = null
    private var transactionParser: TransactionParser? = null
    private var currentTransactions: List<TransactionInfo> = emptyList()
    private var filteredTransactions: List<TransactionInfo> = emptyList()
    private var currentFilter = TransactionFilter(filterType = FilterType.CHARGE)
    
    // Easter egg for mock data
    private var titleTapCount = 0
    private var lastTitleTapTime = 0L
    
    // NFC Intent handling for API < 29
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
        
        nfcReader = NFCReader(this)
        transactionParser = TransactionParser()
        
        // Set transaction callback
        nfcReader?.setTransactionCallback(this)
        
        // Initialize NFC Intent handling for API < 29
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
        android.util.Log.d("MainActivity", "Enabling foreground dispatch")
        adapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
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
            // 読み取り中のフィードバックを追加
            instructionTextView.text = languageManager.getString("instruction_reading")
            progressBar.visibility = View.VISIBLE
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
                        statusTextView.text = languageManager.getString("status_success")
                        addLog("SUCCESS: Balance read successfully: ¥$balance")
                    } else {
                        balanceTextView.text = "Balance: --"
                        statusTextView.text = languageManager.getString("status_failed")
                        addLog("ERROR: Failed to read balance")
                    }
                }
                
                // Close connection after all operations are done
                nfcF.close()
                addLog("FeliCa disconnected")
                
                // 読み取り完了後にUIをリセット
                runOnUiThread {
                    instructionTextView.text = "ICカードを端末に近づけてください"
                    progressBar.visibility = View.GONE
                }
            } else {
                runOnUiThread {
                    statusTextView.text = languageManager.getString("status_not_felica")
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
        currentTransactions = transactions
        addLog("Displaying ${transactions.size} transactions")
        
        // 初回表示時のみフィルターを適用
        if (filteredTransactions.isEmpty()) {
            filteredTransactions = transactions
        }
        
        historyRecyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val inflater = LayoutInflater.from(this@MainActivity)
                val view = inflater.inflate(R.layout.transaction_item, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val transaction = filteredTransactions.getOrNull(position)
                if (transaction != null) {
                    holder.itemView.findViewById<TextView>(R.id.transactionDateTextView).text = transaction.getFormattedDate()
                    holder.itemView.findViewById<TextView>(R.id.transactionBalanceTextView).text = transaction.getFormattedBalance()
                    holder.itemView.findViewById<TextView>(R.id.transactionTypeTextView).text = transaction.getDisplayInfo()
                    holder.itemView.findViewById<TextView>(R.id.transactionDetailsTextView).text = 
                        "連番: ${transaction.sequence}"
                    
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
                } else if (filteredTransactions.isEmpty()) {
                    // 検索結果なしメッセージ
                    holder.itemView.findViewById<TextView>(R.id.transactionDateTextView).text = languageManager.getString("no_search_results")
                    holder.itemView.findViewById<TextView>(R.id.transactionBalanceTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionTypeTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionDetailsTextView).text = ""
                    holder.itemView.findViewById<TextView>(R.id.transactionAmountChangeTextView).visibility = View.GONE
                }
            }
            
            override fun getItemCount(): Int {
                return if (filteredTransactions.isEmpty()) 1 else filteredTransactions.size
            }
        }
        
        addLog("Displayed ${filteredTransactions.size} filtered transactions in UI")
    }
    
    private fun copyHistoryToClipboard() {
        addLog("copyHistoryToClipboard called, currentTransactions size: ${currentTransactions.size}")
        
        if (currentTransactions.isEmpty()) {
            Toast.makeText(this, languageManager.getString("copy_no_history"), Toast.LENGTH_SHORT).show()
            addLog("No transactions to copy")
            return
        }
        
        val historyText = buildString {
            appendLine("=== ICカード取引履歴 ===")
            appendLine("カードタイプ: ${cardTypeTextView.text}")
            appendLine("現在残高: ${balanceTextView.text}")
            appendLine("取引件数: ${currentTransactions.size}件")
            appendLine()
            
            currentTransactions.forEachIndexed { index, transaction ->
                appendLine("【取引 ${index + 1}】")
                appendLine("日付: ${transaction.getFormattedDate()}")
                appendLine("種別: ${transaction.getDisplayInfo()}")
                appendLine("残高: ${transaction.getFormattedBalance()}")
                appendLine("増減: ${transaction.getFormattedAmountChange()}")
                appendLine("連番: ${transaction.sequence}")
                appendLine("端末ID: ${transaction.terminalId}")
                appendLine("処理ID: ${transaction.processId}")
                appendLine("前回残高: ${if (transaction.previousBalance > 0) "¥${transaction.previousBalance}" else "なし"}")
                appendLine("生差分: ${if (transaction.previousBalance > 0) transaction.balance - transaction.previousBalance else "なし"}")
                appendLine("入線区: ${transaction.inLine}")
                appendLine("入駅: ${transaction.inStation}")
                appendLine("出線区: ${transaction.outLine}")
                appendLine("出駅: ${transaction.outStation}")
                appendLine("リージョン: ${transaction.region}")
                appendLine()
            }
            
            appendLine("=== Rawデータ ===")
            currentTransactions.forEach { transaction ->
                appendLine("${transaction.getFormattedDate()}: ${transaction.rawData}")
            }
        }
        
        // LogCatに出力
        Log.d("ICPeek_CopyHistory", "=== コピーした取引履歴 ===")
        Log.d("ICPeek_CopyHistory", historyText)
        
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ICカード取引履歴", historyText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "取引履歴をコピーしました", Toast.LENGTH_SHORT).show()
        addLog("Copied ${currentTransactions.size} transactions to clipboard")
        addLog("Copy content length: ${historyText.length} characters")
    }
    
    private fun exportHistoryToCsv() {
        addLog("exportHistoryToCsv called, currentTransactions size: ${currentTransactions.size}")
        
        if (currentTransactions.isEmpty()) {
            Toast.makeText(this, languageManager.getString("export_no_history"), Toast.LENGTH_SHORT).show()
            addLog("No transactions to export")
            return
        }
        
        try {
            // Generate CSV content
            val csvContent = generateCsvContent()
            
            // Create file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ICカード取引履歴_${timestamp}.csv"
            val csvFile = File(getExternalFilesDir(null), fileName)
            
            // Write to file
            FileWriter(csvFile).use { writer ->
                writer.write(csvContent)
            }
            
            // Share file
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                csvFile
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ICカード取引履歴")
                putExtra(Intent.EXTRA_TEXT, "ICカードの取引履歴データ")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, languageManager.getString("share_history")))
            
            Toast.makeText(this, languageManager.getString("export_success"), Toast.LENGTH_SHORT).show()
            addLog("CSV exported: $fileName (${csvContent.length} characters)")
            
        } catch (e: Exception) {
            Toast.makeText(this, languageManager.getString("export_failed"), Toast.LENGTH_SHORT).show()
            addLog("ERROR exporting CSV: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun generateCsvContent(): String {
        val csv = StringBuilder()
        
        // CSV header
        csv.appendLine("日付,種別,残高,増減,連番,端末ID,処理ID,入線区,入駅,出線区,出駅,リージョン,Rawデータ")
        
        // CSV data
        currentTransactions.forEach { transaction ->
            csv.appendLine("${transaction.getFormattedDate()}," +
                    "${transaction.getDisplayInfo()}," +
                    "${transaction.balance}," +
                    "${transaction.getFormattedAmountChange()}," +
                    "${transaction.sequence}," +
                    "${transaction.terminalId}," +
                    "${transaction.processId}," +
                    "${transaction.inLine}," +
                    "${transaction.inStation}," +
                    "${transaction.outLine}," +
                    "${transaction.outStation}," +
                    "${transaction.region}," +
                    "\"${transaction.rawData}\"")
        }
        
        return csv.toString()
    }
    
    private fun setRecyclerViewHeight() {
        val displayMetrics = resources.displayMetrics
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
        
        // Calculate appropriate height based on screen size
        // Reserve space for other UI elements (status bar, app bar, other cards, etc.)
        val reservedHeightDp = 400 // Approximate space for other elements
        val availableHeightDp = screenHeightDp - reservedHeightDp
        
        // Set minimum and maximum bounds
        val recyclerViewHeightDp = when {
            availableHeightDp < 300 -> 300 // Minimum height
            availableHeightDp > 600 -> 600 // Maximum height  
            else -> availableHeightDp
        }
        
        // Convert to pixels and set height
        val recyclerViewHeightPx = (recyclerViewHeightDp.toFloat() * displayMetrics.density).toInt()
        val layoutParams = historyRecyclerView.layoutParams
        layoutParams.height = recyclerViewHeightPx
        historyRecyclerView.layoutParams = layoutParams
        
        addLog("RecyclerView height set to ${recyclerViewHeightDp}dp (${recyclerViewHeightPx}px) based on screen height ${screenHeightDp}dp")
    }
    
    private fun handleTitleTap() {
        val currentTime = System.currentTimeMillis()
        
        // Reset tap count if more than 2 seconds between taps
        if (currentTime - lastTitleTapTime > 2000) {
            titleTapCount = 0
        }
        
        titleTapCount++
        lastTitleTapTime = currentTime
        
        if (titleTapCount >= 3) {
            loadMockData()
            titleTapCount = 0
        }
    }
    
    private fun loadMockData() {
        addLog("=== Loading mock data (Easter egg) ===")
        
        val mockTransactions = listOf(
            TransactionInfo(
                terminalId = 199, terminalName = "物販端末", processId = 70, processName = "物販",
                year = 2025, month = 9, day = 14,
                inLine = 15, inStation = 229, outLine = 0, outStation = 0,
                balance = 3705, sequence = 1572864, region = 192,
                transactionType = "物販", rawData = "050F000F332E0FE50000790E000018C0",
                previousBalance = -1
            ),
            TransactionInfo(
                terminalId = 22, terminalName = "改札機", processId = 1, processName = "運賃支払(改札出場)",
                year = 2025, month = 9, day = 14,
                inLine = 10, inStation = 73, outLine = 10, outStation = 62,
                balance = 3025, sequence = 1703936, region = 0,
                transactionType = "JR", rawData = "16010002332E0A490A3ED10B00001A00",
                previousBalance = 3705
            ),
            TransactionInfo(
                terminalId = 199, terminalName = "物販端末", processId = 70, processName = "物販",
                year = 2025, month = 9, day = 27,
                inLine = 167, inStation = 44, outLine = 177, outStation = 101,
                balance = 2485, sequence = 1769472, region = 0,
                transactionType = "物販", rawData = "C7460000333BA72CB165B50900001B00",
                previousBalance = 3025
            ),
            TransactionInfo(
                terminalId = 201, terminalName = "Unknown (201)", processId = 70, processName = "物販",
                year = 2025, month = 10, day = 8,
                inLine = 175, inStation = 77, outLine = 55, outStation = 72,
                balance = 1912, sequence = 1835008, region = 0,
                transactionType = "物販", rawData = "C94600003348AF4D3748780700001C00",
                previousBalance = 2485
            ),
            TransactionInfo(
                terminalId = 22, terminalName = "改札機", processId = 1, processName = "運賃支払(改札出場)",
                year = 2025, month = 11, day = 2,
                inLine = 10, inStation = 62, outLine = 10, outStation = 57,
                balance = 2582, sequence = 2031616, region = 0,
                transactionType = "JR", rawData = "1601000233620A3E0A39160A00001F00",
                previousBalance = 1912
            ),
            TransactionInfo(
                terminalId = 8, terminalName = "券売機", processId = 2, processName = "チャージ",
                year = 2025, month = 11, day = 2,
                inLine = 10, inStation = 62, outLine = 0, outStation = 0,
                balance = 2912, sequence = 1900544, region = 0,
                transactionType = "チャージ", rawData = "0802000033620A3E0000600B00001D00",
                previousBalance = 2582
            ),
            TransactionInfo(
                terminalId = 200, terminalName = "自販機", processId = 70, processName = "物販",
                year = 2025, month = 11, day = 15,
                inLine = 93, inStation = 164, outLine = 112, outStation = 153,
                balance = 1532, sequence = 2097152, region = 0,
                transactionType = "物販", rawData = "C8460000336F5DA47099FC0500002000",
                previousBalance = 2912
            ),
            TransactionInfo(
                terminalId = 201, terminalName = "Unknown (201)", processId = 70, processName = "物販",
                year = 2025, month = 11, day = 19,
                inLine = 177, inStation = 45, outLine = 55, outStation = 71,
                balance = 459, sequence = 2162688, region = 0,
                transactionType = "物販", rawData = "C94600003373B12D3747CB0100002100",
                previousBalance = 1532
            ),
            TransactionInfo(
                terminalId = 8, terminalName = "券売機", processId = 2, processName = "チャージ",
                year = 2025, month = 11, day = 29,
                inLine = 10, inStation = 62, outLine = 0, outStation = 0,
                balance = 1459, sequence = 2228224, region = 0,
                transactionType = "チャージ", rawData = "08020000337D0A3E0000B30500002200",
                previousBalance = 459
            ),
            TransactionInfo(
                terminalId = 199, terminalName = "物販端末", processId = 70, processName = "物販",
                year = 2025, month = 11, day = 29,
                inLine = 111, inStation = 44, outLine = 177, outStation = 101,
                balance = 831, sequence = 2293760, region = 0,
                transactionType = "物販", rawData = "C7460000337D6F2CB1653F0300002300",
                previousBalance = 1459
            )
        )
        
        // Update UI with mock data
        balanceTextView.text = "¥831"
        statusTextView.text = "Mock data loaded"
        cardTypeTextView.text = "ICOCA"
        cardTypeTextView.setBackgroundColor(0xFF4CAF50.toInt())
        cardTypeTextView.setTextColor(0xFFFFFFFF.toInt())
        
        displayTransactions(mockTransactions)
        
        Toast.makeText(this, languageManager.getString("mock_data_toast"), Toast.LENGTH_SHORT).show()
        addLog("Mock data loaded: ${mockTransactions.size} transactions")
    }
    
    /**
     * 検索機能のセットアップ
     */
    private fun setupSearchFunctionality() {
        // テキスト入力のリスナー
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentFilter = currentFilter.copy(searchText = s?.toString() ?: "")
                applyFilter()
            }
        })
        
        // フィルターボタンのリスナー
        filterTypeButton.setOnClickListener {
            showFilterTypeDialog()
        }
        
        filterDateButton.setOnClickListener {
            showDateFilterDialog()
        }
        
        filterAmountButton.setOnClickListener {
            showAmountFilterDialog()
        }
    }
    
    /**
     * フィルターを適用
     */
    private fun applyFilter() {
        filteredTransactions = if (currentFilter.isActive()) {
            currentTransactions.filter { it.matchesFilter(currentFilter) }
        } else {
            currentTransactions
        }
        
        updateTransactionDisplay()
    }
    
    /**
     * 取引表示を更新
     */
    private fun updateTransactionDisplay() {
        displayTransactions(filteredTransactions)
    }
    
    /**
     * 検索セクションの展開・折りたたみを設定
     */
    private fun setupSearchExpandCollapse() {
        searchHeader.setOnClickListener {
            toggleSearchExpansion()
        }
    }
    
    /**
     * 検索セクションの展開・折りたたみを切り替え
     */
    private fun toggleSearchExpansion() {
        isSearchExpanded = !isSearchExpanded
        
        if (isSearchExpanded) {
            // 展開アニメーション
            searchContent.visibility = View.VISIBLE
            searchExpandIcon.setImageResource(R.drawable.ic_expand_less)
            
            // スムーズな展開アニメーション
            searchContent.alpha = 0f
            searchContent.translationY = -20f
            searchContent.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
        } else {
            // 折りたたみアニメーション
            searchExpandIcon.setImageResource(R.drawable.ic_expand_more)
            
            // スムーズな折りたたみアニメーション
            searchContent.animate()
                .alpha(0f)
                .translationY(-20f)
                .setDuration(200)
                .withEndAction {
                    searchContent.visibility = View.GONE
                }
                .start()
        }
    }
    
    /**
     * フィルター種別ダイアログを表示
     */
    private fun showFilterTypeDialog() {
        val types = arrayOf(
            languageManager.getString("filter_all"),
            languageManager.getString("filter_charge"),
            languageManager.getString("filter_purchase"),
            languageManager.getString("filter_transit")
        )
        
        val filterTypes = arrayOf(
            FilterType.ALL,
            FilterType.CHARGE,
            FilterType.PURCHASE,
            FilterType.TRANSIT
        )
        
        val currentIndex = filterTypes.indexOf(currentFilter.filterType)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(languageManager.getString("filter_type"))
            .setSingleChoiceItems(types, currentIndex) { dialog, which ->
                currentFilter = currentFilter.copy(filterType = filterTypes[which])
                applyFilter()
                updateFilterButtonTexts()
            }
            .setNegativeButton(languageManager.getString("clear_search")) { dialog, _ ->
                currentFilter = TransactionFilter()
                applyFilter()
                updateFilterButtonTexts()
                searchEditText.text?.clear()
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * 日付フィルターダイアログを表示
     */
    private fun showDateFilterDialog() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        
        // 開始日ダイアログ
        val startDateDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val startDateCalendar = Calendar.getInstance()
                startDateCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                startDateCalendar.set(Calendar.MILLISECOND, 0)
                val startTime = startDateCalendar.timeInMillis
                
                // 終了日ダイアログ
                val endDateDialog = android.app.DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDayOfMonth ->
                        val endDateCalendar = Calendar.getInstance()
                        endDateCalendar.set(endYear, endMonth, endDayOfMonth, 23, 59, 59)
                        endDateCalendar.set(Calendar.MILLISECOND, 999)
                        val endTime = endDateCalendar.timeInMillis
                        
                        currentFilter = currentFilter.copy(
                            startDate = startTime,
                            endDate = endTime
                        )
                        applyFilter()
                        updateFilterButtonTexts()
                    },
                    currentYear,
                    currentMonth,
                    currentDay
                )
                endDateDialog.setTitle("終了日を選択")
                endDateDialog.show()
            },
            currentYear,
            currentMonth,
            currentDay
        )
        startDateDialog.setTitle("開始日を選択")
        startDateDialog.show()
    }
    
    /**
     * 金額フィルターダイアログを表示
     */
    private fun showAmountFilterDialog() {
        val context = this
        val dialogView = layoutInflater.inflate(R.layout.dialog_amount_filter, null)
        val minAmountEditText = dialogView.findViewById<android.widget.EditText>(R.id.minAmountEditText)
        val maxAmountEditText = dialogView.findViewById<android.widget.EditText>(R.id.maxAmountEditText)
        
        // 現在の値を設定
        currentFilter.minAmount?.let { minAmountEditText.setText(it.toString()) }
        currentFilter.maxAmount?.let { maxAmountEditText.setText(it.toString()) }
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("金額範囲を設定")
            .setView(dialogView)
            .setPositiveButton("適用") { dialog, _ ->
                val minAmount = minAmountEditText.text.toString().toIntOrNull()
                val maxAmount = maxAmountEditText.text.toString().toIntOrNull()
                
                currentFilter = currentFilter.copy(
                    minAmount = minAmount,
                    maxAmount = maxAmount
                )
                applyFilter()
                updateFilterButtonTexts()
                dialog.dismiss()
            }
            .setNegativeButton("キャンセル") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("クリア") { dialog, _ ->
                currentFilter = currentFilter.copy(
                    minAmount = null,
                    maxAmount = null
                )
                applyFilter()
                updateFilterButtonTexts()
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * フィルターボタンのテキストを更新
     */
    private fun updateFilterButtonTexts() {
        // 種別ボタン
        filterTypeButton.text = when (currentFilter.filterType) {
            FilterType.ALL -> languageManager.getString("filter_all")
            FilterType.CHARGE -> languageManager.getString("filter_charge")
            FilterType.PURCHASE -> languageManager.getString("filter_purchase")
            FilterType.TRANSIT -> languageManager.getString("filter_transit")
        }
        
        // 日付ボタン
        filterDateButton.text = if (currentFilter.startDate != null || currentFilter.endDate != null) {
            "日付✓"
        } else {
            languageManager.getString("filter_date")
        }
        
        // 金額ボタン
        filterAmountButton.text = if (currentFilter.minAmount != null || currentFilter.maxAmount != null) {
            "金額✓"
        } else {
            languageManager.getString("filter_amount")
        }
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
