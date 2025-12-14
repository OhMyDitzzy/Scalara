package com.ditzdev.ui.codeblock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.ditzdev.ui.R
import com.ditzdev.ui.codeblock.highlighter.BashSyntaxHighlighter
import com.ditzdev.ui.codeblock.highlighter.TreesitterHighlighter

class BashCodeBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val codeTextView: CodeTextView
    private val lineNumberView: LineNumberView
    private val copyButton: ImageButton
    private val horizontalScrollView: HorizontalScrollView
    private val verticalScrollView: ScrollView
    private val contentContainer: FrameLayout

    private var highlighter: SyntaxHighlighter = BashSyntaxHighlighter()
    private var rawCode: String = ""
    
    @ColorInt
    private var backgroundColor: Int = Color.parseColor("#1E1E1E")
    
    @ColorInt
    private var lineNumberColor: Int = Color.parseColor("#858585")
    
    @ColorInt
    private var lineNumberBackgroundColor: Int = Color.parseColor("#252526")
    
    private var fontSize: Float = 14f
    private var fontFamily: Typeface = Typeface.MONOSPACE
    private var showLineNumbers: Boolean = true
    private var showCopyButton: Boolean = true
    private var enableLongPressSelect: Boolean = true

    init {
        inflate(context, R.layout.bash_code_block_view, this)
        
        codeTextView = findViewById(R.id.codeTextView)
        lineNumberView = findViewById(R.id.lineNumberView)
        copyButton = findViewById(R.id.copyButton)
        horizontalScrollView = findViewById(R.id.horizontalScrollView)
        verticalScrollView = findViewById(R.id.verticalScrollView)
        contentContainer = findViewById(R.id.contentContainer)
        
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.BashCodeBlockView)
            
            backgroundColor = typedArray.getColor(
                R.styleable.BashCodeBlockView_codeBackgroundColor,
                backgroundColor
            )
            
            lineNumberColor = typedArray.getColor(
                R.styleable.BashCodeBlockView_lineNumberColor,
                lineNumberColor
            )
            
            lineNumberBackgroundColor = typedArray.getColor(
                R.styleable.BashCodeBlockView_lineNumberBackgroundColor,
                lineNumberBackgroundColor
            )
            
            fontSize = typedArray.getDimension(
                R.styleable.BashCodeBlockView_codeFontSize,
                fontSize * resources.displayMetrics.scaledDensity
            ) / resources.displayMetrics.scaledDensity
            
            showLineNumbers = typedArray.getBoolean(
                R.styleable.BashCodeBlockView_showLineNumbers,
                showLineNumbers
            )
            
            showCopyButton = typedArray.getBoolean(
                R.styleable.BashCodeBlockView_showCopyButton,
                showCopyButton
            )
            
            enableLongPressSelect = typedArray.getBoolean(
                R.styleable.BashCodeBlockView_enableLongPressSelect,
                enableLongPressSelect
            )
            
            typedArray.recycle()
        }
        
        setupViews()
        setupScrollSync()
    }

    private fun setupViews() {
        setBackgroundColor(backgroundColor)
        
        codeTextView.textSize = fontSize
        codeTextView.typeface = fontFamily
        codeTextView.setTextIsSelectable(enableLongPressSelect)
        
        lineNumberView.textSize = fontSize
        lineNumberView.typeface = fontFamily
        lineNumberView.setTextColor(lineNumberColor)
        lineNumberView.setBackgroundColor(lineNumberBackgroundColor)
        lineNumberView.visibility = if (showLineNumbers) View.VISIBLE else View.GONE
        
        copyButton.visibility = if (showCopyButton) View.VISIBLE else View.GONE
        copyButton.setOnClickListener {
            copyCodeToClipboard()
        }
    }

    private fun setupScrollSync() {
        verticalScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            lineNumberView.scrollTo(0, scrollY)
        }
    }

    fun setCode(code: String) {
        rawCode = code
        val highlighted = highlighter.highlight(code)
        codeTextView.text = highlighted
        lineNumberView.updateLineNumbers(code.lines().size)
    }

    fun getCode(): String = rawCode

    fun setHighlighter(highlighter: SyntaxHighlighter) {
        this.highlighter = highlighter
        if (rawCode.isNotEmpty()) {
            setCode(rawCode)
        }
    }

    fun loadTreesitterGrammar(grammarJson: String, language: String) {
        val treesitterHighlighter = TreesitterHighlighter(grammarJson, language)
        setHighlighter(treesitterHighlighter)
    }

    fun setCodeBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        setBackgroundColor(color)
    }

    fun setLineNumberColor(@ColorInt color: Int) {
        lineNumberColor = color
        lineNumberView.setTextColor(color)
    }

    fun setLineNumberBackgroundColor(@ColorInt color: Int) {
        lineNumberBackgroundColor = color
        lineNumberView.setBackgroundColor(color)
    }

    fun setCodeFontSize(size: Float) {
        fontSize = size
        codeTextView.textSize = size
        lineNumberView.textSize = size
        lineNumberView.updateLineNumbers(rawCode.lines().size)
    }

    fun setCodeFontFamily(typeface: Typeface) {
        fontFamily = typeface
        codeTextView.typeface = typeface
        lineNumberView.typeface = typeface
    }

    fun setShowLineNumbers(show: Boolean) {
        showLineNumbers = show
        lineNumberView.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setShowCopyButton(show: Boolean) {
        showCopyButton = show
        copyButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setEnableLongPressSelect(enable: Boolean) {
        enableLongPressSelect = enable
        codeTextView.setTextIsSelectable(enable)
    }

    private fun copyCodeToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bash Code", rawCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    interface SyntaxHighlighter {
        fun highlight(code: String): CharSequence
    }

    private inner class CodeTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

        init {
            setPadding(16, 16, 16, 16)
            setTextColor(Color.parseColor("#D4D4D4"))
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private inner class LineNumberView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : androidx.appcompat.widget.AppCompatTextView(context, attrs, defStyleAttr) {

        init {
            setPadding(16, 16, 16, 16)
            gravity = Gravity.END
        }

        fun updateLineNumbers(lineCount: Int) {
            val sb = StringBuilder()
            for (i in 1..lineCount) {
                sb.append(i)
                if (i < lineCount) {
                    sb.append("\n")
                }
            }
            text = sb.toString()
        }
    }
}