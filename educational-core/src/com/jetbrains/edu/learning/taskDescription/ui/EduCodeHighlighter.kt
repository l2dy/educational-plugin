package com.jetbrains.edu.learning.taskDescription.ui

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class EduCodeHighlighter {

  companion object {
    @JvmStatic
    fun highlightCodeFragments(project: Project, html: String, defaultLanguage: Language): String {
      val document = Jsoup.parse(html)

      val codeElements = document.select("code")

      for (codeElement in codeElements) {
        val textNode = codeElement.childNodes().singleOrNull() as? TextNode ?: continue
        val text = textNode.wholeText
        val language = codeElement.language() ?: defaultLanguage

        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(language, "")
        if (psiFile == null) return html

        val codeText = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(psiFile, text)

        val parent = codeElement.parent()
        // We have to check `parent.parent()` for null
        // because in case of incomplete code `parent.parent()` can be null
        // and in this case `parent.after(codeText)` throws `IllegalArgumentException`
        @Suppress("SENSELESS_COMPARISON")
        if (parent.tagName() == "pre" && parent.parent() != null) {
          parent.after("<span class='code-block'>$codeText</span>")
          parent.remove()
        } else {
          val inlineCodeText = codeText.trim().removeSurrounding("<pre>", "</pre>")
          codeElement.after("<span class='code'>$inlineCodeText</span>")
          codeElement.remove()
        }
      }

      return document.toString()
    }

    private fun Element.language(): Language? {
      val noHighlight = "no-highlight"

      val lang = when {
        hasAttr("data-lang") -> attr("data-lang").removePrefix("text/x-")
        attr("class").startsWith("language-") -> attr("class").removePrefix("language-")
        attr("class") == noHighlight -> return PlainTextLanguage.INSTANCE
        else -> return null
      }
      if (lang.isEmpty()) return null

      return if (lang == noHighlight) PlainTextLanguage.INSTANCE else Language.getRegisteredLanguages().find { it.id.toLowerCase() == lang }
    }
  }
}
