package com.mewname.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Builds the nickname results panel without owning service or window lifecycle. */
internal class OverlayResultsPresenter(private val context: Context) {
    fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }

    }

    fun createView(
        results: List<Pair<String, String>>,
        onCopy: (String) -> Unit,
        onClose: () -> Unit,
        onOpenReview: (() -> Unit)? = null
    ): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 34f
                setColor(Color.argb(208, 255, 255, 255))
            }
            setPadding(60, 60, 60, 60)
            elevation = 40f
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }
        val title = TextView(context).apply {
            text = "Nomes sugeridos"
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)
        layout.addView(titleRow)

        results.forEach { (configName, generatedName) ->
            val btnLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    onCopy(generatedName)
                    Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }

            val label = TextView(context).apply {
                text = configName
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            val value = TextView(context).apply {
                text = generatedName
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
            }

            btnLayout.addView(label)
            btnLayout.addView(value)
            layout.addView(btnLayout)

            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(Color.LTGRAY)
            }
            layout.addView(divider)
        }

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 0)
        }

        fun actionButton(label: String, isLast: Boolean = false, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(48, 63, 84))
                minHeight = 0
                minimumHeight = 0
                minimumWidth = 0
                setPadding(14, 22, 14, 22)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 28f
                    setColor(Color.argb(245, 248, 250, 255))
                    setStroke(2, Color.rgb(205, 214, 226))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (isLast) 0 else 8
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        if (onOpenReview != null) {
            actionsRow.addView(
                actionButton("Revisar dados") { onOpenReview() }
            )
        }

        actionsRow.addView(
            actionButton("Fechar", isLast = true) {
                onClose()
            }
        )

        layout.addView(actionsRow)

        return layout
    }
}
