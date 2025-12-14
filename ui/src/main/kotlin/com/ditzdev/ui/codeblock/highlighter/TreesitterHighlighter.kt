package com.ditzdev.ui.codeblock.highlighter

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.ditzdev.ui.codeblock.BashCodeBlockView
import org.json.JSONArray
import org.json.JSONObject

class TreesitterHighlighter(
    private val grammarJson: String,
    private val language: String
) : BashCodeBlockView.SyntaxHighlighter {

    private val grammar: Map<String, Any>
    private val colorMap: Map<String, Int>

    init {
        grammar = parseGrammar(grammarJson)
        colorMap = createDefaultColorMap()
    }

    private fun parseGrammar(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            val jsonObject = JSONObject(json)
            val keys = jsonObject.keys()
            
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.get(key)
                
                when (value) {
                    is JSONObject -> result[key] = jsonObjectToMap(value)
                    is JSONArray -> result[key] = jsonArrayToList(value)
                    else -> result[key] = value
                }
            }
        } catch (e: Exception) {
            result["error"] = "Failed to parse grammar: ${e.message}"
        }
        
        return result
    }

    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            }
        }
        
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            
            list.add(when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                else -> value
            })
        }
        
        return list
    }

    private fun createDefaultColorMap(): Map<String, Int> {
        return mapOf(
            "keyword" to Color.parseColor("#569CD6"),
            "function" to Color.parseColor("#DCDCAA"),
            "string" to Color.parseColor("#CE9178"),
            "comment" to Color.parseColor("#6A9955"),
            "variable" to Color.parseColor("#9CDCFE"),
            "number" to Color.parseColor("#B5CEA8"),
            "operator" to Color.parseColor("#D4D4D4"),
            "type" to Color.parseColor("#4EC9B0"),
            "constant" to Color.parseColor("#4FC1FF"),
            "property" to Color.parseColor("#9CDCFE"),
            "parameter" to Color.parseColor("#9CDCFE"),
            "punctuation" to Color.parseColor("#D4D4D4"),
            "tag" to Color.parseColor("#569CD6"),
            "attribute" to Color.parseColor("#9CDCFE"),
            "class" to Color.parseColor("#4EC9B0"),
            "interface" to Color.parseColor("#4EC9B0"),
            "enum" to Color.parseColor("#4EC9B0"),
            "namespace" to Color.parseColor("#4EC9B0"),
            "method" to Color.parseColor("#DCDCAA"),
            "macro" to Color.parseColor("#569CD6"),
            "label" to Color.parseColor("#C586C0"),
            "escape" to Color.parseColor("#D7BA7D"),
            "regex" to Color.parseColor("#D16969"),
            "default" to Color.parseColor("#D4D4D4")
        )
    }

    override fun highlight(code: String): CharSequence {
        val spannable = SpannableStringBuilder(code)
        
        val tokens = tokenize(code)
        
        for (token in tokens) {
            val color = getColorForTokenType(token.type)
            applyColor(spannable, token.start, token.end, color)
        }
        
        return spannable
    }

    private fun tokenize(code: String): List<Token> {
        val tokens = mutableListOf<Token>()
        
        val rules = grammar["rules"] as? Map<*, *>
        if (rules == null) {
            return tokenizeBasic(code)
        }
        
        val patterns = extractPatterns(rules)
        
        var position = 0
        while (position < code.length) {
            var matched = false
            
            for (pattern in patterns) {
                val regex = pattern.regex
                val matchResult = regex.find(code, position)
                
                if (matchResult != null && matchResult.range.first == position) {
                    tokens.add(
                        Token(
                            type = pattern.type,
                            value = matchResult.value,
                            start = matchResult.range.first,
                            end = matchResult.range.last + 1
                        )
                    )
                    position = matchResult.range.last + 1
                    matched = true
                    break
                }
            }
            
            if (!matched) {
                position++
            }
        }
        
        return tokens
    }

    private fun tokenizeBasic(code: String): List<Token> {
        val tokens = mutableListOf<Token>()
        
        val keywordPattern = Regex("\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|break|continue|exit)\\b")
        val stringPattern = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
        val commentPattern = Regex("#[^\\n]*")
        val numberPattern = Regex("\\b\\d+(\\.\\d+)?\\b")
        val variablePattern = Regex("\\$\\{?[a-zA-Z_][a-zA-Z0-9_]*\\}?")
        
        val allPatterns = listOf(
            commentPattern to "comment",
            stringPattern to "string",
            keywordPattern to "keyword",
            variablePattern to "variable",
            numberPattern to "number"
        )
        
        for ((pattern, type) in allPatterns) {
            pattern.findAll(code).forEach { match ->
                tokens.add(
                    Token(
                        type = type,
                        value = match.value,
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                )
            }
        }
        
        return tokens.sortedBy { it.start }
    }

    private fun extractPatterns(rules: Map<*, *>): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        
        for ((key, value) in rules) {
            if (value is Map<*, *>) {
                val pattern = value["pattern"] as? String
                if (pattern != null) {
                    try {
                        patterns.add(
                            Pattern(
                                type = key.toString(),
                                regex = Regex(pattern)
                            )
                        )
                    } catch (e: Exception) {
                    }
                }
            }
        }
        
        return patterns
    }

    private fun getColorForTokenType(type: String): Int {
        return colorMap[type] ?: colorMap["default"]!!
    }

    private fun applyColor(spannable: SpannableStringBuilder, start: Int, end: Int, color: Int) {
        if (start >= 0 && end <= spannable.length && start < end) {
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private data class Token(
        val type: String,
        val value: String,
        val start: Int,
        val end: Int
    )

    private data class Pattern(
        val type: String,
        val regex: Regex
    )
}