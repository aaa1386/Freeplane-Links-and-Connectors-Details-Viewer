// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - v7.6.2 FIXED - حفظ کامل لینک‌های HTML ✅

import org.freeplane.core.util.HtmlUtils

// ================= توابع کمکی =================

// 🔥 استخراج SMART متن خام - فقط لینک‌های کانکتوری را حذف کن
def extractPlainTextForProcessing(node) {
    def text = node.text ?: ""
    if (text.contains("<body>")) {
        def s = text.indexOf("<body>") + 6
        def e = text.indexOf("</body>")
        if (s > 5 && e > s) {
            def htmlContent = text.substring(s, e)
            
            // فقط لینک‌های کانکتوری (با آیکن فلش) را حذف کن
            // لینک‌های متنی (🌐📱🔗) را حفظ کن
            def processed = htmlContent.replaceAll(
                /<div style=['"]margin-bottom: 3px; text-align: right['"]>[\s\n]*[↙↗↔]️?[\s\n]*<a[^>]*data-link-type=['"]connector['"][^>]*>.*?<\/a>[\s\n]*<\/div>/,
                ''
            )
            
            // حالا HTML را به متن تبدیل کن (اما لینک‌های <a> باقی می‌مانند)
            def plainText = processed
                .replaceAll(/<div[^>]*>(.*?)<\/div>/, '$1\n')
                .replaceAll(/<br\/?>/, '\n')
                .replaceAll(/<[^>]+>/, '') // فقط تگ‌های دیگر حذف شوند
                .replaceAll(/&nbsp;/, ' ')
                .replaceAll(/\n\n+/, '\n')
                .trim()
            
            // 🔥 فیلتر کردن کامنت‌ها و کد اسکریپت
            def filteredLines = plainText.split('\n')
                .collect { it.trim() }
                .findAll { 
                    it && 
                    !it.startsWith("//") && 
                    !it.startsWith("@ExecutionModes") &&
                    !it.startsWith("import ") &&
                    !it.startsWith("def ") &&
                    !it.startsWith("try {") &&
                    !it.startsWith("catch ")
                }
            
            return filteredLines.join('\n').trim()
        }
    }
    
    // 🔥 برای متن ساده بدون HTML هم فیلتر اعمال کن
    if (text) {
        def filteredLines = text.split('\n')
            .collect { it.trim() }
            .findAll { 
                it && 
                !it.startsWith("//") && 
                !it.startsWith("@ExecutionModes") &&
                !it.startsWith("import ") &&
                !it.startsWith("def ") &&
                !it.startsWith("try {") &&
                !it.startsWith("catch ")
            }
        return filteredLines.join('\n').trim()
    }
    
    return text
}

// 🔥 تابع جدید: بررسی وجود لینک به گره خاص
def hasLinkToNode(targetNode, sourceNodeId) {
    def text = targetNode.text ?: ""
    
    // بررسی در متن ساده
    def plainText = extractPlainTextForProcessing(targetNode)
    if (plainText.contains("#${sourceNodeId}")) {
        return true
    }
    
    // بررسی در HTML (لینک‌های ساخته شده)
    if (text.contains("<body>")) {
        def s = text.indexOf("<body>") + 6
        def e = text.indexOf("</body>")
        if (s > 5 && e > s) {
            def htmlContent = text.substring(s, e)
            
            // جستجوی لینک‌های متنی که به sourceNodeId اشاره می‌کنند
            if (htmlContent.contains("#${sourceNodeId}")) {
                return true
            }
            
            // جستجوی لینک‌های کانکتوری
            def pattern = /<a\s+[^>]*data-link-type=['"]connector['"][^>]*href=['"]#${sourceNodeId}['"][^>]*>/
            if (htmlContent =~ pattern) {
                return true
            }
        }
    }
    
    return false
}

// 🔥 تابع جدید: استخراج محتوای واقعی گره - نسخه تصحیح شده
def extractNodeContent(node) {
    def result = []
    def text = node.text ?: ""
    
    // اگر متن حاوی HTML است
    if (text.contains("<body>")) {
        try {
            def s = text.indexOf("<body>") + 6
            def e = text.indexOf("</body>")
            if (s > 5 && e > s) {
                def htmlContent = text.substring(s, e)
                
                // 🔥 KEY FIX: خطوط HTML را جداگانه پردازش کن
                def lines = htmlContent.split('\n')
                
                lines.each { line ->
                    def trimmed = line.trim()
                    if (!trimmed) return
                    
                    // 🔥 اگر خط یک لینک HTML کامل است (با آیکن و تگ <a>)
                    if (trimmed.matches(/.*<div[^>]*>\s*[🌐📱🔗]\s*<a[^>]*data-link-type=['"]text['"][^>]*>.*?<\/a>\s*<\/div>.*/)) {
                        // لینک HTML رو مستقیماً به نتیجه اضافه کن
                        result << trimmed
                    }
                    // 🔥 اگر فقط لینک <a> است (بدون div)
                    else if (trimmed.matches(/.*[🌐📱🔗]\s*<a[^>]*data-link-type=['"]text['"][^>]*>.*?<\/a>.*/)) {
                        result << trimmed
                    }
                    // اگر متن ساده است
                    else {
                        // تگ‌ها رو حذف کن تا متن ساده بدست بیاد
                        def plainText = trimmed
                            .replaceAll(/<[^>]+>/, '')
                            .replaceAll(/&nbsp;/, ' ')
                            .trim()
                        
                        if (plainText && 
                            !plainText.startsWith("//") && 
                            !plainText.startsWith("@ExecutionModes") &&
                            !plainText.startsWith("import ") &&
                            !plainText.startsWith("def ") &&
                            !plainText.startsWith("try {") &&
                            !plainText.startsWith("catch ") &&
                            !plainText.matches(/^[↙↗↔]️?\s*.+$/)) {
                            result << plainText
                        }
                    }
                }
            }
        } catch (Exception ex) {
            println "خطا در extractNodeContent: ${ex.message}"
            def cleanText = text.replaceAll(/<[^>]+>/, '').trim()
            return cleanText ? [cleanText] : []
        }
    } else {
        // متن ساده - فیلتر کردن کامنت‌ها و کد اسکریپت
        result = text.split('\n')
            .collect { it.trim() }
            .findAll { 
                it && 
                !it.startsWith("//") && 
                !it.startsWith("@ExecutionModes") &&
                !it.startsWith("import ") &&
                !it.startsWith("def ") &&
                !it.startsWith("try {") &&
                !it.startsWith("catch ") &&
                // 🔥 خطوطی که فقط آیکن کانکتور هستند را حذف کن
                !it.matches(/^[↙↗↔]️?\s*.+$/)
            }
    }
    
    return result ?: []
}

// ================= سایر توابع =================

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    text.split('\n').find { it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://") }?.trim() ?: "لینک"
}

def getSmartTitle(uri) {
    if (!uri) return "لینک"
    def parts = uri.split(/\//)
    if (parts.size() < 4) return uri.take(30) + '...'
    
    def protocol = parts[0]
    def slashes = parts[1] ? '/' : ''
    def domain = parts[2]
    return "${protocol}${slashes}${domain}/..."
}

def getTargetNodeTitle(freeplaneUri) {
    if (!freeplaneUri?.contains("#")) return "لینک"
    
    def targetId = freeplaneUri.substring(freeplaneUri.lastIndexOf('#') + 1)
    def targetNode = c.find { it.id == targetId }.find()
    
    if (targetNode) {
        return getFirstLineFromText(extractPlainTextForProcessing(targetNode))
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

// 🔥 تابع جدید: ساخت همه کانکتورها (برای گره اصلی)
def generateAllConnectorsHTML(grouped) {
    def html = []
    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextForProcessing(n))) +
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
                html << "<div style='margin-bottom: 3px; text-align: right'>${icon}${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}

// 🔥 تابع: فقط کانکتورهای جدید اضافه کن (برای گره‌های دیگر)
def generateNewConnectorsHTML(grouped, existingIds = []) {
    def html = []
    def makeLink = { n ->
        def nodeId = n.id
        if (existingIds.contains(nodeId)) return "" // تکراری را نریز!
        
        "<a data-link-type='connector' href='#${nodeId}'>" +
        HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextForProcessing(n))) +
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
                def linkHtml = makeLink(n)
                if (linkHtml) { // فقط اگر جدید باشد
                    html << "<div style='margin-bottom: 3px; text-align: right'>${icon}${linkHtml}</div>"
                }
            }
        }
    }
    html.join("")
}

// 🔥 پردازش خطوط با منطق صحیح
def processLinesToHTML(lines, backwardTitle = null, currentNode = null) {
    def result = []
    
    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed) return
        
        // 🔥 اگر خط از قبل یک لینک HTML کامل است، تغییرش نده
        if (trimmed.startsWith('<div') && trimmed.contains('data-link-type="text"')) {
            result << trimmed
            return
        }
        
        // 🔥 اگر خط فقط لینک <a> است (بدون div wrapper)
        if (trimmed.startsWith('🌐 <a') || trimmed.startsWith('📱 <a') || trimmed.startsWith('🔗 <a')) {
            // به صورت div-wrap شده برگردون
            result << "<div style='margin-bottom: 3px; text-align: right'>${trimmed}</div>"
            return
        }
        
        // Web 🌐 (متن ساده)
        if (trimmed =~ /^https?:\/\/[^\s]+$/) {
            result << "<div style='margin-bottom: 3px; text-align: right'>🌐 <a data-link-type='text' href='${trimmed}'>${HtmlUtils.toXMLEscapedText(getSmartTitle(trimmed))}</a></div>"
        }
        // Markdown [text](url) 🌐
        else if ((trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)) {
            def mdMatcher = (trimmed =~ /\[([^\]]*?)\]\s*\(\s*(https?:\/\/[^\)\s]+)\s*\)/)
            def title = mdMatcher[0][1].trim()
            def uri = mdMatcher[0][2].trim()
            if (!title || title == uri) title = getSmartTitle(uri)
            result << "<div style='margin-bottom: 3px; text-align: right'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // URL + Title 🌐 (متن ساده)
        else if ((trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)) {
            def urlTitleMatcher = (trimmed =~ /(https?:\/\/[^\s]+)\s+(.+)/)
            def uri = urlTitleMatcher[0][1].trim()
            def title = urlTitleMatcher[0][2].trim()
            result << "<div style='margin-bottom: 3px; text-align: right'>🌐 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // Obsidian 📱 (متن ساده)
        else if (trimmed.startsWith("obsidian://")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = (parts.length > 1) ? parts[1]?.trim() : "ابسیدین"
            result << "<div style='margin-bottom: 3px; text-align: right'>📱 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // Freeplane 🔗 (متن ساده)
        else if (trimmed.startsWith("freeplane:") || (trimmed.contains("#") && !trimmed.startsWith("obsidian://"))) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            def title = backwardTitle
            if (!title && uri.contains("#") && currentNode) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    title = getFirstLineFromText(extractPlainTextForProcessing(targetNode))
                }
            }
            if (!title) title = ((parts.length > 1) ? parts[1]?.trim() : "لینک")
            result << "<div style='margin-bottom: 3px; text-align: right'>🔗 <a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // متن عادی (نه لینک)
        else {
            // 🔥 فقط متن ساده (با escaping)
            if (!trimmed.matches(/^[↙↗↔]️?\s*.+$/)) {
                result << HtmlUtils.toXMLEscapedText(trimmed)
            }
        }
    }
    
    return result
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

// 🔥 حذف مستقیم کانکتور از HTML - با بررسی ایمنی
def removeConnectorFromHTML(nodeText, sourceId) {
    if (!nodeText || !nodeText.contains("<body>")) return nodeText
    
    try {
        def s = nodeText.indexOf("<body>") + 6
        def e = nodeText.indexOf("</body>")
        
        // بررسی محدوده‌های معتبر
        if (s <= 5 || e <= s || e > nodeText.length()) {
            return nodeText
        }
        
        def before = nodeText.substring(0, s)
        def htmlContent = nodeText.substring(s, e)
        def after = nodeText.substring(e)
        
        // حذف دقیق کانکتور مورد نظر
        def connectorPattern = /<div style=['"]margin-bottom: 3px; text-align: right['"]>[\s\n]*[↙↗↔]️?[\s\n]*<a[^>]*data-link-type=['"]connector['"][^>]*href=['"]#${sourceId}['"][^>]*>.*?<\/a>[\s\n]*<\/div>/
        def newHtmlContent = htmlContent.replaceAll(connectorPattern, '')
        
        return before + newHtmlContent + after
    } catch (Exception e) {
        println "خطا در removeConnectorFromHTML: ${e.message}"
        return nodeText
    }
}

// 🔥 ساخت backward link در گره مقصد - فقط اگر وجود نداشته باشد
def createBackwardTextLinkIfNeeded(targetNode, sourceNode, sourceFreeplaneUri) {
    def sourceId = sourceNode.id
    
    // بررسی کن که آیا در گره مقصد از قبل لینکی به گره مبدا وجود دارد
    if (hasLinkToNode(targetNode, sourceId)) {
        // لینک از قبل وجود دارد - کاری نکن
        return false
    }
    
    // لینک وجود ندارد - بساز
    def sourceTitle = getFirstLineFromText(extractPlainTextForProcessing(sourceNode))

    // اضافه کردن backward link به انتهای متن target
    def targetPlain = extractPlainTextForProcessing(targetNode)
    def targetLines = targetPlain.split('\n')
    targetLines = targetLines + ["freeplane:${sourceFreeplaneUri} ${sourceTitle}"]
    
    def targetHTML = processLinesToHTML(targetLines, sourceTitle, targetNode)
    
    // فقط کانکتورهای جدید را اضافه کن
    def existingConnectorIds = extractConnectedNodeIdsFromText(targetNode)
    def connectors = extractConnectedNodes(targetNode)
    def connectorsHTML = generateNewConnectorsHTML(connectors, existingConnectorIds)
    
    def finalHTML = targetHTML.join('\n')
    if (connectorsHTML) {
        finalHTML += "\n" + connectorsHTML
    }
    
    targetNode.text = "<html><body>${finalHTML}</body></html>"
    return true
}

// 🔥 آپدیت همسایه‌ها - نسخه بهبود یافته
def updateOtherSideConnectors(centerNode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        
        // محتوای اصلی را حفظ کن
        def contentLines = extractNodeContent(proxy)
        
        // فقط کانکتورهای جدید بساز
        def existingConnectorIds = extractConnectedNodeIdsFromText(proxy)
        def connectorsHTML = generateNewConnectorsHTML(extractConnectedNodes(proxy), existingConnectorIds)
        
        // 🔥 KEY FIX: اگر کانکتور جدید نیست → باز هم HTML اصلی را بساز (برای حفظ کانکتورهای موجود)
        def htmlLines = processLinesToHTML(contentLines, null, proxy)
        
        def finalHTML = htmlLines.join('\n')
        
        // 🔥 اگر کانکتورهای قبلی وجود دارند، آنها را اضافه کن
        def currentConnectors = extractConnectedNodes(proxy)
        def allConnectorsHTML = generateAllConnectorsHTML(currentConnectors)
        
        if (allConnectorsHTML) {
            finalHTML += "\n" + allConnectorsHTML
        }
        
        proxy.text = "<html><body>${finalHTML}</body></html>"
    }
}

// 🔢 تابع جدید: حذف کانکتور از همه گره‌های متصل
def removeConnectorFromAllConnectedNodes(centerNode, targetNode) {
    def centerId = centerNode.id
    
    // 🔥 1. حذف از گره هدف
    if (targetNode) {
        def currentText = targetNode.text
        def cleanedText = removeConnectorFromHTML(currentText, centerId)
        if (cleanedText != currentText) {
            targetNode.text = cleanedText
            // بعد از حذف، گره هدف را بازسازی کن
            def targetContentLines = extractNodeContent(targetNode)
            def targetHtmlLines = processLinesToHTML(targetContentLines, null, targetNode)
            def targetConnectors = extractConnectedNodes(targetNode)
            def targetConnectorsHTML = generateAllConnectorsHTML(targetConnectors)
            
            def targetFinalHTML = targetHtmlLines.join('\n')
            if (targetConnectorsHTML) {
                targetFinalHTML += "\n" + targetConnectorsHTML
            }
            targetNode.text = "<html><body>${targetFinalHTML}</body></html>"
        }
    }
    
    // 🔥 2. حذف از همه گره‌های متصل به مرکز
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        if (other != targetNode) {
            def currentText = other.text
            def cleanedText = removeConnectorFromHTML(currentText, centerId)
            if (cleanedText != currentText) {
                other.text = cleanedText
                // 🔥 بازسازی گره برای حذف متن کانکتور
                def otherContentLines = extractNodeContent(other)
                def otherHtmlLines = processLinesToHTML(otherContentLines, null, other)
                def otherConnectors = extractConnectedNodes(other)
                def otherConnectorsHTML = generateAllConnectorsHTML(otherConnectors)
                
                def otherFinalHTML = otherHtmlLines.join('\n')
                if (otherConnectorsHTML) {
                    otherFinalHTML += "\n" + otherConnectorsHTML
                }
                other.text = "<html><body>${otherFinalHTML}</body></html>"
            }
        }
    }
}

// 🔥 تابع اصلی پردازش - الگوی جدید
def processNode() {
    def node = c.selected
    if (!node) return

    // 1. کانکتورهای قبلی را ذخیره کن
    def previousConnectorIds = extractConnectedNodeIdsFromText(node)
    def previouslyConnectedNodes = []
    previousConnectorIds.each { nodeId ->
        def targetNode = c.find { it.id == nodeId }.find()
        if (targetNode && targetNode != node) {
            previouslyConnectedNodes << targetNode
        }
    }

    // 2. محتوای واقعی گره را استخراج کن
    def contentLines = extractNodeContent(node)
    
    // 3. خطوط را پردازش کن (فقط لینک‌های جدید HTML می‌شوند)
    def processedLines = processLinesToHTML(contentLines, null, node)
    
    // 4. همه کانکتورهای فعلی را بساز
    def connectors = extractConnectedNodes(node)
    def connectorsHTML = generateAllConnectorsHTML(connectors)
    
    // 5. متن‌ها و لینک‌ها را ترکیب کن
    def finalContent = []
    
    processedLines.each { line ->
        // اگر خط از قبل HTML است (لینک) یا متن ساده است
        if (line.startsWith('<')) {
            finalContent << line
        } else {
            // متن ساده - مستقیماً در body قرار می‌گیرد
            finalContent << line
        }
    }
    
    // 6. کانکتورها را اضافه کن (اگر وجود دارند)
    def finalHTML = finalContent.join('\n')
    if (connectorsHTML) {
        if (finalHTML) {
            finalHTML += "\n" + connectorsHTML
        } else {
            finalHTML = connectorsHTML
        }
    }
    
    node.text = "<html><body>${finalHTML}</body></html>"

    // 7. دوطرفه: برای هر freeplane link، backward link بساز
    def plainText = extractPlainTextForProcessing(node)
    def freeplaneUris = []
    plainText.split('\n').each { line ->
        def trimmed = line.trim()
        if (trimmed.startsWith("freeplane:") || trimmed.contains("#")) {
            def parts = trimmed.split(' ', 2)
            freeplaneUris << parts[0]
        }
    }

    freeplaneUris.each { uri ->
        if (uri.contains("#")) {
            def targetId = uri.substring(uri.lastIndexOf('#') + 1)
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                createBackwardTextLinkIfNeeded(targetNode, node, uri)
            }
        }
    }

    // 8. آپدیت همسایه‌ها
    updateOtherSideConnectors(node)
    
    // 9. حذف کانکتورهای حذف شده
    def currentConnected = []
    currentConnected.addAll(connectors['ورودی'] ?: [])
    currentConnected.addAll(connectors['خروجی'] ?: [])
    currentConnected.addAll(connectors['دوطرفه'] ?: [])
    
    def removedConnections = previouslyConnectedNodes.findAll { !currentConnected.contains(it) }
    removedConnections.each { oldConnectedNode ->
        removeConnectorFromAllConnectedNodes(node, oldConnectedNode)
    }
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node) return
    
    processNode()
    ui.showMessage("✅ v7.6.2 FIXED - حفظ کامل لینک‌های HTML ✅", 1)
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
