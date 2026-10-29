package com.example.simplealarm

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var statusText: TextView
    private lateinit var cancelButton: Button
    private val dayButtons = mutableMapOf<Int, MaterialButton>()

    private val dayOrder = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    private val dayIds = listOf(
        R.id.dayMon, R.id.dayTue, R.id.dayWed, R.id.dayThu,
        R.id.dayFri, R.id.daySat, R.id.daySun
    )
    private val dayFullNames = mapOf(
        Calendar.MONDAY to "周一", Calendar.TUESDAY to "周二",
        Calendar.WEDNESDAY to "周三", Calendar.THURSDAY to "周四",
        Calendar.FRIDAY to "周五", Calendar.SATURDAY to "周六",
        Calendar.SUNDAY to "周日"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timePicker = findViewById(R.id.timePicker)
        statusText = findViewById(R.id.statusText)
        cancelButton = findViewById(R.id.cancelButton)
        timePicker.setIs24HourView(true)

        dayOrder.forEachIndexed { i, day -> dayButtons[day] = findViewById(dayIds[i]) }

        val cfg = AlarmScheduler.loadConfig(this)
        timePicker.hour = cfg.hour
        timePicker.minute = cfg.minute
        if (cfg.days.isNotEmpty()) {
            cfg.days.forEach { d -> dayButtons[d]?.isChecked = true }
        } else if (!cfg.enabled) {
            listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            ).forEach { d -> dayButtons[d]?.isChecked = true }
        }

        findViewById<Button>(R.id.setButton).setOnClickListener { onSetClicked() }
        cancelButton.setOnClickListener {
            AlarmScheduler.setEnabled(this, false)
            refreshStatus()
            Toast.makeText(this, "已关闭闹钟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onSetClicked() {
        val selected = dayButtons.filterValues { it.isChecked }.keys
        if (selected.isEmpty()) {
            Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show()
            return
        }
        AlarmScheduler.saveConfig(this, timePicker.hour, timePicker.minute, selected)
        AlarmScheduler.scheduleNext(this)
        refreshStatus()
        Toast.makeText(this, "闹钟已设置", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val cfg = AlarmScheduler.loadConfig(this)
        val next = AlarmScheduler.getNextTrigger(this)
        if (cfg.enabled && next > System.currentTimeMillis()) {
            val dateFmt = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
            val cal = Calendar.getInstance().apply { timeInMillis = next }
            val dayName = dayFullNames[cal.get(Calendar.DAY_OF_WEEK)] ?: ""
            statusText.text = "下次：${dateFmt.format(Date(next))} $dayName"
            cancelButton.isEnabled = true
        } else if (cfg.enabled) {
            statusText.text = "闹钟已启用"
            cancelButton.isEnabled = true
        } else {
            statusText.text = "闹钟已关闭"
            cancelButton.isEnabled = false
        }
    }
}