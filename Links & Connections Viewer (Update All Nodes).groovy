// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - FINAL FIXED VERSION

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

// ================= Check URI existence =================
def hasURI(node) {
    def text = node.text ?: ""
    return text.contains("#") || text.contains("freeplane:") || text =~ /https?:\/\//
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

// ================= Extract connectors (اصلاح جهت) =================
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
            // معکوس: start=true end=false → برعکس عمل کن
            if (nodeIsSource) {
                // node → otherNode → ورودی (معکوس)
                if (!grouped['Input'].contains(otherNode))
                    grouped['Input'] << otherNode
            } else {
                // otherNode → node → خروجی (معکوس)
                if (!grouped['Output'].contains(otherNode))
                    grouped['Output'] << otherNode
            }
        }
        else if (!start && end) {
            // معکوس: !start end=true → برعکس عمل کن
            if (nodeIsSource) {
                // otherNode → node → خروجی (معکوس)
                if (!grouped['Output'].contains(otherNode))
                    grouped['Output'] << otherNode
            } else {
                // node → otherNode → ورودی (معکوس)
                if (!grouped['Input'].contains(otherNode))
                    grouped['Input'] << otherNode
            }
        }
        else {
            // بدون فلش
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
            def label =
                (type == 'Input') ? 'Input ←' :
                (type == 'Output') ? 'Output →' :
                                     'Mutual ↔'
            html << "<div style='font-weight:bold;margin:5px 0;text-align:left;direction:rtl;'>${label}:</div>"
            nodes.eachWithIndex { n, i ->
                html << "<div style='margin-right:15px;text-align:right;direction:rtl;'>${i+1}. ${makeLink(n)}</div>"
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

// ================= Extract URI from node text + cleanup =================
def extractTextLinksFromNodeText(node) {
    def freeplaneLinks = []
    def obsidianLinks = []
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
                    title = (parts.length > 1) ? parts[1].trim() : "Replace title from other map"
                }
            } else {
                title = (parts.length > 1) ? parts[1].trim() : "Link"
            }

            freeplaneLinks << [uri: uri, title: title]
        }
        else if (t.startsWith("obsidian://")) {
            def parts = t.split(' ', 2)
            def uri = parts[0]
            def title = (parts.length > 1) ? parts[1].trim() : "Obsidian"
            obsidianLinks << [uri: uri, title: title]
        }
        else if (t) {
            keepLines << t
        }
    }
    node.text = keepLines.join("\n")
    freeplaneLinks + obsidianLinks
}

// ================= Save Details =================
def saveDetails(node, textLinks, connectors) {
    def html = []
    def hasNewCategory = false

    def freeplaneLinks = textLinks.findAll { it.uri.startsWith("freeplane:") || it.uri.startsWith("#") || it.uri =~ /https?:\/\// }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>🔗 Freeplane links:</div>"
        freeplaneLinks.eachWithIndex { l, i ->
            html << "<div style='margin-right:15px;text-align:right;'>${i+1}. " +
                    "<a data-link-type='text' href='${l.uri}'>" +
                    HtmlUtils.toXMLEscapedText(l.title) +
                    "</a></div>"
        }
        hasNewCategory = true
    }

    def obsidianLinks = textLinks.findAll { it.uri.startsWith("obsidian://") }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        if (hasNewCategory) {
            html << "<hr>"
        }
        html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>📱 Obsidian links:</div>"
        obsidianLinks.eachWithIndex { l, i ->
            html << "<div style='margin-right:15px;text-align:right;'>${i+1}. " +
                    "<a data-link-type='text' href='${l.uri}'>" +
                    HtmlUtils.toXMLEscapedText(l.title) +
                    "</a></div>"
        }
        hasNewCategory = true
    }

    def connectorsHTML = generateConnectorsHTML(connectors)
    if (connectorsHTML) {
        if (hasNewCategory) {
            html << "<hr>"
        }
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
    if (!textLinks.any { it.uri == sourceUri }) {
        textLinks << [uri: sourceUri, title: title]
    }

    saveDetails(targetNode, textLinks, extractConnectedNodes(targetNode))
}

// ================= Process single node =================
def processSingleNode(node, mode) {
    def newLinks = extractTextLinksFromNodeText(node)
    def connectors = extractConnectedNodes(node)
    def existingTextLinks = extractTextLinksFromDetails(node)
    def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri }

    saveDetails(node, finalTextLinks, connectors)

    if (mode == "Two-way") {
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
        def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri }

        saveDetails(proxyNode, finalTextLinks, connectors)
    }
}

// ================= Execute =================
try {
    def node = c.selected
    if (!node) return

    def hasUri = hasURI(node)

    def mode
    if (hasUri) {
        mode = showSimpleDialog()
    } else {
        mode = "One-way"
    }

    if (!mode) return

    updateAllConnectors(mode)

} catch (e) {
    ui.showMessage("Error:\n${e.message}", 0)
}
