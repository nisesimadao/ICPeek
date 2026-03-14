package com.icpeek.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.icpeek.app.R
import com.icpeek.app.model.TransactionInfo

/**
 * 取引リスト用のRecyclerViewアダプター
 */
class TransactionAdapter(
    private var transactions: List<TransactionInfo>,
    private val onItemClick: (TransactionInfo) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    /**
     * ViewHolderクラス
     */
    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.transactionDateTextView)
        val balanceTextView: TextView = itemView.findViewById(R.id.transactionBalanceTextView)
        val typeTextView: TextView = itemView.findViewById(R.id.transactionTypeTextView)
        val detailsTextView: TextView = itemView.findViewById(R.id.transactionDetailsTextView)
        val amountChangeTextView: TextView = itemView.findViewById(R.id.transactionAmountChangeTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions.getOrNull(position)
        
        if (transaction != null) {
            holder.dateTextView.text = transaction.getFormattedDate()
            holder.balanceTextView.text = transaction.getFormattedBalance()
            holder.typeTextView.text = transaction.getDisplayInfo()
            holder.detailsTextView.text = "連番: ${transaction.sequence}"

            val amountChange = transaction.getFormattedAmountChange()
            if (amountChange.isNotEmpty()) {
                holder.amountChangeTextView.text = amountChange
                holder.amountChangeTextView.setTextColor(transaction.getAmountChangeColor())
                holder.amountChangeTextView.visibility = View.VISIBLE
            } else {
                holder.amountChangeTextView.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onItemClick(transaction)
            }
        } else {
            // 空の状態（検索結果なし）
            holder.dateTextView.text = "検索結果がありません"
            holder.balanceTextView.text = ""
            holder.typeTextView.text = ""
            holder.detailsTextView.text = ""
            holder.amountChangeTextView.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int {
        return if (transactions.isEmpty()) 1 else transactions.size
    }

    /**
     * 取引リストを更新
     */
    fun updateTransactions(newTransactions: List<TransactionInfo>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    /**
     * 現在の取引リストを取得
     */
    fun getTransactions(): List<TransactionInfo> {
        return transactions
    }
}
