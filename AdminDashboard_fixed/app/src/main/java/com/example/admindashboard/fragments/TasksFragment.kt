package com.example.admindashboard.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.admindashboard.R
import com.example.admindashboard.adapters.TasksAdapter
import com.example.admindashboard.databinding.FragmentTasksBinding
import com.example.admindashboard.models.Task
import com.example.admindashboard.utils.DataStore
import java.text.SimpleDateFormat
import java.util.*

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TasksAdapter
    private var activeFilter = "all"

    // Alarm state
    private val handler  = Handler(Looper.getMainLooper())
    private val snoozed  = mutableMapOf<Int, Long>()   // taskId → snoozeUntil millis
    private var ringingId: Int? = null
    private var mediaPlayer: MediaPlayer? = null
    private var alarmDialog: AlertDialog? = null

    private val alarmRunnable = object : Runnable {
        override fun run() {
            checkAlarms()
            handler.postDelayed(this, 10_000)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilterChips()
        binding.btnAdd.setOnClickListener { showTaskDialog(null) }
        refreshStats()
        render()
        handler.post(alarmRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(alarmRunnable)
        stopAlarmSound()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = TasksAdapter(
            emptyList(),
            onToggle = { toggleDone(it) },
            onEdit   = { showTaskDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = adapter
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
            binding.chipPending to "pending",
            binding.chipDone    to "done",
            binding.chipHigh    to "high"
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
        val list  = DataStore.tasks.filter { t ->
            val matchesSearch = t.title.lowercase().contains(query) ||
                                t.desc.lowercase().contains(query)
            val matchesFilter = when (activeFilter) {
                "pending" -> !t.done
                "done"    -> t.done
                "high"    -> t.priority == "high"
                else      -> true
            }
            matchesSearch && matchesFilter
        }
        adapter.update(list)
        binding.rvTasks.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        refreshStats()
    }

    private fun refreshStats() {
        val all     = DataStore.tasks.size
        val pending = DataStore.tasks.count { !it.done }
        val done    = DataStore.tasks.count { it.done }
        val high    = DataStore.tasks.count { it.priority == "high" && !it.done }
        binding.tvTotal.text   = all.toString()
        binding.tvPending.text = pending.toString()
        binding.tvDone.text    = done.toString()
        binding.tvHigh.text    = high.toString()
    }

    // ── Toggle done ───────────────────────────────────────────────────────

    private fun toggleDone(t: Task) {
        t.done = !t.done
        DataStore.save()
        render()
    }

    // ── Alarm engine ──────────────────────────────────────────────────────

    private fun checkAlarms() {
        if (ringingId != null) return
        val sdf  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val now  = Date()
        DataStore.tasks.forEach { t ->
            if (t.done || t.date.isEmpty() || t.time.isEmpty()) return@forEach
            if (ringingId == t.id) return@forEach
            val snoozeUntil = snoozed[t.id]
            if (snoozeUntil != null && System.currentTimeMillis() < snoozeUntil) return@forEach
            val alarm = runCatching { sdf.parse("${t.date}T${t.time}") }.getOrNull() ?: return@forEach
            val diff  = now.time - alarm.time
            if (diff in 0..60_000) {
                snoozed.remove(t.id)
                triggerAlarm(t)
                if (t.repeat) {
                    val next = Calendar.getInstance().apply {
                        time = alarm; add(Calendar.DATE, 1)
                    }
                    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    t.date = iso.format(next.time)
                }
            }
        }
    }

    private fun triggerAlarm(task: Task) {
        ringingId = task.id
        playAlarmSound()
        showAlarmDialog(task)
    }

    private fun showAlarmDialog(task: Task) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_alarm, null)
        dialogView.findViewById<TextView>(R.id.tvTaskName).text = task.title
        dialogView.findViewById<TextView>(R.id.tvTaskDesc).text = task.desc

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        alarmDialog = builder.create()
        alarmDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            dismissAlarm(markDone = false)
        }
        dialogView.findViewById<Button>(R.id.btnMarkDone).setOnClickListener {
            dismissAlarm(markDone = true)
        }
        dialogView.findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            snoozeAlarm(task)
        }

        alarmDialog?.show()
    }

    private fun dismissAlarm(markDone: Boolean) {
        stopAlarmSound()
        alarmDialog?.dismiss()
        alarmDialog = null
        if (markDone && ringingId != null) {
            DataStore.tasks.find { it.id == ringingId }?.done = true
            DataStore.save()
            render()
        }
        ringingId = null
    }

    private fun snoozeAlarm(task: Task) {
        stopAlarmSound()
        alarmDialog?.dismiss()
        alarmDialog = null
        snoozed[task.id] = System.currentTimeMillis() + 5 * 60 * 1000
        ringingId = null
        toast(getString(R.string.snoozed_5min))
    }

    private fun playAlarmSound() {
        stopAlarmSound()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(requireContext(), uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
    }

    // ── Add / Edit task dialog ────────────────────────────────────────────

    private fun showTaskDialog(existing: Task?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_task, null)

        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etTitle    = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDesc     = dialogView.findViewById<EditText>(R.id.etDesc)
        val spinPrio   = dialogView.findViewById<Spinner>(R.id.spinPriority)
        val spinCat    = dialogView.findViewById<Spinner>(R.id.spinCategory)
        val btnDate    = dialogView.findViewById<Button>(R.id.btnDate)
        val btnTime    = dialogView.findViewById<Button>(R.id.btnTime)
        val cbRepeat   = dialogView.findViewById<CheckBox>(R.id.cbRepeat)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnSave)

        val priorities = listOf("high", "medium", "low")
        val categories = listOf("work", "personal", "other")

        spinPrio.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            priorities.map { it.replaceFirstChar { c -> c.uppercase() } }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinCat.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            categories.map { it.replaceFirstChar { c -> c.uppercase() } }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        var selectedDate = existing?.date ?: ""
        var selectedTime = existing?.time ?: ""

        fun updateDateBtn() { btnDate.text = if (selectedDate.isEmpty()) "Select date" else selectedDate }
        fun updateTimeBtn() { btnTime.text = if (selectedTime.isEmpty()) "Select time" else selectedTime }

        if (existing != null) {
            tvTitle.text = getString(R.string.edit_task)
            etTitle.setText(existing.title)
            etDesc.setText(existing.desc)
            spinPrio.setSelection(priorities.indexOf(existing.priority).coerceAtLeast(0))
            spinCat.setSelection(categories.indexOf(existing.category).coerceAtLeast(0))
            cbRepeat.isChecked = existing.repeat
        }
        updateDateBtn(); updateTimeBtn()

        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                updateDateBtn()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, min ->
                selectedTime = String.format("%02d:%02d", h, min)
                updateTimeBtn()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) { etTitle.error = "Required"; return@setOnClickListener }

            if (existing != null) {
                existing.title    = title
                existing.desc     = etDesc.text.toString().trim()
                existing.priority = priorities[spinPrio.selectedItemPosition]
                existing.category = categories[spinCat.selectedItemPosition]
                existing.date     = selectedDate
                existing.time     = selectedTime
                existing.repeat   = cbRepeat.isChecked
            } else {
                DataStore.tasks.add(
                    Task(
                        id       = DataStore.nextTaskId++,
                        title    = title,
                        desc     = etDesc.text.toString().trim(),
                        priority = priorities[spinPrio.selectedItemPosition],
                        category = categories[spinCat.selectedItemPosition],
                        date     = selectedDate,
                        time     = selectedTime,
                        repeat   = cbRepeat.isChecked
                    )
                )
            }
            toast(getString(R.string.task_saved))
            DataStore.save()
            dialog.dismiss()
            render()
        }
        dialog.show()
    }

    private fun confirmDelete(t: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage("Delete \"${t.title}\"?")
            .setPositiveButton("Delete") { _, _ ->
                DataStore.tasks.remove(t)
                DataStore.save()
                toast(getString(R.string.task_deleted))
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
