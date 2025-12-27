// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aj1386

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

// ================= چک کردن وجود URI =================
def hasURI(node) {
    def text = node.text ?: ""
    return text.contains("#") || text.contains("freeplane:") || text =~ /https?:\/\//
}

// ================= دیالوگ =================
def showSimpleDialog() {
    Object[] options = ["یک طرفه", "دو طرفه"]
    JOptionPane.showInputDialog(
        ui.frame,
        "لطفا نوع لینک‌سازی را انتخاب کنید:",
        "انتخاب نوع لینک",
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    )
}

// ================= متن خام =================
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
    text.split('\n').find {
        it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://")
    }?.trim() ?: "لینک"
}

// ================= تبدیل NodeModel → NodeProxy =================
def asProxy(n) {
    (n.metaClass.hasProperty(n, "connectorsIn")) ? n :
        c.find { it.delegate == n }.find()
}

// ================= استخراج کانکتورها =================
def extractConnectedNodes(node) {
    node = asProxy(node)
    if (!node) return ['ورودی':[], 'خروجی':[], 'دوطرفه':[]]

    def map = [:]
    node.connectorsIn.each {
        map[it.source.delegate] = (map[it.source.delegate] ?: []) + "ورودی"
    }
    node.connectorsOut.each {
        map[it.target.delegate] = (map[it.target.delegate] ?: []) + "خروجی"
    }

    def grouped = ['ورودی': [], 'خروجی': [], 'دوطرفه': []]
    map.each { n, types ->
        if (types.contains("ورودی") && types.contains("خروجی"))
            grouped['دوطرفه'] << n
        else if (types.contains("ورودی"))
            grouped['ورودی'] << n
        else if (types.contains("خروجی"))
            grouped['خروجی'] << n
    }
    grouped
}

// ================= HTML کانکتورها =================
def generateConnectorsHTML(grouped) {
    def html = []
    
    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(
            getFirstLineFromText(extractPlainTextFromNode(n))
        ) +
        "</a>"
    }

    ['ورودی','خروجی','دوطرفه'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {
            html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>گره‌های ${type}:</div>"
            nodes.eachWithIndex { n, i ->
                html << "<div style='margin-right:15px;text-align:right;direction:rtl;'>${i+1}. ${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}

// ================= لینک‌های متنی از Details =================
def extractTextLinksFromDetails(node) {
    def list = []
    def h = node.detailsText
    if (!h || !h.contains("<body>")) return list
    def body = h.substring(h.indexOf("<body>")+6, h.indexOf("</body>"))
    def m = body =~ /<a\s+data-link-type="text"[^>]*href="([^"]+)"[^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}

// ================= استخراج URI از متن گره + پاکسازی =================
def extractTextLinksFromNodeText(node) {
    def links = []
    def keepLines = []

    extractPlainTextFromNode(node).split('\n').each { l ->
        def t = l.trim()
        if (t.startsWith("freeplane:") || t.contains("#") || t =~ /https?:\/\//) {
            def parts = t.split(' ', 2)
            def uri = parts[0]
            def title = null

            if (uri.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    title = getFirstLineFromText(extractPlainTextFromNode(targetNode))
                } else {
                    title = (parts.length > 1) ? parts[1].trim() : "عنوان را از نقشه دیگر جایگزین کن"
                }
            } else {
                title = (parts.length > 1) ? parts[1].trim() : "لینک"
            }

            links << [uri: uri, title: title]
        } 
        // ✅ Obsidian URI
        else if (t.startsWith("obsidian://")) {
            def parts = t.split(' ', 2)
            def uri = parts[0]
            def title = (parts.length > 1) ? parts[1].trim() : "ابسیدین"
            links << [uri: uri, title: title]
        }
        else if (t) {
            keepLines << t
        }
    }
    // URI ها حذف و متن پاکسازی شده ذخیره می‌شود
    node.text = keepLines.join("\n")
    links
}

// ================= ذخیره Details =================
def saveDetails(node, textLinks, connectors) {
    def html = []
    def hasContent = false
    
    if (textLinks && !textLinks.isEmpty()) {
        html << "<div style='font-weight:bold;text-align:right;'>لینک‌ها:</div>"
        textLinks.eachWithIndex { l, i ->
            html << "<div style='margin-right:15px;text-align:right;'>${i+1}. " +
                    "<a data-link-type='text' href='${l.uri}'>" +
                    HtmlUtils.toXMLEscapedText(l.title) +
                    "</a></div>"
        }
        html << "<hr>"
        hasContent = true
    }
    
    def connectorsHTML = generateConnectorsHTML(connectors)
    if (connectorsHTML.trim()) {
        html << connectorsHTML
        hasContent = true
    }
    
    if (hasContent) {
        node.details = "<html><body style='direction:rtl;'>${html.join("")}</body></html>"
        node.detailsContentType = "html"
    } else {
        node.details = null
        node.detailsContentType = null
    }
}

// ================= لینک برگشتی متنی =================
def createBackwardTextLink(targetNode, sourceNode) {
    def sourceUri = "#${sourceNode.id}"
    def title = getFirstLineFromText(extractPlainTextFromNode(sourceNode))

    def textLinks = extractTextLinksFromDetails(targetNode)
    if (!textLinks.any { it.uri == sourceUri }) {
        textLinks << [uri: sourceUri, title: title]
    }

    saveDetails(targetNode, textLinks, extractConnectedNodes(targetNode))
}

// ================= پردازش تک گره =================
def processSingleNode(node, mode) {
    def newLinks = extractTextLinksFromNodeText(node)
    def connectors = extractConnectedNodes(node)
    def existingTextLinks = extractTextLinksFromDetails(node)
    def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri }

    saveDetails(node, finalTextLinks, connectors)

    if (mode == "دو طرفه") {
        newLinks.each { link ->
            if (link.uri.contains("#")) {
                def targetId = link.uri.substring(link.uri.lastIndexOf('#') + 1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode && targetNode != node) {
                    createBackwardTextLink(targetNode, node)
                }
            }
        }
    }
}

// ================= آپدیت کامل نقشه (URI + Obsidian همه گره‌ها) =================
def updateAllConnectors(mode) {
    def node = c.selected
    if (!node) return
    
    // ✅ اول گره انتخاب شده (mode اعمال)
    processSingleNode(node, mode)
    
    // ✅ همه گره‌های نقشه → URI ها + Obsidian استخراج + پاکسازی
    def allNodes = c.find { true }
    allNodes.each { n ->
        def proxyNode = asProxy(n)
        if (!proxyNode || proxyNode == node) return  // گره انتخاب شده قبلاً پردازش شد
        
        // ✅ برای همه: URI ها + Obsidian استخراج
        def newLinks = extractTextLinksFromNodeText(proxyNode)
        def connectors = extractConnectedNodes(proxyNode)
        def existingTextLinks = extractTextLinksFromDetails(proxyNode)
        def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri }
        
        saveDetails(proxyNode, finalTextLinks, connectors)
    }
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node) return
    
    // ✅ فقط اگر URI در گره انتخابی باشد → دیالوگ
    def hasUri = hasURI(node)
    
    def mode
    if (hasUri) {
        mode = showSimpleDialog()
    } else {
        mode = "یک طرفه"  // مستقیم اجرا بدون دیالوگ
    }
    
    if (!mode) return
    
    updateAllConnectors(mode)
    
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
