package de.cvguy.kotlin.koreander

import de.cvguy.kotlin.koreander.filter.KoreanderFilter
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.io.InputStream

class KoreanderTest {
    val koreander = Koreander()
    val unit = Koreander.typeOf(Unit)
    val string = Koreander.typeOf(String)

    @Test
    fun canCompileString() {
        val result = koreander.compile("", unit)
        assertThat(result, instanceOf(CompiledTemplate::class.java))
    }

    @Test
    fun canCompileURL() {
        val url = javaClass.getResource("/empty.kor")
        val result = koreander.compile(url, unit)
        assertThat(result, instanceOf(CompiledTemplate::class.java))
    }

    @Test
    fun conCompileInputStream() {
        val inputStream: InputStream = "".byteInputStream()
        val result = koreander.compile(inputStream, unit)
        assertThat(result, instanceOf(CompiledTemplate::class.java))
    }

    @Test
    fun canCompileFile() {
        val file = File("src/test/resources/empty.kor")
        val result = koreander.compile(file, unit)
        assertThat(result, instanceOf(CompiledTemplate::class.java))
    }

    @Test
    fun canRenderCompiledTemplate() {
        val compiled = koreander.compile("", unit)
        val result = koreander.render(compiled, Unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun multipleCompiles() {
        val compiled = koreander.compile("", unit)
        val result = koreander.render(compiled, Unit)
        assertThat(result, instanceOf(String::class.java))

        val compiled2 = koreander.compile("", string)
        val result2 = koreander.render(compiled2, String)
        assertThat(result2, instanceOf(String::class.java))

        val result3 = koreander.render(compiled, Unit)
        assertThat(result3, instanceOf(String::class.java))
    }

    @Test
    fun canRenderCompiledTemplateUnsafe() {
        val compiled = koreander.compile("", unit)
        val result = koreander.render(compiled, Unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun canRenderString() {
        val result = koreander.render("", unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun canRenderURL() {
        val url = javaClass.getResource("/empty.kor")
        val result = koreander.render(url, unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun canRenderFile() {
        val file = File("src/test/resources/empty.kor")
        val result = koreander.render(file, unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun conRenderInputStream() {
        val inputStream: InputStream = "".byteInputStream()
        val result = koreander.render(inputStream, unit)
        assertThat(result, instanceOf(String::class.java))
    }

    @Test
    fun canAddCustomFilter() {
        koreander.filters["test"] = object : KoreanderFilter { override fun filter(input: String) = "out" }
        val result = koreander.render(":test", unit)
        assertThat(result, equalTo("out"))
    }
}