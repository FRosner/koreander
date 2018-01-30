package de.cvguy.kotlin.koreander.parser

import de.cvguy.kotlin.koreander.exception.ExpectedOther
import de.cvguy.kotlin.koreander.exception.UnexpectedDocType
import de.cvguy.kotlin.koreander.exception.UnexpectedEndOfInput
import de.cvguy.kotlin.koreander.exception.UnexpextedToken
import de.cvguy.kotlin.koreander.parser.Token.Type.*
import org.jetbrains.kotlin.backend.common.pop

import java.util.Stack

val TRIPLE_QUOT = "\"\"\""

class KoreanderParser(
        private val lexer: Lexer = Lexer()
) {
    fun generateScriptCode(input: String, contextClass: String): String {
        return KoreanderParseEngine(lexer.lexString(input), contextClass).parse()
    }
}

class KoreanderParseEngine(
        tokens: List<Token>,
        val contextClass: String
) {
    abstract class TemplateLine(protected val content: String, depth: Int) {
        var depth: Int = depth
            private set

        abstract fun outputExpression(): String
        open fun templateLine(): String = "_koreanderTemplateOutput.add(${outputExpression()})"
        fun resetDepth() { depth = 0 }
    }

    class OutputTemplateLine(content: String, depth: Int) : TemplateLine(content, depth) {
        override fun outputExpression() = TRIPLE_QUOT + " ".repeat(depth) + content + TRIPLE_QUOT + ".htmlEscape()"
    }

    class HtmlSafeTemplateLine(content: String, depth: Int) : TemplateLine(content, depth) {
        override fun outputExpression() = TRIPLE_QUOT + " ".repeat(depth) + content + TRIPLE_QUOT
    }

    class ControlLine(content: String, depth: Int = 0) : TemplateLine(content, depth) {
        override fun outputExpression() = throw AssertionError("Control lines do not output anything.")
        override fun templateLine(): String = content
    }

    class ExpressionLine(content: String, depth: Int) : TemplateLine(content, depth) {
        override fun outputExpression(): String = (if(depth > 0) TRIPLE_QUOT + " ".repeat(depth) + TRIPLE_QUOT + " + " else "") + content
    }

    private val iterator = tokens.listIterator()
    private val lines = mutableListOf<TemplateLine>()
    private val delayedLines = Stack<TemplateLine>()

    fun parse(): String {
        lines.clear()

        lines.add(ControlLine("val _koreanderTemplateOutput = mutableListOf<String>()"))
        lines.add(ControlLine("""fun String.htmlEscape(): String { return replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt") }"""))
        lines.add(ControlLine("(bindings[\"context\"] as $contextClass).apply({"))

        unshiftDocType()

        // one loop execution processes one line of the template
        while(iterator.hasNext()) {
            val index = iterator.nextIndex()

            // optional whitespace
            unshiftWhiteSpace()

            val hadTag = unshiftTag()

            val hadOutput = unshiftCode() || unshiftSilentCode() || unshiftComment() || unshiftText()

            // can close tag on the same line (a little hacky for now)
            // maybe lines could have types + running a post processor
            if(hadTag && hadOutput && iterator.nextIsClosingWhitespace()) {
                oneLinerTagOutput()
            }

            // nothing has been processed
            if(index == iterator.nextIndex()) {
                throw UnexpextedToken(iterator.next())
            }
        }

        closeOpenTags(0)

        lines.add(ControlLine("})"))
        lines.add(ControlLine("""_koreanderTemplateOutput.joinToString("\n")"""))

        val output = lines.map { it.templateLine() }.filter { it.isNotEmpty() }.joinToString("\n")

        println(output)

        return output
    }

    private fun oneLinerTagOutput() {
        val expressionLine = lines.pop()
        val openingTagLine = lines.pop()
        val closingTagLine = delayedLines.pop()
        val depth = openingTagLine.depth

        openingTagLine.resetDepth()
        expressionLine.resetDepth()
        closingTagLine.resetDepth()

        val expression = listOf(
                openingTagLine.outputExpression(),
                expressionLine.outputExpression(),
                closingTagLine.outputExpression()
        ).joinToString(" + ")

        lines.add(ExpressionLine(expression, depth))
    }

    private fun unshiftDocType(): Boolean {
        iterator.nextIfType(DOC_TYPE_IDENTIFIER) ?: return false
        val typeToken = iterator.nextIfType(DOC_TYPE)

        val docTypeLine = when (typeToken?.content) {
            null -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
            "Strict" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
            "Frameset" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Frameset//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd\">"
            "5" -> "<!DOCTYPE html>"
            "1.1" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">"
            "Basic" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML Basic 1.1//EN\" \"http://www.w3.org/TR/xhtml-basic/xhtml-basic11.dtd\">"
            "Mobile" -> "<!DOCTYPE html PUBLIC \"-//WAPFORUM//DTD XHTML Mobile 1.2//EN\" \"http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd\">"
            "RDFa" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML+RDFa 1.0//EN\" \"http://www.w3.org/MarkUp/DTD/xhtml-rdfa-1.dtd\">"
            else -> throw UnexpectedDocType(typeToken)
        }

        lines.add(HtmlSafeTemplateLine(docTypeLine, currentDepth))

        return true
    }

    private fun unshiftWhiteSpace(): Boolean {
        val token = iterator.nextIfType(WHITE_SPACE) ?: return false

        val len = token.content.length

        closeOpenTags(len)

        // remember as current depth
        delayedLines.push(ControlLine("", len))

        return true
    }

    private fun closeOpenTags(downTo: Int) {
        while (delayedLines.isNotEmpty() && currentDepth >= downTo) {
            lines.add(delayedLines.pop())
        }
    }

    private fun unshiftComment(): Boolean {
        val token = iterator.nextIfType(COMMENT) ?: return false
        lines.add(HtmlSafeTemplateLine("<!-- ${token.content} -->", currentDepth))
        return true
    }

    private fun unshiftText(): Boolean {
        val token = iterator.nextIfType(TEXT) ?: return false
        lines.add(OutputTemplateLine(token.content, currentDepth))
        return true
    }

    private fun unshiftTag(): Boolean {
        val elementToken = iterator.nextIfType(ELEMENT_IDENTIFIER)
        val elementExpression = elementToken?.let { iterator.nextForceType(BRACKET_EXPRESSION, STRING) }

        val elementIdToken = iterator.nextIfType(ELEMENT_ID_IDENTIFIER)
        val elementIdExpression = elementIdToken?.let { iterator.nextForceType(BRACKET_EXPRESSION, STRING) }

        val elementClassToken = iterator.nextIfType(ELEMENT_CLASS_IDENTIFIER)
        val elementClassExpression = elementClassToken?.let { iterator.nextForceType(BRACKET_EXPRESSION, STRING) }

        // must have at least one defined
        elementToken ?: elementIdToken ?: elementClassToken ?: return false

        val attributes = mutableListOf<Pair<Token, Token>>()

        while(true) {
            val name = iterator.nextIfType(BRACKET_EXPRESSION, STRING) ?: break
            iterator.nextForceType(ATTRIBUTE_CONNECTOR)
            val value = iterator.nextForceType(BRACKET_EXPRESSION, QUOTED_STRING, STRING)

            attributes.add(Pair(name, value))
        }

        val name = if(elementExpression == null) "div" else expressionCode(elementExpression, true)
        val id = if(elementIdExpression == null) "" else appendAttributeString("id", elementIdExpression)
        val classes = if(elementClassExpression == null) "" else appendAttributeString("class", elementClassExpression)
        val attribute = attributes.map { appendAttributeCode(it.first, it.second) }.joinToString("")

        if(iterator.nextIsClosingWhitespace()) {
            lines.add(HtmlSafeTemplateLine("<$name$id$classes$attribute></$name>", currentDepth))
        }
        else {
            lines.add(HtmlSafeTemplateLine("<$name$id$classes$attribute>", currentDepth))
            delayedLines.push(HtmlSafeTemplateLine("</$name>", currentDepth))
        }

        return true
    }

    private fun appendAttributeString(name: String, value: Token): String {
        val valueExpression = expressionCode(value, true)
        return """ $name="$valueExpression""""
    }

    private fun appendAttributeCode(name: Token, value: Token): String {
        val nameExpression = expressionCode(name, true)
        val valueExpression = expressionCode(value, true)
        return """ $nameExpression="$valueExpression""""
    }

    private fun expressionCode(token: Token, inString: Boolean) = when(token.type) {
        EXPRESSION -> """(${token.content}).toString()""".let { if(inString) inStringExpression(it) else it }
        BRACKET_EXPRESSION -> """(${token.content.substring(1, token.content.length - 1)}).toString()""".let { if(inString) inStringExpression(it) else it }
        QUOTED_STRING -> if(inString) token.content.substring(1, token.content.length - 1) else token.content
        STRING, TEXT -> if(inString) token.content else """"${token.content}""""
        else -> throw ExpectedOther(token, setOf(BRACKET_EXPRESSION, QUOTED_STRING, EXPRESSION, STRING))
    }

    private fun unshiftCode(): Boolean {
        iterator.nextIfType(CODE_IDENTIFIER) ?: return false
        val code = iterator.nextForceType(EXPRESSION)

        if (iterator.nextIsDeeperWhitespace()) {
            lines.add(ControlLine("_koreanderTemplateOutput.add(\"$currentWhitespace\" + (${code.content} {"))
            delayedLines.push(ControlLine("}).toString())"))
        } else {
            lines.add(ExpressionLine(expressionCode(code, false), currentDepth))
        }

        return true
    }

    private fun unshiftSilentCode(): Boolean {
        iterator.nextIfType(SILENT_CODE_IDENTIFIER) ?: return false
        val code = iterator.nextForceType(EXPRESSION)

        if (iterator.nextIsDeeperWhitespace()) {
            lines.add(ControlLine("${code.content} {"))
            delayedLines.push(ControlLine("}", currentDepth))
        } else {
            lines.add(ControlLine(code.content))
        }

        return true
    }

    private fun ListIterator<Token>.peek(): Token? {
        if(!hasNext()) return null

        val result = next()

        previous()

        return result
    }

    private fun ListIterator<Token>.nextIsDeeperWhitespace(): Boolean {
        val token = peek() ?: return false
        return token.type == WHITE_SPACE && token.content.length > currentDepth
    }

    private fun ListIterator<Token>.nextIsClosingWhitespace(): Boolean {
        hasNext() || return true // end of input, will be closed
        val token = peek() ?: return false
        return token.type == WHITE_SPACE && token.content.length <= currentDepth
    }

    private fun ListIterator<Token>.nextIfType(vararg type: Token.Type): Token? {
        val token = peek() ?: return null

        if(type.contains(token.type)) {
            return next()
        }

        return null
    }

    private fun ListIterator<Token>.nextForceType(vararg type: Token.Type): Token {
        return nextIfType(*type) ?: throw ExpectedOther(peek() ?: throw UnexpectedEndOfInput(), type.toSet())
    }

    private val currentDepth get() = delayedLines.lastOrNull()?.depth ?: 0
    private val currentWhitespace get() = " ".repeat(currentDepth)

    private fun inStringExpression(expression: String): String {
        return """${'$'}{$expression}"""
    }
}