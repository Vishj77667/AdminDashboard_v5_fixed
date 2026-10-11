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
import com.example.admindashboard.models.Task
import java.text.SimpleDateFormat
import java.util.*

class TasksAdapter(
    private var items: List<Task>,
    private val onToggle: (Task) -> Unit,
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TasksAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCheck: TextView       = view.findViewById(R.id.tvCheck)
        val tvTitle: TextView       = view.findViewById(R.id.tvTitle)
        val tvDesc: TextView        = view.findViewById(R.id.tvDesc)
        val tvPriorityTag: TextView = view.findViewById(R.id.tvPriorityTag)
        val tvCategoryTag: TextView = view.findViewById(R.id.tvCategoryTag)
        val tvAlarmInfo: TextView   = view.findViewById(R.id.tvAlarmInfo)
        val btnEdit: Button         = view.findViewById(R.id.btnEdit)
        val btnDelete: Button       = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t   = items[position]
        val ctx = h.itemView.context

        // Done state
        h.tvCheck.setBackgroundResource(
            if (t.done) R.drawable.bg_task_check_done else R.drawable.bg_task_check
        )
        h.tvCheck.text = if (t.done) "✓" else ""
        h.tvCheck.setTextColor(ContextCompat.getColor(ctx, R.color.bg_primary))

        h.tvTitle.text = t.title
        if (t.done) {
            h.tvTitle.paintFlags = h.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            h.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
        } else {
            h.tvTitle.paintFlags = h.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            h.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }

        // Description
        if (t.desc.isNotEmpty()) {
            h.tvDesc.visibility = View.VISIBLE
            h.tvDesc.text = t.desc
        } else {
            h.tvDesc.visibility = View.GONE
        }

        // Priority tag
        val (prioBg, prioText) = when (t.priority) {
            "high"   -> R.drawable.bg_tag_high   to R.color.tag_high_text
            "low"    -> R.drawable.bg_tag_low     to R.color.tag_low_text
            else     -> R.drawable.bg_tag_medium  to R.color.tag_medium_text
        }
        h.tvPriorityTag.setBackgroundResource(prioBg)
        h.tvPriorityTag.setTextColor(ContextCompat.getColor(ctx, prioText))
        h.tvPriorityTag.text = t.priority.replaceFirstChar { it.uppercase() }

        // Category tag
        val (catBg, catText) = when (t.category) {
            "personal" -> R.drawable.bg_tag_personal to R.color.tag_personal_text
            "other"    -> R.drawable.bg_tag_other     to R.color.tag_other_text
            else       -> R.drawable.bg_tag_work      to R.color.tag_work_text
        }
        h.tvCategoryTag.setBackgroundResource(catBg)
        h.tvCategoryTag.setTextColor(ContextCompat.getColor(ctx, catText))
        h.tvCategoryTag.text = t.category.replaceFirstChar { it.uppercase() }

        // Alarm info
        if (t.date.isNotEmpty() && t.time.isNotEmpty()) {
            h.tvAlarmInfo.visibility = View.VISIBLE
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val alarm = runCatching { sdf.parse("${t.date}T${t.time}") }.getOrNull()
            val isOverdue = alarm != null && alarm.before(Date()) && !t.done
            val repeatStr = if (t.repeat) " · daily" else ""
            val overdueStr = if (isOverdue) " · OVERDUE" else ""
            h.tvAlarmInfo.text = "⏰ ${t.date} ${t.time}$repeatStr$overdueStr"
            h.tvAlarmInfo.setTextColor(
                ContextCompat.getColor(ctx, if (isOverdue) R.color.danger_text else R.color.text_tertiary)
            )
        } else {
            h.tvAlarmInfo.visibility = View.GONE
        }

        h.tvCheck.setOnClickListener   { onToggle(t) }
        h.btnEdit.setOnClickListener   { onEdit(t) }
        h.btnDelete.setOnClickListener { onDelete(t) }
    }

    fun update(newList: List<Task>) {
        items = newList
        notifyDataSetChanged()
    }
}
