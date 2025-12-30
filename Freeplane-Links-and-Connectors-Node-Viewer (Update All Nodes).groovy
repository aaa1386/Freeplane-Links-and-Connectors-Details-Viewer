// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link_all"})
// aaa1386 - Two-way + تمام نقشه FINAL

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
    if (c.contains("<body>")) {
        def s = c.indexOf("<body>") + 6
        def e = c.indexOf("</body>")
        if (s > 5 && e > s) {
            return c.substring(s, e)
                    .replaceAll("<[^>]+>", "\n")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("\n+", "\n")
                    .trim()
        }
    }
    c
}

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    text.split('\n').find { it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://") }?.trim() ?: "لینک"
}

def getSmartTitle(uri) {
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri + '...'
    def title = parts[0] + '//' + parts[2] + '/'  
    return title + '...'
}

def hasLinks(node) {
    def plainText = extractPlainTextFromNode(node)
    return plainText =~ /https?:\/\/|freeplane:|obsidian:|\[.*https/
}

def processAllLinesToHTML(lines, backwardTitle = null) {
    def result = []
    
    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed) {
            result << line
            return
        }
        
        // Web 🌐
        if (trimmed =~ /^https?:\/\/[^\s]+$/) {
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${trimmed}'>${HtmlUtils.toXMLEscapedText(getSmartTitle(trimmed))}</a></div>"
        }
        // Markdown 🌐
        else if ((trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def mdMatcher = (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            def title = mdMatcher[0][1].trim()
            def uri = mdMatcher[0][2].trim()
            if (!title || title == uri) title = getSmartTitle(uri)
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // Markdown خالی 🌐
        else if ((trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def emptyMatcher = (trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            def uri = emptyMatcher[0][1].trim()
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(getSmartTitle(uri))}</a></div>"
        }
        // URL + Title 🌐
        else if ((trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)) {
            def urlTitleMatcher = (trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)
            def uri = urlTitleMatcher[0][1].trim()
            def title = urlTitleMatcher[0][2].trim()
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // Obsidian 📱
        else if (trimmed.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = (parts.length > 1) ? parts[1]?.trim() : "ابسیدین"
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>📱 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // Freeplane 🔗
        else if (trimmed.startsWith("freeplane:") || trimmed.contains("#")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = backwardTitle ?: ((parts.length > 1) ? parts[1]?.trim() : "لینک")
            if (uri.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode && !backwardTitle) {
                    title = getFirstLineFromText(extractPlainTextFromNode(targetNode))
                }
            }
            result << "<div style='margin-bottom:3px;text-align:right;direction:rtl;'>🔗 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // متن عادی
        else {
            result << trimmed
        }
    }
    
    return result
}

def processSingleNode(node, mode) {
    def plainText = extractPlainTextFromNode(node)
    
    if (!hasLinks(node)) return
    
    // ۱. Freeplane targets پیدا کن
    def freeplaneTargets = []
    plainText.split('\n').each { line ->
        def trimmed = line.trim()
        if (trimmed.startsWith("freeplane:") || trimmed.contains("#")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            if (uri.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                freeplaneTargets << targetId
            }
        }
    }
    
    // ۲. HTML کن
    def lines = plainText.split('\n')
    def htmlLines = processAllLinesToHTML(lines)
    node.text = "<html><body style='direction:rtl;font-family:Tahoma;'>${htmlLines.join('\n')}</body></html>"
    
    // ۳. Two-way
    if (mode == "Two-way" && !freeplaneTargets.isEmpty()) {
        def sourceId = node.id
        def sourceTitle = getFirstLineFromText(plainText)
        
        freeplaneTargets.each { targetId ->
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                def backwardLine = "#${sourceId} ${sourceTitle}"
                def targetPlain = extractPlainTextFromNode(targetNode)
                def targetLines = targetPlain.split('\n') + [backwardLine]
                def targetHTML = processAllLinesToHTML(targetLines, sourceTitle)
                targetNode.text = "<html><body style='direction:rtl;font-family:Tahoma;'>${targetHTML.join('\n')}</body></html>"
            }
        }
    }
}

// ✅ فیکس: همیشه دیالوگ نشون بده
def processAllMap(mode) {
    def processed = 0
    
    c.find { true }.each { node ->
        if (hasLinks(node)) {
            processSingleNode(node, mode)
            processed++
        }
    }
    
    ui.showMessage("✅ ${processed} گره آپدیت شد!", 1)
}

try {
    // ✅ فیکس: همیشه دیالوگ!
    def mode = showSimpleDialog()
    processAllMap(mode)

} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
