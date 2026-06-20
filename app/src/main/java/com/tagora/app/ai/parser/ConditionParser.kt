package com.tagora.app.ai.parser

import com.tagora.app.data.model.AndCondition
import com.tagora.app.data.model.MultiTagCondition
import com.tagora.app.data.model.NotCondition
import com.tagora.app.data.model.OrCondition
import com.tagora.app.data.model.TaskCondition

/**
 * 条件表达式递归下降解析器。
 *
 * 语法（关键字大小写不敏感）：
 *   expression = or_expr
 *   or_expr    = and_expr ("OR" and_expr)*
 *   and_expr   = not_expr ("AND" not_expr)*
 *   not_expr   = "NOT" atom | atom
 *   atom       = TAG_NAME | "(" expression ")"
 *
 * 标签名含空格或关键字时需用双引号包裹：`"工作 AND 学习"`
 *
 * @param nameToId 标签名称到 ID 的映射表
 * @throws IllegalArgumentException 当表达式语法错误或标签名无法解析时
 */
class ConditionParser(
    private val nameToId: Map<String, String>,
) {
    private var pos = 0
    private var input = ""
    private val tokens = mutableListOf<Token>()

    private sealed class Token {
        data class Tag(val name: String) : Token()
        data object And : Token()
        data object Or : Token()
        data object Not : Token()
        data object LeftParen : Token()
        data object RightParen : Token()
        data object End : Token()
    }

    /**
     * 解析条件表达式字符串，返回 TaskCondition 树。
     * 空字符串或空白字符串返回无条件 AND（始终满足）。
     */
    fun parse(expression: String): TaskCondition {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return AndCondition(emptyList())

        input = trimmed
        pos = 0
        tokens.clear()
        tokenize()
        tokens.add(Token.End)

        pos = 0
        val result = parseOr()
        if (currentToken() !is Token.End) {
            val remaining = tokens.drop(pos).joinToString(" ") {
                when (it) {
                    is Token.Tag -> it.name
                    is Token.And -> "AND"
                    is Token.Or -> "OR"
                    is Token.Not -> "NOT"
                    is Token.LeftParen -> "("
                    is Token.RightParen -> ")"
                    is Token.End -> ""
                }
            }
            throw IllegalArgumentException("条件表达式在 '${remaining.trim()}' 附近有语法错误")
        }
        return result
    }

    // ── Tokenizer ──

    private fun tokenize() {
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '(' -> {
                    tokens.add(Token.LeftParen)
                    i++
                }
                c == ')' -> {
                    tokens.add(Token.RightParen)
                    i++
                }
                c == '"' -> {
                    // 引号包裹的标签名
                    val end = input.indexOf('"', i + 1)
                    if (end < 0) throw IllegalArgumentException("未闭合的双引号，位置 $i")
                    val name = input.substring(i + 1, end)
                    if (name.isBlank()) throw IllegalArgumentException("引号内标签名为空，位置 $i")
                    tokens.add(Token.Tag(name.trim()))
                    i = end + 1
                }
                c.isWhitespace() -> {
                    i++
                }
                else -> {
                    // 读取一个词
                    val start = i
                    while (i < input.length && !isSpecial(input[i])) {
                        i++
                    }
                    val word = input.substring(start, i)
                    when (word.uppercase()) {
                        "AND" -> tokens.add(Token.And)
                        "OR" -> tokens.add(Token.Or)
                        "NOT" -> tokens.add(Token.Not)
                        else -> tokens.add(Token.Tag(word))
                    }
                }
            }
        }
    }

    private fun isSpecial(c: Char): Boolean = c == '(' || c == ')' || c == '"' || c.isWhitespace()

    // ── Recursive Descent Parser ──

    private fun currentToken(): Token = tokens.getOrElse(pos) { Token.End }

    private fun advance() { if (pos < tokens.size) pos++ }

    private fun expect(token: Token): Token {
        val cur = currentToken()
        if (cur::class == token::class) {
            advance()
            return cur
        }
        throw IllegalArgumentException("预期 ${tokenDesc(token)}，但遇到 ${tokenDesc(cur)}")
    }

    private fun tokenDesc(token: Token): String = when (token) {
        is Token.Tag -> "标签名"
        is Token.And -> "AND"
        is Token.Or -> "OR"
        is Token.Not -> "NOT"
        is Token.LeftParen -> "("
        is Token.RightParen -> ")"
        is Token.End -> "表达式结尾"
    }

    // or_expr = and_expr ("OR" and_expr)*
    private fun parseOr(): TaskCondition {
        val terms = mutableListOf(parseAnd())
        while (currentToken() is Token.Or) {
            advance()
            terms.add(parseAnd())
        }
        return if (terms.size == 1) terms[0] else OrCondition(terms)
    }

    // and_expr = not_expr ("AND" not_expr)*
    private fun parseAnd(): TaskCondition {
        val terms = mutableListOf(parseNot())
        while (currentToken() is Token.And) {
            advance()
            terms.add(parseNot())
        }
        return if (terms.size == 1) terms[0] else AndCondition(terms)
    }

    // not_expr = "NOT" atom | atom
    private fun parseNot(): TaskCondition {
        return if (currentToken() is Token.Not) {
            advance()
            NotCondition(parseAtom())
        } else {
            parseAtom()
        }
    }

    // atom = TAG_NAME | "(" expression ")"
    private fun parseAtom(): TaskCondition {
        return when (val token = currentToken()) {
            is Token.LeftParen -> {
                advance()
                val expr = parseOr()
                expect(Token.RightParen)
                expr
            }
            is Token.Tag -> {
                advance()
                val tagId = nameToId[token.name]
                    ?: throw IllegalArgumentException("标签 '${token.name}' 不存在，请先创建该标签")
                MultiTagCondition(listOf(tagId))
            }
            is Token.End -> throw IllegalArgumentException("意外的表达式结尾")
            else -> throw IllegalArgumentException("意外的符号 '${tokenDesc(token)}'")
        }
    }
}
