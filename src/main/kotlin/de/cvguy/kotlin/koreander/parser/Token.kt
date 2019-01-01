package de.cvguy.kotlin.koreander.parser

data class Token(
        val type: Type,
        val content: String,
        val line: Int,
        val character: Int,
        val offset: Int
) {
    enum class Type {
        DOC_TYPE_IDENTIFIER,
        DOC_TYPE,
        WHITE_SPACE,
        ELEMENT_IDENTIFIER,
        ELEMENT_ID_IDENTIFIER,
        ELEMENT_CLASS_IDENTIFIER,
        ATTRIBUTE_KEY,
        ATTRIBUTE_CONNECTOR,
        FILTER_IDENTIFIER,
        STRING,
        TEXT,
        QUOTED_STRING,
        EXPRESSION,
        BRACKET_EXPRESSION,
        CODE_IDENTIFIER,
        LAMBDA_VARIABLES_IDENTIFIER,
        LAMBDA_VARIABLES,
        SILENT_CODE_IDENTIFIER,
        COMMENT_IDENTIFIER,
        COMMENT
    }
}