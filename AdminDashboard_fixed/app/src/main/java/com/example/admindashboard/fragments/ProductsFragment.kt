package com.example.admindashboard.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.admindashboard.R
import com.example.admindashboard.adapters.ProductsAdapter
import com.example.admindashboard.databinding.FragmentProductsBinding
import com.example.admindashboard.models.Product
import com.example.admindashboard.utils.DataStore

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductsAdapter
    private var activeFilter = "all"

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        binding.btnAdd.setOnClickListener { showAddProductDialog(null) }
        refreshStats()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = ProductsAdapter(
            emptyList(),
            onEdit   = { showAddProductDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = render()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupFilterChips() {
        val chips = mapOf(
            binding.chipAll      to "all",
            binding.chipPlans    to "plans",
            binding.chipAddons   to "addons",
            binding.chipHardware to "hardware"
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener {
                activeFilter = filter
                chips.keys.forEach { c ->
                    c.setBackgroundResource(if (c == chip) R.drawable.bg_chip_active else 0)
                    c.setTextColor(
                        requireContext().getColor(
                            if (c == chip) R.color.text_primary else R.color.text_secondary
                        )
                    )
                }
                render()
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────

    private fun render() {
        val query = binding.etSearch.text.toString().lowercase()
        val list  = DataStore.products.filter { p ->
            (activeFilter == "all" || p.category == activeFilter) &&
            (p.name.lowercase().contains(query) || p.category.contains(query))
        }
        adapter.update(list)
        binding.rvProducts.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility    = if (list.isEmpty()) View.VISIBLE else View.GONE
        refreshStats()
    }

    private fun refreshStats() {
        val total    = DataStore.products.size
        val inStock  = DataStore.products.count { it.stock >= 20 }
        val lowStock = DataStore.products.count { it.stock in 1..19 }
        binding.tvTotalValue.text   = total.toString()
        binding.tvInStockValue.text = inStock.toString()
        binding.tvLowStockValue.text = lowStock.toString()
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────

    private fun showAddProductDialog(existing: Product?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_product, null)

        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etName     = dialogView.findViewById<EditText>(R.id.etName)
        val etPrice    = dialogView.findViewById<EditText>(R.id.etPrice)
        val spinCat    = dialogView.findViewById<Spinner>(R.id.spinCategory)
        val etStock    = dialogView.findViewById<EditText>(R.id.etStock)
        val etRating   = dialogView.findViewById<EditText>(R.id.etRating)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnSave)

        val categories = listOf("plans", "addons", "hardware")
        spinCat.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, categories
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        if (existing != null) {
            tvTitle.text = getString(R.string.edit_product)
            etName.setText(existing.name)
            etPrice.setText(existing.price.toString())
            spinCat.setSelection(categories.indexOf(existing.category).coerceAtLeast(0))
            etStock.setText(existing.stock.toString())
            etRating.setText(existing.rating.toString())
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name   = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "Required"; return@setOnClickListener }
            val price  = etPrice.text.toString().toIntOrNull() ?: 0
            val cat    = categories[spinCat.selectedItemPosition]
            val stock  = etStock.text.toString().toIntOrNull() ?: 0
            val rating = etRating.text.toString().toDoubleOrNull()?.coerceIn(1.0, 5.0) ?: 4.0

            if (existing != null) {
                existing.name     = name
                existing.price    = price
                existing.category = cat
                existing.stock    = stock
                existing.rating   = rating
                toast(getString(R.string.product_updated))
                DataStore.save()
            } else {
                DataStore.products.add(0,
                    Product(DataStore.nextProductId++, name, cat, price, stock, rating)
                )
                toast(getString(R.string.product_added))
                DataStore.save()
            }
            dialog.dismiss()
            render()
        }
        dialog.show()
    }

    private fun confirmDelete(p: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage("Delete \"${p.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                DataStore.products.remove(p)
                toast(getString(R.string.product_deleted))
                DataStore.save()
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
