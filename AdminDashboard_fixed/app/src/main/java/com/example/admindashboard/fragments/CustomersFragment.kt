package com.example.admindashboard.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.admindashboard.R
import com.example.admindashboard.adapters.CustomersAdapter
import com.example.admindashboard.databinding.FragmentCustomersBinding
import com.example.admindashboard.models.Customer
import com.example.admindashboard.utils.DataStore
import java.text.SimpleDateFormat
import java.util.*

class CustomersFragment : Fragment() {

    private var _binding: FragmentCustomersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CustomersAdapter
    private var activeFilter = "all"

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        binding.btnAdd.setOnClickListener { showCustomerDialog(null) }
        refreshStats()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = CustomersAdapter(
            emptyList(),
            onMarkPaid = { markPaid(it) },
            onEdit     = { showCustomerDialog(it) },
            onDelete   = { confirmDelete(it) }
        )
        binding.rvCustomers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCustomers.adapter = adapter
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
            binding.chipAll     to "all",
            binding.chipDue     to "due",
            binding.chipCleared to "paid"
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
        val sdf   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Date()

        val list = DataStore.customers.filter { c ->
            val matchesSearch  = c.name.lowercase().contains(query) || c.phone.contains(query)
            val matchesFilter  = when (activeFilter) {
                "due"  -> c.balance > 0
                "paid" -> c.balance <= 0
                else   -> true
            }
            matchesSearch && matchesFilter
        }

        adapter.update(list)
        binding.rvCustomers.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility     = if (list.isEmpty()) View.VISIBLE else View.GONE
        refreshStats()
    }

    private fun refreshStats() {
        val sdf     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today   = Date()
        val total   = DataStore.customers.size
        val due     = DataStore.customers.sumOf { it.balance }
        val overdue = DataStore.customers.count { c ->
            c.balance > 0 && c.due.isNotEmpty() &&
            runCatching { sdf.parse(c.due)?.before(today) == true }.getOrDefault(false)
        }
        binding.tvTotalCustomers.text = total.toString()
        binding.tvTotalDue.text       = DataStore.formatRupees(due)
        binding.tvOverdue.text        = overdue.toString()
        binding.tvTotalDue.setTextColor(
            requireContext().getColor(if (due > 0) R.color.delta_down else R.color.delta_up)
        )
    }

    // ── Mark Paid ─────────────────────────────────────────────────────────

    private fun markPaid(c: Customer) {
        if (c.isPaid) { toast("Already fully paid"); return }
        AlertDialog.Builder(requireContext())
            .setTitle("Mark as Paid")
            .setMessage("Mark ${c.name} as fully paid?\nBalance: ${DataStore.formatRupees(c.balance)}")
            .setPositiveButton("Mark Paid") { _, _ ->
                c.paid = c.bill
                DataStore.save()
                toast("${c.name} marked as paid")
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────

    private fun showCustomerDialog(existing: Customer?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_customer, null)

        val tvTitle   = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etName    = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone   = dialogView.findViewById<EditText>(R.id.etPhone)
        val etBill    = dialogView.findViewById<EditText>(R.id.etBill)
        val etPaid    = dialogView.findViewById<EditText>(R.id.etPaid)
        val btnDue    = dialogView.findViewById<Button>(R.id.btnDueDate)
        val etNote    = dialogView.findViewById<EditText>(R.id.etNote)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave   = dialogView.findViewById<Button>(R.id.btnSave)

        var selectedDue = existing?.due ?: ""
        val displaySdf  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val storageSdf  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun updateDueBtn() {
            btnDue.text = if (selectedDue.isEmpty()) "Select date"
            else runCatching {
                displaySdf.format(storageSdf.parse(selectedDue)!!)
            }.getOrDefault(selectedDue)
        }

        if (existing != null) {
            tvTitle.text = getString(R.string.edit_customer)
            etName.setText(existing.name)
            etPhone.setText(existing.phone)
            etBill.setText(existing.bill.toString())
            etPaid.setText(existing.paid.toString())
            etNote.setText(existing.note)
        }
        updateDueBtn()

        btnDue.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d) }
                selectedDue = storageSdf.format(c.time)
                updateDueBtn()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val name  = etName.text.toString().trim()
            if (name.isEmpty()) { etName.error = "Required"; return@setOnClickListener }
            val phone = etPhone.text.toString().trim()
            val bill  = etBill.text.toString().toIntOrNull() ?: 0
            val paid  = etPaid.text.toString().toIntOrNull() ?: 0
            val note  = etNote.text.toString().trim()

            if (existing != null) {
                existing.name  = name; existing.phone = phone
                existing.bill  = bill; existing.paid  = paid
                existing.due   = selectedDue; existing.note = note
            } else {
                DataStore.customers.add(
                    Customer(DataStore.nextCustomerId++, name, phone, bill, paid, selectedDue, note)
                )
            }
            toast(getString(R.string.customer_saved))
            DataStore.save()
            dialog.dismiss()
            render()
        }
        dialog.show()
    }

    private fun confirmDelete(c: Customer) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage("Delete \"${c.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                DataStore.customers.remove(c)
                DataStore.save()
                toast(getString(R.string.customer_deleted))
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
