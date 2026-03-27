package io.github.mobdev

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

    private lateinit var vm: CalculatorViewModel
    private lateinit var tvDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vm = ViewModelProvider(this)[CalculatorViewModel::class.java]
        tvDisplay = findViewById(R.id.tvDisplay)

        setupDigitButtons()
        setupOperationButtons()
        setupSpecialButtons()

        updateDisplay()
    }

    private fun setupDigitButtons() {
        val digitIds = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )
        digitIds.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                vm.onDigit(digit)
                updateDisplay()
            }
        }
        findViewById<Button>(R.id.btnDot).setOnClickListener {
            vm.onDot()
            updateDisplay()
        }
    }

    private fun setupOperationButtons() {
        val opIds = mapOf(
            R.id.btnAdd to "+", R.id.btnSub to "−",
            R.id.btnMul to "×", R.id.btnDiv to "÷"
        )
        opIds.forEach { (id, op) ->
            findViewById<Button>(id).setOnClickListener {
                vm.onOperation(op)
                updateDisplay()
            }
        }
        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            vm.onEquals()
            updateDisplay()
        }
    }

    private fun setupSpecialButtons() {
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            vm.onClear()
            updateDisplay()
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            vm.onBackspace()
            updateDisplay()
        }
        findViewById<Button>(R.id.btnPercent).setOnClickListener {
            vm.onPercent()
            updateDisplay()
        }
        findViewById<Button>(R.id.btnToggleSign).setOnClickListener {
            vm.onToggleSign()
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        tvDisplay.text = vm.display
    }
}
