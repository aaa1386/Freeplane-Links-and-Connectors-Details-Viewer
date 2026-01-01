// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - 4 SLASH //// MARKER v6 - FIXED
// MODIFIED: Added two-way synchronization for removed links

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

// ================= Smart Title =================
def getSmartTitle(uri) {
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri + '...'
    def title = parts[0] + '//' + parts[2] + '/'  
    return title + '...'
}

// ================= بقیه توابع بدون تغییر =================
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

def extractTextLinksFromDetails(node) {
    def list = []
    def h = node.detailsText
    if (!h || !h.contains("<body>")) return list
    def body = h.substring(h.indexOf("<body>")+6, h.indexOf("</body>"))
    def m = body =~ /<a\s+data-link-type="text"[^>]*href="([^"]+)"[^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}

// ================= استخراج لینک‌ها - همه متن =================
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
        
        // 0. URL ساده 🌐
        if (!processed && trimmed =~ /^https?:\/\/[^\s]+$/) {
            def uri = trimmed
            webLinks << [uri: uri, title: getSmartTitle(uri)]
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
        
        // 2. Markdown خالی:  🌐
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
        
        // 5. Freeplane 🔗 - FIXED: فقط لینک‌های معتبر Freeplane
        else if (!processed && (trimmed?.startsWith("freeplane:") || trimmed?.startsWith("#"))) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = null

            if (uri?.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    title = getFirstLineFromText(extractPlainTextFromNode(targetNode))
                } else {
                    title = (parts.length > 1) ? parts[1]?.trim() : "عنوان را از نقشه دیگر جایگزین کن"
                }
            } else {
                title = (parts.length > 1) ? parts[1]?.trim() : "لینک"
            }

            freeplaneLinks << [uri: uri, title: title]
            processed = true
        }
        
        // 6. Obsidian 📱
        else if (!processed && trimmed?.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = (parts.length > 1) ? parts[1]?.trim() : "ابسیدین"
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

// ============== بقیه توابع بدون تغییر (مختصر) ==============
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

// ================= Save Details با پشتیبانی از حالت‌های مختلف =================
def saveDetails(node, textLinks, connectors, mode, isSource = true) {
    def html = []

    def webLinks = textLinks.findAll { 
        def uri = it.uri ?: ""
        uri.startsWith("http://") || uri.startsWith("https://")
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
    
    // Freeplane Links با آیکن‌های مختلف بر اساس mode
    def freeplaneLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("freeplane:") || u.startsWith("#")
    }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        freeplaneLinks.each { l ->
            def titleNow = resolveTitleForLink(l)
            def icon
            if (mode == "Two-way") {
                icon = "🔗↔️ "
            } else {
                // حالت یک طرفه
                icon = isSource ? "🔗↗️ " : "🔗🔙 "
            }
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>${icon}" +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }
    
    def obsidianLinks = textLinks.findAll { (it.uri ?: "").startsWith("obsidian://") }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        obsidianLinks.each { l ->
            def titleNow = l.title ?: "ابسیدین"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>📱 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }
    
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

// ================= ایجاد لینک بازگشتی همیشه =================
def createBackwardLinkInTarget(targetNode, sourceNode, mode) {
    def sourceUri = "#${sourceNode.id}"
    def sourceTitle = getFirstLineFromText(extractPlainTextFromNode(sourceNode))
    
    // استخراج لینک‌های موجود از مقصد
    def existingLinks = extractTextLinksFromDetails(targetNode)
    
    // بررسی وجود لینک بازگشتی
    def linkExists = false
    existingLinks.each { link ->
        if (link.uri == sourceUri) {
            linkExists = true
            link.title = sourceTitle  // به‌روزرسانی عنوان
        }
    }
    
    // اگر لینک وجود ندارد، اضافه کن
    if (!linkExists) {
        existingLinks << [uri: sourceUri, title: sourceTitle]
    }
    
    // ذخیره جزئیات با آیکن مناسب (گره مقصد = isSource = false)
    def connectors = extractConnectedNodes(targetNode)
    saveDetails(targetNode, existingLinks, connectors, mode, false)
}

// ================= حذف لینک بازگشتی از گره مقصد =================
def removeBackwardLinkFromTarget(targetNode, sourceNode, mode) {
    def sourceUri = "#${sourceNode.id}"
    
    // استخراج لینک‌های موجود از مقصد
    def existingLinks = extractTextLinksFromDetails(targetNode)
    
    // فیلتر کردن لینک‌ها (حذف لینک به منبع)
    def filteredLinks = existingLinks.findAll { link ->
        link.uri != sourceUri
    }
    
    // ذخیره جزئیات بدون لینک حذف شده
    def connectors = extractConnectedNodes(targetNode)
    
    // بررسی اینکه آیا این گره هنوز مبدا لینک‌هایی است یا خیر
    def isSource = filteredLinks.any { it.uri?.contains("#") && it.uri != sourceUri }
    
    saveDetails(targetNode, filteredLinks, connectors, mode, isSource)
}

// ================= Process node با همگام‌سازی دوطرفه =================
def processNode(mode) {
    def node = c.selected
    if (!node) return

    // 1. استخراج لینک‌های فعلی از جزئیات گره
    def existingLinks = extractTextLinksFromDetails(node)
    
    // 2. استخراج لینک‌های جدید از متن گره
    def newLinks = extractTextLinksFromNodeText(node)
    
    // 3. شناسایی لینک‌های حذف شده (لینک‌های Freeplane که در existingLinks بودند اما در newLinks نیستند)
    def removedFreeplaneLinks = existingLinks.findAll { existingLink ->
        def uri = existingLink.uri ?: ""
        // فقط لینک‌های Freeplane که به گره‌های دیگر اشاره می‌کنند
        if (uri.contains("#") && (uri.startsWith("freeplane:") || uri.startsWith("#"))) {
            // بررسی اینکه آیا این لینک در newLinks وجود دارد
            def stillExists = newLinks.any { newLink ->
                newLink.uri == uri
            }
            return !stillExists
        }
        return false
    }
    
    // 4. حذف لینک‌های بازگشتی از گره‌های مقصد
    removedFreeplaneLinks.each { removedLink ->
        def uri = removedLink.uri ?: ""
        if (uri.contains("#")) {
            def targetId = uri.substring(uri.lastIndexOf('#') + 1)
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                removeBackwardLinkFromTarget(targetNode, node, mode)
            }
        }
    }
    
    // 5. استخراج connectorها
    def connectors = extractConnectedNodes(node)
    
    // 6. ترکیب لینک‌های قدیمی و جدید
    def finalTextLinks = []
    
    // اول لینک‌های جدید
    newLinks.each { newLink ->
        def found = false
        existingLinks.each { existingLink ->
            if (existingLink.uri == newLink.uri) {
                found = true
                // به‌روزرسانی عنوان
                existingLink.title = newLink.title ?: existingLink.title
            }
        }
        if (!found) {
            finalTextLinks << newLink
        }
    }
    
    // اضافه کردن لینک‌های قدیمی که در جدید نیستند و Freeplane نیستند
    existingLinks.each { existingLink ->
        def isFreeplaneLink = existingLink.uri?.contains("#") && 
                             (existingLink.uri?.startsWith("freeplane:") || existingLink.uri?.startsWith("#"))
        
        if (!isFreeplaneLink) {
            def found = false
            newLinks.each { newLink ->
                if (newLink.uri == existingLink.uri) {
                    found = true
                }
            }
            if (!found) {
                finalTextLinks << existingLink
            }
        }
    }
    
    // 7. ذخیره در مبدا (گره منبع)
    saveDetails(node, finalTextLinks, connectors, mode, true)
    
    // 8. برای هر لینک Freeplane جدید، حتما لینک بازگشتی ایجاد کن
    newLinks.each { link ->
        def uri = link.uri ?: ""
        if (uri.contains("#")) {
            def targetId = uri.substring(uri.lastIndexOf('#') + 1)
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                createBackwardLinkInTarget(targetNode, node, mode)
            }
        }
    }
    
    // 9. به‌روزرسانی connectorهای گره‌های دیگر
    updateOtherSideConnectors(node, mode)
}

// ================= Update other side connectors با mode =================
def updateOtherSideConnectors(centerNode, mode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        
        // استخراج لینک‌های موجود
        def existingLinks = extractTextLinksFromDetails(proxy)
        def connectors = extractConnectedNodes(proxy)
        
        // تشخیص اینکه آیا این گره مبدا لینکی است یا مقصد
        def isSource = existingLinks.any { it.uri?.contains("#") }
        
        // ذخیره جزئیات
        saveDetails(proxy, existingLinks, connectors, mode, isSource)
    }
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node) return
    
    def plainText = extractPlainTextFromNode(node)
    def hasFreeplaneLink = plainText.contains("freeplane:") || plainText.contains("#")
    
    def mode
    if (hasFreeplaneLink) {
        mode = showSimpleDialog()
    } else {
        mode = "One-way"
    }
    
    if (mode) {
        processNode(mode)
        // ui.showMessage("✅ همه لینک‌های متن پردازش شد", 1)
    }
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
