package com.mewname.app

import android.app.Instrumentation
import android.os.Bundle
import com.mewname.app.domain.CalendarRepository
import java.time.LocalDate

/** Exercises the calendar source on the device's network without opening an activity. */
class CalendarRuntimeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val today = LocalDate.now()
            val repository = CalendarRepository(targetContext)
            val catalog = repository.refresh(today.year)
            val events = catalog.events.filter { it.occursOn(today) }
            check(repository.cached(today.year) == catalog) { "Calendar cache mismatch" }
            result.putString("stream", "Date=" + today + "; events=" + events.size +
                "; titles=" + events.map { it.title } + "\nPASS\n")
            finish(-1, result)
        } catch (failure: Exception) {
            result.putString("stream", failure.stackTraceToString())
            finish(0, result)
        }
    }
}