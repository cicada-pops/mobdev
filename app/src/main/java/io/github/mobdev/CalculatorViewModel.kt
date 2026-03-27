package io.github.mobdev

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class CalculatorViewModel(private val state: SavedStateHandle) : ViewModel() {

    companion object {
        private const val KEY_DISPLAY = "display"
        private const val KEY_FIRST_OPERAND = "first_operand"
        private const val KEY_OPERATION = "operation"
        private const val KEY_RESET_ON_NEXT = "reset_on_next"
    }

    var display: String
        get() = state[KEY_DISPLAY] ?: "0"
        set(value) { state[KEY_DISPLAY] = value }

    private var firstOperand: Double
        get() = state[KEY_FIRST_OPERAND] ?: 0.0
        set(value) { state[KEY_FIRST_OPERAND] = value }

    private var operation: String
        get() = state[KEY_OPERATION] ?: ""
        set(value) { state[KEY_OPERATION] = value }

    private var resetOnNext: Boolean
        get() = state[KEY_RESET_ON_NEXT] ?: false
        set(value) { state[KEY_RESET_ON_NEXT] = value }

    fun onDigit(digit: String) {
        if (resetOnNext) {
            display = digit
            resetOnNext = false
        } else {
            display = if (display == "0" && digit != ".") digit else display + digit
        }
    }

    fun onDot() {
        if (resetOnNext) {
            display = "0."
            resetOnNext = false
            return
        }
        if (!display.contains(".")) {
            display += "."
        }
    }

    fun onOperation(op: String) {
        if (operation.isNotEmpty() && !resetOnNext) {
            calculate()
        }
        firstOperand = display.toDoubleOrNull() ?: 0.0
        operation = op
        resetOnNext = true
    }

    fun onEquals() {
        if (operation.isEmpty()) return
        calculate()
        operation = ""
    }

    fun onClear() {
        display = "0"
        firstOperand = 0.0
        operation = ""
        resetOnNext = false
    }

    fun onBackspace() {
        if (resetOnNext) return
        display = if (display.length > 1) display.dropLast(1) else "0"
    }

    fun onPercent() {
        val value = display.toDoubleOrNull() ?: return
        display = formatResult(value / 100.0)
        resetOnNext = true
    }

    fun onToggleSign() {
        val value = display.toDoubleOrNull() ?: return
        display = formatResult(-value)
    }

    private fun calculate() {
        val second = display.toDoubleOrNull() ?: return
        val result = when (operation) {
            "+" -> firstOperand + second
            "−" -> firstOperand - second
            "×" -> firstOperand * second
            "÷" -> if (second != 0.0) firstOperand / second else Double.NaN
            else -> return
        }
        display = if (result.isNaN()) "Error" else formatResult(result)
        firstOperand = if (result.isNaN()) 0.0 else result
        resetOnNext = true
    }

    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble() && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
