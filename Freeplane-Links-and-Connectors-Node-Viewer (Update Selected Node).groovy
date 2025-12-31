// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - 4 SLASH //// MARKER v6.3.10 FINAL - Markdown خالی + Smart Title

import org.freeplane.core.util.HtmlUtils

// ================= بررسی وجود URI =================
def checkHasFreeplaneLink(node) {
    def text = node.text ?: ""
    if (!text.contains("<body>")) {
        return text.contains("freeplane:")
    }
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def body = text.substring(s, e)
        return body.contains("freeplane:")
    }
    return false
}

// 🔥 محتوای اصلی خالص (بدون لینک)
def extractMainContent(node) {
    def text = node.text ?: ""
    
    if (!text.contains("<body>")) {
        return text.replaceAll(/https?:\/\/[^\s\n]+/, "")
                  .replaceAll(/\[[^\]]*\]\s*\([^)]+\)/, "")
                  .replaceAll(/freeplane:[^\s\n]+/, "")
                  .replaceAll(/obsidian:\/\/[^\s\n]+/, "")
                  .replaceAll(/\#[^\s\n]+/, "")
                  .trim()
    }
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        def cleanText = htmlContent
            .replaceAll(/<div[^>]*>[\s\n]*[🌐📱🔗↙↗↔]️?[\s\n]*<a[^>]*>.*?<\/a>[\s\n]*<\/div>/, "")
            .replaceAll("<[^>]+>", "\n")
            .replaceAll("&nbsp;", " ")
            .replaceAll("\n+", "\n")
            .trim()
        return cleanText
    }
    
    return text.replaceAll("<[^>]+>", "\n").trim()
}

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    text.split('\n').find { it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://") }?.trim() ?: "لینک"
}

// 🔥 Smart Title - تا سومین اسلش
def getSmartTitle(uri) {
    if (!uri) return "لینک"
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri.take(30) + '...'
    
    def protocol = parts[0]
    def slashes = parts[1] ? '/' : ''
    def domain = parts[2]
    return "${protocol}${slashes}${domain}/..."
}

// 🔥 عنوان از گره مقصد
def getTargetNodeTitle(freeplaneUri) {
    if (!freeplaneUri?.contains("#")) return "لینک"
    
    def targetId = freeplaneUri.substring(freeplaneUri.lastIndexOf('#') + 1)
    def targetNode = c.find { it.id == targetId }.find()
    
    if (targetNode) {
        return getFirstLineFromText(extractMainContent(targetNode))
    }
    return "لینک"
}

// ================= Proxy و Connectors =================
def asProxy(n) {
    (n.metaClass.hasProperty(n, "connectorsIn")) ? n :
        c.find { it.delegate == n }.find()
}

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

def generateConnectorsHTML(grouped) {
    def html = []

    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractMainContent(n))) +
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

// 🔥 استخراج لینک‌های متنی از HTML
def extractTextLinksFromNodeHTML(node) {
    def list = []
    def h = node.text
    if (!h || !h.contains("<body>")) return list
    
    def s = h.indexOf("<body>") + 6
    def e = h.indexOf("</body>")
    if (s <= 5 || e <= s) return list
    
    def body = h.substring(s, e)
    def m = body =~ /<a\s+data-link-type=['"]text['"][^>]*href=['"]([^'"]+)['"][^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}

// 🔥 استخراج ID کانکتورها از HTML
def extractConnectedNodeIdsFromText(node) {
    def connectedIds = []
    def text = node.text ?: ""
    
    if (!text.contains("<body>")) return connectedIds
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        def pattern = /<a\s+[^>]*data-link-type=['"]connector['"][^>]*href=['"]#([^'"]+)['"][^>]*>/
        def matcher = (htmlContent =~ pattern)
        
        matcher.each { match ->
            def nodeId = match[1]
            if (nodeId && !connectedIds.contains(nodeId)) {
                connectedIds << nodeId
            }
        }
    }
    
    return connectedIds
}

// 🔥 حذف مستقیم کانکتور از HTML
def removeConnectorFromHTML(nodeText, sourceId) {
    if (!nodeText?.contains("<body>")) return nodeText
    
    def s = nodeText.indexOf("<body>") + 6
    def e = nodeText.indexOf("</body>")
    if (s <= 5 || e <= s) return nodeText
    
    def htmlContent = nodeText.substring(s, e)
    
    def pattern = /<div[^>]*>[\s\n]*[↙↗↔]️?[\s\n]*<a\s+[^>]*data-link-type=['"]connector['"][^>]*href=['"]#${sourceId}['"][^>]*>.*?<\/a>[\s\n]*<\/div>/ 
    def cleanedHtml = htmlContent.replaceAll(pattern, "")
    
    return nodeText.substring(0, s) + cleanedHtml + nodeText.substring(e)
}

// 🔥 همه لینک‌ها + Markdown خالی []()
def extractTextLinksFromNodeText(node) {
    def freeplaneLinks = []
    def obsidianLinks = []
    def webLinks = []
    def keepLines = []
    
    def lines = node.text.split('\n')
    
    lines.each { l ->
        def trimmed = l.trim()
        if (!trimmed) {
            keepLines << l
            return
        }
        
        def processed = false
        
        // URL تنها
        if (!processed && trimmed =~ /^https?:\/\/[^\s]+$/) {
            webLinks << [uri: trimmed, title: getSmartTitle(trimmed)]
            processed = true
        }
        // Markdown [text](url)
        else if (!processed && (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def mdMatcher = (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            mdMatcher.each { match ->
                def title = match[1].trim()
                def uri = match[2].trim()
                if (!title || title == uri) title = getSmartTitle(uri)
                webLinks << [uri: uri, title: title]
            }
            processed = true
        }
        // 🔥 Markdown خالی []()
        else if (!processed && (trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def emptyMatcher = (trimmed =~ /\[\s*\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            emptyMatcher.each { match ->
                def uri = match[1].trim()
                webLinks << [uri: uri, title: getSmartTitle(uri)]
            }
            processed = true
        }
        // URL + text
        else if (!processed && trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/) {
            def matcher = (trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)
            matcher.each { match ->
                webLinks << [uri: match[1].trim(), title: match[2].trim()]
            }
            processed = true
        }
        // 🔥 freeplane
        else if (!processed && trimmed.startsWith("freeplane:")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0]
            def customTitle = (parts.length > 1) ? parts[1]?.trim() : null
            def title = customTitle ?: getTargetNodeTitle(uri)
            freeplaneLinks << [uri: uri, title: title]
            processed = true
        }
        else if (!processed && trimmed.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            obsidianLinks << [uri: parts[0], title: (parts.length > 1) ? parts[1]?.trim() : "ابسیدین"]
            processed = true
        }
        // #ID connector - حذف
        else if (!processed && trimmed.contains("#") && !trimmed.startsWith("freeplane:")) {
            processed = true
        }
        
        if (!processed) keepLines << l
    }
    
    node.text = keepLines.join('\n')
    return freeplaneLinks + obsidianLinks + webLinks
}

def generateTextLinksHTML(textLinks) {
    def html = []

    def webLinks = textLinks.findAll { 
        def uri = it.uri ?: ""
        uri.startsWith("http://") || uri.startsWith("https://")
    }
    if (webLinks) {
        webLinks.each { l ->
            def titleNow = l.title ?: l.uri
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🌐 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }
    
    def freeplaneLinks = textLinks.findAll { (it.uri ?: "").startsWith("freeplane:") }
    if (freeplaneLinks) {
        freeplaneLinks.each { l ->
            def titleNow = l.title ?: "لینک"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🔗 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }
    
    def obsidianLinks = textLinks.findAll { (it.uri ?: "").startsWith("obsidian://") }
    if (obsidianLinks) {
        obsidianLinks.each { l ->
            def titleNow = l.title ?: "ابسیدین"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>📱 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }
    
    html.join("")
}

def addLinksToNodeText(node, textLinks, connectors) {
    def mainContent = extractMainContent(node)
    def connectorsHTML = generateConnectorsHTML(connectors)
    def textLinksHTML = generateTextLinksHTML(textLinks)
    
    def finalHTML = []
    
    if (mainContent.trim()) {
        finalHTML << "<div style='direction:rtl;font-family:Tahoma;margin-bottom:10px;'>${HtmlUtils.toXMLEscapedText(mainContent)}</div>"
    }
    
    if (textLinksHTML) {
        finalHTML << textLinksHTML
    }
    
    if (connectorsHTML) {
        finalHTML << connectorsHTML
    }
    
    if (finalHTML) {
        node.text = "<html><body>${finalHTML.join('')}</body></html>"
    } else {
        node.text = mainContent
    }
}

def createBackwardTextLink(targetNode, sourceNode, sourceFreeplaneUri) {
    def sourceTitle = getFirstLineFromText(extractMainContent(sourceNode))

    def existingLinks = extractTextLinksFromNodeHTML(targetNode)
    if (existingLinks.any { it.uri == sourceFreeplaneUri }) return

    existingLinks << [uri: sourceFreeplaneUri, title: sourceTitle]
    def targetConnectors = extractConnectedNodes(targetNode)
    addLinksToNodeText(targetNode, existingLinks, targetConnectors)
}

def removeConnectorLinkFromNode(targetNode, sourceNode) {
    def sourceId = sourceNode.id
    def currentText = targetNode.text
    def cleanedText = removeConnectorFromHTML(currentText, sourceId)
    targetNode.text = cleanedText
    
    def textLinks = extractTextLinksFromNodeHTML(targetNode)
    def connectors = extractConnectedNodes(targetNode)
    addLinksToNodeText(targetNode, textLinks, connectors)
}

def updateOtherSideConnectors(centerNode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        
        def textLinks = extractTextLinksFromNodeHTML(proxy)
        def connectors = extractConnectedNodes(proxy)
        addLinksToNodeText(proxy, textLinks, connectors)
    }
}

def processNode() {
    def node = c.selected
    if (!node) return

    def allExistingLinks = extractTextLinksFromNodeHTML(node)
    
    def previousConnectorIds = extractConnectedNodeIdsFromText(node)
    def previouslyConnectedNodes = []
    previousConnectorIds.each { nodeId ->
        def targetNode = c.find { it.id == nodeId }.find()
        if (targetNode && targetNode != node) {
            previouslyConnectedNodes << targetNode
        }
    }

    def newLinks = extractTextLinksFromNodeText(node)
    def connectors = extractConnectedNodes(node)
    
    def finalTextLinks = (allExistingLinks + newLinks).unique { it.uri ?: "" }

    addLinksToNodeText(node, finalTextLinks, connectors)

    // 🔥 Two-way
    finalTextLinks.each { link ->
        def uri = link.uri ?: ""
        if (uri.startsWith("freeplane:") && uri.contains("#")) {
            def targetId = uri.substring(uri.lastIndexOf('#') + 1)
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                createBackwardTextLink(targetNode, node, uri)
            }
        }
    }

    updateOtherSideConnectors(node)
    
    def currentConnected = []
    currentConnected.addAll(connectors['ورودی'] ?: [])
    currentConnected.addAll(connectors['خروجی'] ?: [])
    currentConnected.addAll(connectors['دوطرفه'] ?: [])
    
    def removedConnections = previouslyConnectedNodes.findAll { !currentConnected.contains(it) }
    removedConnections.each { oldConnectedNode ->
        removeConnectorLinkFromNode(oldConnectedNode, node)
    }
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node) return
    
    processNode()
    ui.showMessage("✅ v6.3.10 FINAL - Markdown []() + Smart Title ✅", 1)
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
