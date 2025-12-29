// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - ICON ONLY + HR + //// MARKER v2 - NO MAP MESSAGE

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

// ================= Check URI existence in WHOLE MAP =================
def hasURI() {
    def allNodes = c.find { true }
    allNodes.any { node ->
        def text = node.text ?: ""
        text.contains("#") || text.contains("freeplane:") || text =~ /https?:\/\//
    }
}

// ================= Dialog =================
def showSimpleDialog() {
    Object[] options = ["One-way", "Two-way"]
    JOptionPane.showInputDialog(
        ui.frame,
        "Please select link type:",
        "Select Link Type",
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    )
}

// ================= Raw text =================
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
    if (!text) return "Link"
    text.split('\n').find {
        it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://")
    }?.trim() ?: "Link"
}

// ================= NodeModel → NodeProxy =================
def asProxy(n) {
    (n.metaClass.hasProperty(n, "connectorsIn")) ? n :
        c.find { it.delegate == n }.find()
}

// ================= Extract connectors =================
def extractConnectedNodes(node) {
    node = asProxy(node)
    if (!node) return ['Input':[], 'Output':[], 'Bidirectional':[]]

    def nodeId = node.id
    def grouped = ['Input': [], 'Output': [], 'Bidirectional': []]

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
            otherNode = tgt
            nodeIsSource = true
        } else if (tgtId == nodeId) {
            otherNode = src
        } else {
            return
        }

        if (!otherNode) return

        def start = con.hasStartArrow()
        def end   = con.hasEndArrow()

        if (start && end) {
            if (!grouped['Bidirectional'].contains(otherNode))
                grouped['Bidirectional'] << otherNode
        } 
        else if (start && !end) {
            if (nodeIsSource) {
                if (!grouped['Input'].contains(otherNode))
                    grouped['Input'] << otherNode
            } else {
                if (!grouped['Output'].contains(otherNode))
                    grouped['Output'] << otherNode
            }
        }
        else if (!start && end) {
            if (nodeIsSource) {
                if (!grouped['Output'].contains(otherNode))
                    grouped['Output'] << otherNode
            } else {
                if (!grouped['Input'].contains(otherNode))
                    grouped['Input'] << otherNode
            }
        }
        else {
            if (nodeIsSource) {
                grouped['Output'] << otherNode
            } else {
                grouped['Input'] << otherNode
            }
        }
    }
    grouped
}

// ================= Connector HTML =================
def generateConnectorsHTML(grouped) {
    def html = []

    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(
            getFirstLineFromText(extractPlainTextFromNode(n))
        ) +
        "</a>"
    }

    ['Input','Output','Bidirectional'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {
            def icon =
                (type == 'Input')  ? '↙️ ' :
                (type == 'Output') ? '↗️ ' :
                                     '↔️ '
            nodes.each { n ->
                html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>${icon}${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}

// ================= Text links from Details =================
def extractTextLinksFromDetails(node) {
    def list = []
    def h = node.detailsText
    if (!h || !h.contains("<body>")) return list
    def body = h.substring(h.indexOf("<body>")+6, h.indexOf("</body>"))
    def m = body =~ /<a\s+data-link-type="text"[^>]*href="([^"]+)"[^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}

// ================= Smart Title =================
def getSmartTitle(uri) {
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri + '...'
    def title = parts[0] + '//' + parts[2] + '/'  
    return title + '...'
}

// ================= Extract links - فقط بعد از //// =================
def extractTextLinksFromNodeText(node) {
    def freeplaneLinks = []
    def obsidianLinks = []
    def webLinks = []
    def keepLines = []
    def processSection = false  // قبل از //// خاموش

    def lines = node.text.split('\n')
    
    lines.each { l ->
        def trimmed = l.trim()
        
        // علامت شروع: ////
        if (trimmed == "////") {
            processSection = true
            keepLines << l
            return
        }
        
        // قبل از //// : هیچ پردازشی نکن
        if (!processSection) {
            keepLines << l
            return
        }
        
        // بعد از //// : پردازش عادی
        if (!trimmed) {
            keepLines << l
            return
        }
        
        def processed = false
        
        // 0. URL ساده 🌐
        if (!processed && trimmed =~ /^https?:\/\/[^\s]+$/) {
            webLinks << [uri: trimmed, title: getSmartTitle(trimmed)]
            processed = true
        }
        
        // 1. Markdown: [title](url) 🌐
        else if (!processed && (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def mdMatcher = (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            mdMatcher.each { match ->
                def title = match[1].trim()
                def uri = match[2].trim()
                if (!title || title == uri) {
                    title = getSmartTitle(uri)
                }
                webLinks << [uri: uri, title: title]
            }
            processed = true
        }
        
        // 2. Markdown خالی: [](url) 🌐
        else if (!processed && (trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def emptyMatcher = (trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            emptyMatcher.each { match ->
                def uri = match[1].trim()
                webLinks << [uri: uri, title: getSmartTitle(uri)]
            }
            processed = true
        }
        
        // 3. Markdown + Title 🌐
        else if (!processed && trimmed =~ /\[([^\]]*)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)\s+(.+)/) {
            def matcher = (trimmed =~ /\[([^\]]*)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)\s+(.+)/)
            matcher.each { match ->
                def uri = match[2].trim()
                def title = match[3].trim()
                webLinks << [uri: uri, title: title]
            }
            processed = true
        }
        
        // 4. URL + Title 🌐
        else if (!processed && trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/) {
            def matcher = (trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)
            matcher.each { match ->
                def uri = match[1].trim()
                def title = match[2].trim()
                webLinks << [uri: uri, title: title]
            }
            processed = true
        }
        
        // 5. Freeplane 🔗
        else if (!processed && (trimmed?.startsWith("freeplane:") || trimmed?.contains("#"))) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = null

            if (uri?.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    title = getFirstLineFromText(extractPlainTextFromNode(targetNode))
                } else {
                    title = (parts.length > 1) ? parts[1]?.trim() : "Replace title from other map"
                }
            } else {
                title = (parts.length > 1) ? parts[1]?.trim() : "Link"
            }

            freeplaneLinks << [uri: uri, title: title]
            processed = true
        }
        
        // 6. Obsidian 📱
        else if (!processed && trimmed?.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = (parts.length > 1) ? parts[1]?.trim() : "Obsidian"
            obsidianLinks << [uri: uri, title: title]
            processed = true
        }
        
        if (!processed) {
            keepLines << l
        }
    }
    
    node.text = keepLines.join('\n')
    return freeplaneLinks + obsidianLinks + webLinks
}

// ============== Resolve link titles ==============
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
    return link.title ?: "Link"
}

// ================= Save Details =================
def saveDetails(node, textLinks, connectors) {
    def html = []

    // Web Links 🌐 - اول
    def webLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("http://") || u.startsWith("https://")
    }
    if (webLinks && !webLinks.isEmpty()) {
        webLinks.each { l ->
            def titleNow = l.title ?: l.uri
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🌐 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }

    // Freeplane Links 🔗
    def freeplaneLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("freeplane:") || u.startsWith("#")
    }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        freeplaneLinks.each { l ->
            def titleNow = resolveTitleForLink(l)
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🔗 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }

    // Obsidian Links 📱
    def obsidianLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("obsidian://")
    }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        obsidianLinks.each { l ->
            def titleNow = l.title ?: "Obsidian"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>📱 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
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

// ================= Backward text link =================
def createBackwardTextLink(targetNode, sourceNode) {
    def sourceUri = "#${sourceNode.id}"
    def title = getFirstLineFromText(extractPlainTextFromNode(sourceNode))

    def textLinks = extractTextLinksFromDetails(targetNode)
    if (!textLinks.any { (it.uri ?: "") == sourceUri }) {
        textLinks << [uri: sourceUri, title: title]
    }

    saveDetails(targetNode, textLinks, extractConnectedNodes(targetNode))
}

// ================= Process single node =================
def processSingleNode(node, mode) {
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
}

// ================= Full map update =================
def updateAllConnectors(mode) {
    def node = c.selected
    if (!node) return

    processSingleNode(node, mode)

    def allNodes = c.find { true }
    allNodes.each { n ->
        def proxyNode = asProxy(n)
        if (!proxyNode || proxyNode == node) return

        def newLinks = extractTextLinksFromNodeText(proxyNode)
        def connectors = extractConnectedNodes(proxyNode)
        def existingTextLinks = extractTextLinksFromDetails(proxyNode)
        def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri ?: "" }

        saveDetails(proxyNode, finalTextLinks, connectors)
    }
}

// ================= Execute =================
try {
    def node = c.selected
    if (!node) return

    def hasAnyUri = hasURI()

    def mode
    if (hasAnyUri) {
        mode = showSimpleDialog()
    } else {
        mode = "One-way"
    }

    if (!mode) return

    updateAllConnectors(mode)
    // پیام حذف شد ✅

} catch (e) {
    ui.showMessage("Error:\n${e.message}", 0)
}
