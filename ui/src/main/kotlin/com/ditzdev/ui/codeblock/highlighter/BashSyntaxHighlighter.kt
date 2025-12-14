package com.ditzdev.ui.codeblock.highlighter

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.ditzdev.ui.codeblock.BashCodeBlockView

class BashSyntaxHighlighter : BashCodeBlockView.SyntaxHighlighter {

    private val keywords = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "select",
        "while", "until", "do", "done", "in", "function", "time", "coproc",
        "return", "break", "continue", "exit", "shift", "export", "readonly",
        "local", "declare", "typeset", "unset", "trap", "source", "eval",
        "exec", "let", "test"
    )

    private val builtinCommands = setOf(
        "cd", "pwd", "echo", "printf", "read", "set", "unset", "alias",
        "bg", "fg", "jobs", "kill", "wait", "disown", "suspend", "logout",
        "times", "type", "which", "whereis", "whatis", "apropos", "help",
        "history", "fc", "getopts", "umask", "ulimit", "shopt", "bind",
        "builtin", "command", "enable", "hash", "pwd", "readarray", "mapfile"
    )

    private val commonCommands = setOf(
        "ls", "cat", "grep", "sed", "awk", "cut", "sort", "uniq", "wc",
        "head", "tail", "find", "xargs", "mkdir", "rm", "cp", "mv", "touch",
        "chmod", "chown", "chgrp", "ln", "tar", "gzip", "gunzip", "zip",
        "unzip", "curl", "wget", "git", "ssh", "scp", "rsync", "diff",
        "patch", "make", "gcc", "python", "node", "npm", "pip", "docker",
        "kubectl", "systemctl", "service", "ps", "top", "htop", "kill",
        "pkill", "killall", "free", "df", "du", "mount", "umount", "lsblk",
        "fdisk", "mkfs", "fsck", "dd", "nc", "netstat", "ss", "ip", "ifconfig",
        "ping", "traceroute", "nslookup", "dig", "iptables", "ufw", "firewall-cmd"
    )

    private val colorKeyword = Color.parseColor("#569CD6")
    private val colorBuiltin = Color.parseColor("#DCDCAA")
    private val colorCommand = Color.parseColor("#4EC9B0")
    private val colorString = Color.parseColor("#CE9178")
    private val colorComment = Color.parseColor("#6A9955")
    private val colorVariable = Color.parseColor("#9CDCFE")
    private val colorNumber = Color.parseColor("#B5CEA8")
    private val colorOperator = Color.parseColor("#D4D4D4")
    private val colorFlag = Color.parseColor("#C586C0")

    override fun highlight(code: String): CharSequence {
        val spannable = SpannableStringBuilder(code)
        val lines = code.lines()
        var currentPos = 0

        for (line in lines) {
            highlightLine(spannable, line, currentPos)
            currentPos += line.length + 1
        }

        return spannable
    }

    private fun highlightLine(spannable: SpannableStringBuilder, line: String, startPos: Int) {
        if (line.trimStart().startsWith("#")) {
            applyColor(spannable, startPos, startPos + line.length, colorComment)
            return
        }

        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            val pos = startPos + i

            when {
                ch == '\'' && !inDoubleQuote -> {
                    val end = findClosingQuote(line, i + 1, '\'')
                    if (end != -1) {
                        applyColor(spannable, pos, startPos + end + 1, colorString)
                        i = end
                        inSingleQuote = !inSingleQuote
                    }
                }
                ch == '"' && !inSingleQuote -> {
                    val end = findClosingQuote(line, i + 1, '"')
                    if (end != -1) {
                        highlightStringWithVariables(spannable, line, i, end, startPos)
                        i = end
                        inDoubleQuote = !inDoubleQuote
                    }
                }
                ch == '#' && !inSingleQuote && !inDoubleQuote -> {
                    applyColor(spannable, pos, startPos + line.length, colorComment)
                    return
                }
                ch == '$' && !inSingleQuote -> {
                    val varEnd = findVariableEnd(line, i)
                    applyColor(spannable, pos, startPos + varEnd, colorVariable)
                    i = varEnd - 1
                }
                ch.isDigit() && !inSingleQuote && !inDoubleQuote -> {
                    val numEnd = findNumberEnd(line, i)
                    if (numEnd > i) {
                        applyColor(spannable, pos, startPos + numEnd, colorNumber)
                        i = numEnd - 1
                    }
                }
                ch in setOf('|', '&', ';', '>', '<', '=') && !inSingleQuote && !inDoubleQuote -> {
                    applyColor(spannable, pos, pos + 1, colorOperator)
                }
                ch == '-' && i + 1 < line.length && line[i + 1].isLetter() && !inSingleQuote && !inDoubleQuote -> {
                    val flagEnd = findFlagEnd(line, i)
                    applyColor(spannable, pos, startPos + flagEnd, colorFlag)
                    i = flagEnd - 1
                }
            }

            i++
        }

        highlightWords(spannable, line, startPos)
    }

    private fun highlightWords(spannable: SpannableStringBuilder, line: String, startPos: Int) {
        val words = line.split(Regex("\\s+|[|&;><(){}\\[\\]]"))
        var searchPos = 0

        for (word in words) {
            if (word.isEmpty()) continue

            val wordStart = line.indexOf(word, searchPos)
            if (wordStart == -1) continue

            val cleanWord = word.trim()
            val absStart = startPos + wordStart
            val absEnd = absStart + cleanWord.length

            if (hasExistingSpan(spannable, absStart, absEnd)) {
                searchPos = wordStart + word.length
                continue
            }

            when {
                keywords.contains(cleanWord) -> {
                    applyColor(spannable, absStart, absEnd, colorKeyword)
                }
                builtinCommands.contains(cleanWord) -> {
                    applyColor(spannable, absStart, absEnd, colorBuiltin)
                }
                commonCommands.contains(cleanWord) -> {
                    applyColor(spannable, absStart, absEnd, colorCommand)
                }
            }

            searchPos = wordStart + word.length
        }
    }

    private fun highlightStringWithVariables(
        spannable: SpannableStringBuilder,
        line: String,
        start: Int,
        end: Int,
        startPos: Int
    ) {
        applyColor(spannable, startPos + start, startPos + end + 1, colorString)

        var i = start + 1
        while (i < end) {
            if (line[i] == '$') {
                val varEnd = findVariableEnd(line, i)
                applyColor(spannable, startPos + i, startPos + varEnd, colorVariable)
                i = varEnd
            } else {
                i++
            }
        }
    }

    private fun findClosingQuote(line: String, start: Int, quote: Char): Int {
        var i = start
        while (i < line.length) {
            if (line[i] == quote && (i == 0 || line[i - 1] != '\\')) {
                return i
            }
            i++
        }
        return -1
    }

    private fun findVariableEnd(line: String, start: Int): Int {
        if (start + 1 >= line.length) return start + 1

        var i = start + 1

        if (line[i] == '{') {
            while (i < line.length && line[i] != '}') i++
            return if (i < line.length) i + 1 else i
        }

        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
            i++
        }

        return i
    }

    private fun findNumberEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
            i++
        }
        return i
    }

    private fun findFlagEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i] == '-' || line[i].isLetterOrDigit())) {
            i++
        }
        return i
    }

    private fun hasExistingSpan(spannable: SpannableStringBuilder, start: Int, end: Int): Boolean {
        val spans = spannable.getSpans(start, end, ForegroundColorSpan::class.java)
        return spans.isNotEmpty()
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
}