package com.example.admindashboard.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.admindashboard.R
import com.example.admindashboard.adapters.LogAdapter
import com.example.admindashboard.databinding.FragmentAdminBinding
import com.example.admindashboard.utils.DataStore
import java.text.SimpleDateFormat
import java.util.*

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private lateinit var logAdapter: LogAdapter
    private var pendingExportType: String = "all"

    // Launcher for restoring data
    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            binding.tvFileName.text = it.lastPathSegment ?: "backup.json"
            handleRestore(it)
        }
    }

    // Launcher for exporting data
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            performExport(it, pendingExportType)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLogList()
        setupButtons()
        refreshSummary()
        refreshLog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private fun setupLogList() {
        logAdapter = LogAdapter(emptyList())
        binding.rvLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLog.adapter = logAdapter
    }

    private fun setupButtons() {
        // Export
        binding.btnExportAll.setOnClickListener       { initiateExport("all") }
        binding.btnExportCustomers.setOnClickListener { initiateExport("customers") }
        binding.btnExportProducts.setOnClickListener  { initiateExport("products") }

        // Restore
        binding.btnChooseFile.setOnClickListener {
            restoreLauncher.launch("application/json")
        }

        // Danger zone
        binding.btnClearCustomers.setOnClickListener { confirmClear("customers") }
        binding.btnClearProducts.setOnClickListener  { confirmClear("products") }
        binding.btnClearTasks.setOnClickListener     { confirmClear("tasks") }
        binding.btnClearAll.setOnClickListener       { confirmClear("all") }
    }

    // ── Summary ───────────────────────────────────────────────────────────

    private fun refreshSummary() {
        binding.tvProductCount.text  = DataStore.products.size.toString()
        binding.tvCustomerCount.text = DataStore.customers.size.toString()
        val due     = DataStore.customers.sumOf { it.balance }
        val pending = DataStore.tasks.count { !it.done }
        binding.tvTotalDue.text      = DataStore.formatRupees(due)
        binding.tvTasksPending.text  = pending.toString()
        binding.tvTotalDue.setTextColor(
            requireContext().getColor(if (due > 0) R.color.delta_down else R.color.delta_up)
        )
        binding.tvTasksPending.setTextColor(
            requireContext().getColor(if (pending > 0) R.color.stock_low else R.color.delta_up)
        )
    }

    private fun refreshLog() {
        val logs = DataStore.backupLog
        logAdapter.update(logs)
        binding.tvNoLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvLog.visibility   = if (logs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun addLog(action: String, detail: String) {
        val now = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        DataStore.backupLog.add(0, DataStore.LogEntry(action, detail, now))
        DataStore.save()
        refreshLog()
        refreshSummary()
    }

    // ── Export ────────────────────────────────────────────────────────────

    private fun initiateExport(type: String) {
        pendingExportType = type
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "backup_${type}_$dateStr.json"
        exportLauncher.launch(fileName)
    }

    private fun performExport(uri: Uri, type: String) {
        try {
            val json = DataStore.getBackupJson(type)
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray())
            }
            addLog("Exported", type)
            toast(getString(R.string.backup_downloaded))
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────

    private fun handleRestore(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("Restore Backup")
            .setMessage("This will REPLACE your current data. Are you sure?")
            .setPositiveButton("Restore") { _, _ ->
                try {
                    val text = requireContext().contentResolver.openInputStream(uri)?.use { isr ->
                        isr.bufferedReader().readText()
                    } ?: throw Exception("Could not read file")

                    if (DataStore.restoreFromJson(text)) {
                        val name = uri.lastPathSegment ?: "backup.json"
                        addLog("Restored", name)
                        toast(getString(R.string.data_restored))
                    } else {
                        throw Exception("Invalid backup format")
                    }
                } catch (e: Exception) {
                    toast("${getString(R.string.invalid_backup)}: ${e.message}")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Danger zone ───────────────────────────────────────────────────────

    private fun confirmClear(type: String) {
        val label = when (type) {
            "all"       -> "ALL data"
            "customers" -> "all customers"
            "products"  -> "all products"
            else        -> "all tasks"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("⚠ Danger")
            .setMessage("Permanently delete $label? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                if (type == "all" || type == "products") {
                    DataStore.products.clear(); DataStore.nextProductId = 1
                }
                if (type == "all" || type == "customers") {
                    DataStore.customers.clear(); DataStore.nextCustomerId = 1
                }
                if (type == "all" || type == "tasks") {
                    DataStore.tasks.clear(); DataStore.nextTaskId = 1
                }
                DataStore.save()
                addLog("Cleared", label)
                toast("$label cleared")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
