package com.example.admindashboard.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.admindashboard.R
import com.example.admindashboard.models.Customer
import com.example.admindashboard.utils.DataStore
import java.text.SimpleDateFormat
import java.util.*

class CustomersAdapter(
    private var items: List<Customer>,
    private val onMarkPaid: (Customer) -> Unit,
    private val onEdit: (Customer) -> Unit,
    private val onDelete: (Customer) -> Unit
) : RecyclerView.Adapter<CustomersAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView   = view.findViewById(R.id.tvAvatar)
        val tvName: TextView     = view.findViewById(R.id.tvName)
        val tvPhone: TextView    = view.findViewById(R.id.tvPhone)
        val tvDueBadge: TextView = view.findViewById(R.id.tvDueBadge)
        val tvBill: TextView     = view.findViewById(R.id.tvBill)
        val tvPaid: TextView     = view.findViewById(R.id.tvPaid)
        val tvBalance: TextView  = view.findViewById(R.id.tvBalance)
        val tvDueDate: TextView  = view.findViewById(R.id.tvDueDate)
        val tvNote: TextView     = view.findViewById(R.id.tvNote)
        val btnMarkPaid: Button  = view.findViewById(R.id.btnMarkPaid)
        val btnEdit: Button      = view.findViewById(R.id.btnEdit)
        val btnDelete: Button    = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c   = items[position]
        val ctx = h.itemView.context

        h.tvAvatar.text = DataStore.initials(c.name)
        h.tvName.text   = c.name
        h.tvPhone.text  = c.phone
        h.tvBill.text   = DataStore.formatRupees(c.bill)
        h.tvPaid.text   = DataStore.formatRupees(c.paid)

        val balance = c.balance
        h.tvBalance.text = DataStore.formatRupees(balance)
        h.tvBalance.setTextColor(
            ContextCompat.getColor(ctx, if (balance > 0) R.color.delta_down else R.color.delta_up)
        )

        // Due date + overdue check
        val fmt = SimpleDateFormat("dd MMM yy", Locale.getDefault())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        if (c.due.isNotEmpty()) {
            val dueDate = runCatching { sdf.parse(c.due) }.getOrNull()
            val isOverdue = dueDate != null && dueDate.before(Date()) && balance > 0
            h.tvDueDate.text = if (dueDate != null) fmt.format(dueDate) else c.due
            if (isOverdue) h.tvDueDate.setTextColor(ContextCompat.getColor(ctx, R.color.delta_down))
            else h.tvDueDate.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        } else {
            h.tvDueDate.text = "—"
        }

        // Badge
        if (c.isPaid) {
            h.tvDueBadge.text = "✓ Paid"
            h.tvDueBadge.setTextColor(ContextCompat.getColor(ctx, R.color.paid_text))
            h.tvDueBadge.setBackgroundResource(R.drawable.bg_paid_btn)
        } else {
            h.tvDueBadge.text = "Due ${DataStore.formatRupees(balance)}"
            h.tvDueBadge.setTextColor(ContextCompat.getColor(ctx, R.color.danger_text))
            h.tvDueBadge.setBackgroundResource(R.drawable.bg_btn_danger)
        }

        // Note
        if (c.note.isNotEmpty()) {
            h.tvNote.visibility = View.VISIBLE
            h.tvNote.text = c.note
        } else {
            h.tvNote.visibility = View.GONE
        }

        h.btnMarkPaid.setOnClickListener  { onMarkPaid(c) }
        h.btnEdit.setOnClickListener      { onEdit(c) }
        h.btnDelete.setOnClickListener    { onDelete(c) }
    }

    fun update(newList: List<Customer>) {
        items = newList
        notifyDataSetChanged()
    }
}
