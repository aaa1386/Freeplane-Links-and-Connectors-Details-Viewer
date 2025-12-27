// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aj1386 -  ICON ONLY + HR

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

// ================= بررسی وجود URI =================
def hasURI(node) {
    extractPlainTextFromNode(node).split('\n').any { it.trim().startsWith("freeplane:") }
}

// ================= دیالوگ =================
def showSimpleDialog() {
    Object[] options = ["One-way", "Two-way"]
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
    text.split('\n').find { it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://") }?.trim() ?: "لینک"
}

// ================= تبدیل NodeModel → NodeProxy =================
def asProxy(n) {
    (n.metaClass.hasProperty(n, "connectorsIn")) ? n :
        c.find { it.delegate == n }.find()
}

// ================= استخراج کانکتورها (بر اساس جهت فلش) =================
def extractConnectedNodes(node) {
    node = asProxy(node)
    if (!node) return ['ورودی': [], 'خروجی': [], 'دوطرفه': []]

    def nodeId = node.id
    def grouped = ['ورودی': [], 'خروجی': [], 'دوطرفه': []]

    def allConnectors = (node.connectorsIn + node.connectorsOut).unique()

    allConnectors.each { con ->
        def src = con.source?.delegate
        def tgt = con.target?.delegate
        if (!src || !tgt) return

        def srcId = src.id
        def tgtId = tgt.id

        def otherNode
        def nodeIsSource = false

        if (srcId == nodeId) {
            otherNode   = tgt
            nodeIsSource = true
        } else if (tgtId == nodeId) {
            otherNode   = src
        } else {
            return
        }

        if (!otherNode) return

        def start = con.hasStartArrow()
        def end   = con.hasEndArrow()

        if (start && end) {
            if (!grouped['دوطرفه'].contains(otherNode))
                grouped['دوطرفه'] << otherNode
        }
        else if (start && !end) {
            if (nodeIsSource) {
                if (!grouped['ورودی'].contains(otherNode))
                    grouped['ورودی'] << otherNode
            } else {
                if (!grouped['خروجی'].contains(otherNode))
                    grouped['خروجی'] << otherNode
            }
        }
        else if (!start && end) {
            if (nodeIsSource) {
                if (!grouped['خروجی'].contains(otherNode))
                    grouped['خروجی'] << otherNode
            } else {
                if (!grouped['ورودی'].contains(otherNode))
                    grouped['ورودی'] << otherNode
            }
        }
        else {
            if (nodeIsSource) {
                grouped['خروجی'] << otherNode
            } else {
                grouped['ورودی'] << otherNode
            }
        }
    }

    grouped
}

// ================= HTML کانکتورها (عنوان حذف + آیکن) =================
def generateConnectorsHTML(grouped) {
    def html = []

    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextFromNode(n))) +
        "</a>"
    }

    ['ورودی','خروجی','دوطرفه'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {
            def icon = 
                (type == 'ورودی')   ? '↙️ ' :
                (type == 'خروجی')   ? '↗️ ' :
                                       '↔️ '
            nodes.each { n ->
                html << "<div style='margin-right:0px;margin-bottom:3px;text-align:right;direction:rtl;'>${icon}${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}

// ================= لینک‌های متنی =================
def extractTextLinksFromDetails(node) {
    def list = []
    def h = node.detailsText
    if (!h || !h.contains("<body>")) return list
    def body = h.substring(h.indexOf("<body>")+6, h.indexOf("</body>"))
    def m = body =~ /<a\s+data-link-type="text"[^>]*href="([^"]+)"[^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}

// ================= استخراج لینک‌ها از متن گره (یو آر آی حذف می‌شود) =================
def extractTextLinksFromNodeText(node) {
    def freeplaneLinks = []
    def obsidianLinks = []
    def keepLines = []

    extractPlainTextFromNode(node).split('\n').each { l ->
        def t = l.trim()
        if (t?.startsWith("freeplane:")) {
            def parts = t.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = null

            if (uri?.contains("#")) {
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

            freeplaneLinks << [uri: uri, title: title]
        } 
        else if (t?.startsWith("obsidian://")) {
            def parts = t.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = (parts.length > 1) ? parts[1].trim() : "ابسیدین"
            obsidianLinks << [uri: uri, title: title]
        }
        else if (t) {
            keepLines << t
        }
    }
    node.text = keepLines.join("\n")
    freeplaneLinks + obsidianLinks
}

// ============== کمک برای آپدیت عنوان لینک‌ها بر اساس گره مقصد ==============
def resolveTitleForLink(link) {
    def uri = link.uri ?: ""
    if (uri && (uri.startsWith("freeplane:") || uri.startsWith("#"))) {
        if (uri.contains("#")) {
            def targetId = uri.substring(uri.lastIndexOf('#') + 1)
            if (targetId) {
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    return getFirstLineFromText(extractPlainTextFromNode(targetNode))
                }
            }
        }
    }
    return link.title ?: "لینک"
}

// ================= ذخیره Details (عنوان حذف + آیکن + خط) =================
def saveDetails(node, textLinks, connectors) {
    def html = []

    // Freeplane Links
    def freeplaneLinks = textLinks.findAll { (it.uri ?: "").startsWith("freeplane:") || (it.uri ?: "").startsWith("#") }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        freeplaneLinks.each { l ->
            def titleNow = resolveTitleForLink(l)
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🔗 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
        html << "<hr>"
    }
    
    // Obsidian Links
    def obsidianLinks = textLinks.findAll { (it.uri ?: "").startsWith("obsidian://") }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        obsidianLinks.each { l ->
            def titleNow = l.title ?: "ابسیدین"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>📱 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
        html << "<hr>"
    }
    
    // Connectors
    def connectorsHTML = generateConnectorsHTML(connectors)
    if (connectorsHTML) {
        html << connectorsHTML
    }
    
    if (html && !html.isEmpty()) {
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
    def sourceTitle = getFirstLineFromText(extractPlainTextFromNode(sourceNode))

    def textLinks = extractTextLinksFromDetails(targetNode)
    if (textLinks.any { (it.uri ?: "") == sourceUri }) return

    textLinks << [uri: sourceUri, title: sourceTitle]
    saveDetails(targetNode, textLinks, extractConnectedNodes(targetNode))
}

// ================= آپدیت کانکتورهای طرف مقابل =================
def updateOtherSideConnectors(centerNode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        saveDetails(
            proxy,
            extractTextLinksFromDetails(proxy),
            extractConnectedNodes(proxy)
        )
    }
}

// ================= پردازش گره =================
def processNode(mode) {
    def node = c.selected
    if (!node) return

    def newLinks = extractTextLinksFromNodeText(node)
    def connectors = extractConnectedNodes(node)
    def existingTextLinks = extractTextLinksFromDetails(node)
    def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri ?: "" }

    saveDetails(node, finalTextLinks, connectors)

    if (mode == "Two-way") {
        newLinks.each { link ->
            def uri = link.uri ?: ""
            if (uri.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#') + 1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode && targetNode != node) {
                    createBackwardTextLink(targetNode, node)
                }
            }
        }
    }

    updateOtherSideConnectors(node)
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node || !hasURI(node)) {
        processNode("One-way")
    } else {
        def mode = showSimpleDialog()
        if (mode) {
            processNode(mode)
        }
    }
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
