package com.example.admindashboard.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.admindashboard.R
import com.example.admindashboard.utils.DataStore

class LogAdapter(
    private var items: List<DataStore.LogEntry>
) : RecyclerView.Adapter<LogAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvEntry: TextView = view.findViewById(R.id.tvLogEntry)
        val tvTime: TextView  = view.findViewById(R.id.tvLogTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = items[position]
        h.tvEntry.text = "${e.action} — ${e.detail}"
        h.tvTime.text  = e.time
    }

    fun update(newList: List<DataStore.LogEntry>) {
        items = newList
        notifyDataSetChanged()
    }
}
