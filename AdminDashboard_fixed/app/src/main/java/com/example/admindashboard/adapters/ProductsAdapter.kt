package com.example.admindashboard.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.admindashboard.R
import com.example.admindashboard.models.Product
import com.example.admindashboard.utils.DataStore

class ProductsAdapter(
    private var items: List<Product>,
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : RecyclerView.Adapter<ProductsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmoji: ImageView      = view.findViewById(R.id.tvEmoji)
        val tvName: TextView        = view.findViewById(R.id.tvName)
        val tvCategory: TextView    = view.findViewById(R.id.tvCategory)
        val tvStars: TextView       = view.findViewById(R.id.tvStars)
        val tvRatingSales: TextView = view.findViewById(R.id.tvRatingSales)
        val tvPrice: TextView       = view.findViewById(R.id.tvPrice)
        val tvStock: TextView       = view.findViewById(R.id.tvStock)
        val btnEdit: Button         = view.findViewById(R.id.btnEdit)
        val btnDelete: Button       = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val p   = items[position]
        val ctx = h.itemView.context

        h.tvName.text        = p.name
        h.tvCategory.text    = p.category.replaceFirstChar { it.uppercase() }
        h.tvStars.text       = DataStore.stars(p.rating)
        h.tvRatingSales.text = "${p.rating} · ${p.sales} sales"
        h.tvPrice.text       = DataStore.formatRupees(p.price)

        if (p.stock < 20) {
            h.tvStock.text      = "Low stock: ${p.stock}"
            h.tvStock.setTextColor(ContextCompat.getColor(ctx, R.color.stock_low))
        } else {
            h.tvStock.text      = "In stock: ${p.stock}"
            h.tvStock.setTextColor(ContextCompat.getColor(ctx, R.color.stock_ok))
        }

        h.btnEdit.setOnClickListener   { onEdit(p) }
        h.btnDelete.setOnClickListener { onDelete(p) }
    }

    fun update(newList: List<Product>) {
        items = newList
        notifyDataSetChanged()
    }
}
