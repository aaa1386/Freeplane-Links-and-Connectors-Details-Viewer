// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - FINAL - درست کار می‌کند

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

def showSimpleDialog() {
    Object[] options = ["One-way", "Two-way"]
    JOptionPane.showInputDialog(
        ui.frame,
        "لطفا نوع لینک‌سازی را انتخاب کنید:",
        "انتخاب نوع لینک (تمام نقشه)",
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    )
}

def extractPlainTextFromNode(node) {
    def c = node.text ?: ""
    if (!c.contains("<body>")) return HtmlUtils.htmlToPlain(c)
    
    def s = c.indexOf("<body>") + 6
    def e = c.indexOf("</body>")
    if (s <= 5 || e <= s) return HtmlUtils.htmlToPlain(c)
    
    def htmlContent = c.substring(s, e)
    
    // پیدا کردن تمام <a href=...> بدون شرط اضافی
    def links = []
    def pos = 0
    while (pos < htmlContent.length()) {
        def aStart = htmlContent.indexOf("<a", pos)
        if (aStart == -1) break
        
        def hrefPos = htmlContent.indexOf('href="', aStart)
        if (hrefPos == -1) { pos = aStart + 2; continue }
        
        def hrefEnd = htmlContent.indexOf('"', hrefPos + 6)
        if (hrefEnd == -1) { pos = aStart + 2; continue }
        def url = htmlContent.substring(hrefPos + 6, hrefEnd)
        
        def titleStart = htmlContent.indexOf('>', hrefEnd) + 1
        def titleEnd = htmlContent.indexOf('</a>', titleStart)
        if (titleEnd == -1) { pos = aStart + 2; continue }
        def title = htmlContent.substring(titleStart, titleEnd).trim()
        
        if (title && url.startsWith("http")) {
            links << "[${title}](${url})"
        }
        pos = titleEnd + 4
    }
    
    return links.join('\n')
}

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    text.split('\n')[0]?.trim() ?: "لینک"
}

def getSmartTitle(uri) {
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri.take(40) + '...'
    return "${parts[0]}//${parts[2]}/..."
}

def hasLinks(node) {
    def plainText = extractPlainTextFromNode(node)
    return plainText.contains("[") || plainText.contains("http") || plainText.contains("freeplane:") || plainText.contains("obsidian:")
}

def processAllLinesToHTML(lines, backwardTitle = null, currentNode = null) {
    def result = []
    
    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed) return
        
        // Markdown [title](url)
        if (trimmed.contains("[") && trimmed.contains("](") && trimmed.contains("http")) {
            def startB = trimmed.indexOf('[')
            def endB = trimmed.indexOf(']', startB)
            def startP = trimmed.indexOf('(', endB)
            def endP = trimmed.indexOf(')', startP)
            
            if (startB > -1 && endB > startB && startP > endB && endP > startP) {
                def title = trimmed.substring(startB + 1, endB).trim()
                def uri = trimmed.substring(startP + 1, endP).trim()
                if (!title || title == uri) title = getSmartTitle(uri)
                result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
                return
            }
        }
        
        // Plain URL
        if (trimmed.startsWith("http")) {
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${trimmed}'>${HtmlUtils.toXMLEscapedText(getSmartTitle(trimmed))}</a></div>"
            return
        }
        
        // Obsidian
        if (trimmed.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            def title = parts.length > 1 ? parts[1].trim() : "ابسیدین"
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>📱 <a data-link-type='text' href='${parts[0]}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
            return
        }
        
        // Freeplane
        if (trimmed.startsWith("freeplane:") || trimmed.indexOf('#') > -1) {
            def parts = trimmed.split(' ', 2)
            def title = parts.length > 1 ? parts[1].trim() : "لینک"
            result << "<div style='margin-bottom:3px;text-align:right;'>🔗 <a data-link-type='text' href='${parts[0]}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
            return
        }
        
        result << trimmed
    }
    
    return result
}

def processSingleNode(node, mode) {
    def plainText = extractPlainTextFromNode(node)
    if (!plainText?.trim()) return
    
    // Freeplane targets
    def freeplaneTargets = []
    plainText.split('\n').each { line ->
        if (line.trim().contains("#")) {
            def targetId = line.trim().split(' ')[0].split('#')[1]
            freeplaneTargets << targetId
        }
    }
    
    // HTML
    def lines = plainText.split('\n').findAll { it.trim() }
    def htmlLines = processAllLinesToHTML(lines)
    node.text = "<html><body>${htmlLines.join('\n')}</body></html>"
    
    // Two-way (ساده)
    if (mode == "Two-way" && !freeplaneTargets.isEmpty()) {
        def sourceId = node.id
        def sourceTitle = getFirstLineFromText(plainText)
        freeplaneTargets.each { targetId ->
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                def targetPlain = extractPlainTextFromNode(targetNode)
                def newLine = "#${sourceId} ${sourceTitle}"
                def targetLines = (targetPlain.split('\n') + [newLine]).findAll { it.trim() }
                def targetHTML = processAllLinesToHTML(targetLines)
                targetNode.text = "<html><body>${targetHTML.join('\n')}</body></html>"
            }
        }
    }
}

def processAllMap(mode) {
    def processed = 0
    c.find { true }.each { node ->
        if (hasLinks(node)) {
            processSingleNode(node, mode)
            processed++
        }
    }
    ui.showMessage("✅ ${processed} گره پردازش شد", 1)
}

try {
    def mode = showSimpleDialog()
    processAllMap(mode)
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
