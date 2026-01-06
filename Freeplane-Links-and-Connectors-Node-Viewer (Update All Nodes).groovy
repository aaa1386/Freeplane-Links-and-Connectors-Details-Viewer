// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aaa1386 - v9.4.2 - بررسی و اصلاح کل نقشه + لینک‌سازی عادی ✅
// 🔥 تغییر: اصلاح دیالوگ‌ها و رفع خطای JOptionPane
// 🔥 رفع مشکل: لینک‌های فریپلنی و کانکتوری بدون خطا پرش می‌کنند
// شروع خوب برای آپدیت کل نقشه  اشکالات دارد در حالت فرپلنی و انتخابگزینه سوم ولی نسخه قبل هم مقایسه شود مطمین نیستم

import org.freeplane.core.util.HtmlUtils
import javax.swing.*

// ================= توابع جدید برای دیالوگ =================
def showMainDialog() {
    // 🔥 KEY FIX: استفاده از آرایه Object[] به جای لیست Groovy
    Object[] options = ["لینک‌سازی عادی", "بررسی و اصلاح کل نقشه", "بررسی گره انتخاب شده"].toArray()
    
    JOptionPane.showInputDialog(
        ui.frame,
        "لطفا نوع عملیات را انتخاب کنید:",
        "انتخاب نوع عملیات",
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    )
}

def showLinkingModeDialog() {
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

def hasFreeplaneLink(node) {
    def plainText = extractPlainTextForProcessing(node)
    return plainText.contains("freeplane:")
}

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
                /<div style=['"]margin-bottom: 3px; text-align: right['"]>[\s\n]*(?:[↗↔]️?|🔙)[\s\n]*<a[^>]*data-link-type=['"]text['"][^>]*>.*?<\/a>[\s\n]*<\/div>/,
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

// 🔥 تابع جدید: استخراج محتوای واقعی گره - نسخه کاملاً اصلاح شده
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
                
                // 🔥 KEY FIX: استخراج همه لینک‌های HTML (چه کانکتوری و چه Freeplane)
                def allLinkPattern = /<div[^>]*>[\s\S]*?<a[^>]*>[\s\S]*?<\/a>[\s\S]*?<\/div>/
                def matcher = (htmlContent =~ /(?s)${allLinkPattern}/)
                def allLinks = []
                matcher.each { link ->
                    allLinks << link.trim()
                }
                
                // جدا کردن لینک‌های Freeplane (با آیکن 🔗) از لینک‌های کانکتوری
                def freeplaneLinks = []
                def connectorLinks = []
                
                allLinks.each { linkStr ->
                    // اگر لینک Freeplane است (حاوی 🔗)
                    if (linkStr.contains("🔗")) {
                        freeplaneLinks << linkStr
                        println "📌 حفظ لینک Freeplane: ${linkStr.take(80)}..."
                    } 
                    // اگر لینک کانکتوری است (با آیکن ↗️ ↔️ 🔙 اما بدون 🔗)
                    else if (linkStr.contains("↗️") || linkStr.contains("↔️") || linkStr.contains("🔙")) {
                        connectorLinks << linkStr
                        println "📌 حذف لینک کانکتوری: ${linkStr.take(80)}..."
                    }
                    // سایر لینک‌های HTML
                    else {
                        freeplaneLinks << linkStr
                        println "📌 حفظ لینک HTML دیگر: ${linkStr.take(80)}..."
                    }
                }
                
                // حذف همه لینک‌ها از htmlContent
                def remainingContent = htmlContent
                allLinks.each { link ->
                    remainingContent = remainingContent.replace(link, '')
                }
                
                // پردازش باقی مانده متن
                remainingContent.split('\n').each { line ->
                    def trimmed = line.trim()
                    if (trimmed && 
                        !trimmed.startsWith("//") && 
                        !trimmed.startsWith("@ExecutionModes") &&
                        !trimmed.startsWith("import ") &&
                        !trimmed.startsWith("def ") &&
                        !trimmed.startsWith("try {") &&
                        !trimmed.startsWith("catch ") &&
                        !trimmed.matches(/^(?:[↗↔]️?|🔙)\s*.+$/)) {
                        result << trimmed
                    }
                }
                
                // اضافه کردن لینک‌های Freeplane و سایر لینک‌های HTML (به جز کانکتوری)
                freeplaneLinks.each { link ->
                    result << link
                }
            }
        } catch (Exception ex) {
            println "خطا در extractNodeContent: ${ex.message}"
            // اگر خطا رخ داد، کل متن را به صورت ساده برگردان
            def cleanText = text.replaceAll(/<[^>]+>/, '').replaceAll(/&[a-z]+;/, '').trim()
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
                !it.matches(/^(?:[↗↔]️?|🔙)\s*.+$/)
            }
    }
    
    return result ?: []
}

// ================= سایر توابع =================

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    def lines = text.split('\n')
    for (line in lines) {
        def trimmed = line.trim()
        if (trimmed && !trimmed.startsWith("freeplane:") && !trimmed.startsWith("obsidian://")) {
            return trimmed
        }
    }
    return "لینك"
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

// 🔥 تابع بهبود یافته: اگر عنوان با @ شروع شود، تغییر نکند
def getTargetNodeTitle(freeplaneUri, currentTitle = null) {
    if (!freeplaneUri?.contains("#")) return "لینک"
    
    def targetId = freeplaneUri.substring(freeplaneUri.lastIndexOf('#') + 1)
    def targetNode = c.find { it.id == targetId }.find()
    
    if (targetNode) {
        def newTitle = getFirstLineFromText(extractPlainTextForProcessing(targetNode))
        // 🔥 اگر عنوان فعلی با @ شروع می‌شود، تغییرش نده
        if (currentTitle?.startsWith('@')) {
            return currentTitle
        }
        return newTitle
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

// 🔥 تابع جدید: ساخت URI کامل برای یک گره - نسخه اصلاح شده (بر اساس الگوی صحیح)
def getNodeUri(node) {
    if (!node) return ""
    
    try {
        // 🔥 KEY FIX: ساخت URI کامل با مسیر فایل نقشه
        def mapFile = node.map.file
        if (mapFile && mapFile.exists()) {
            def filePath = mapFile.absolutePath
            
            // 🔥 KEY FIX: ساخت URI به فرمت freeplane:/%20/درایو:/مسیر
            // 1. تبدیل بک‌اسلش به فوروارد اسلش
            def normalizedPath = filePath.replace('\\', '/')
            
            // 2. کدگذاری صحیح فاصله‌ها
            def encodedPath = normalizedPath.replace(' ', '%20')
            
            // 3. 🔥 KEY FIX: ساخت URI با الگوی صحیح freeplane:/%20/...
            // برای ویندوز: freeplane:/%20/D:/AJ/OneDrive/...
            // الگوی صحیح: freeplane:/%20/ + مسیر کامل
            def uri = "freeplane:/%20/${encodedPath}#${node.id}"
            
            println "   🔗 ساخت URI برای گره ${node.id}: ${uri}"
            return uri
        } else {
            // اگر فایل نقشه موجود نبود، از ID استفاده کن
            println "   ⚠️ فایل نقشه برای گره ${node.id} یافت نشد، استفاده از ID ساده"
            return "#${node.id}"
        }
    } catch (Exception e) {
        println "   ❌ خطا در ساخت URI برای گره ${node.id}: ${e.message}"
        return "#${node.id}"
    }
}

// 🔥 تابع جدید: ساخت همه کانکتورها (برای گره اصلی) - با URI کامل و آیکون اصلاح شده
def generateAllConnectorsHTML(grouped) {
    def html = []
    def makeLink = { n ->
        def nodeUri = getNodeUri(n)
        def nodeTitle = HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextForProcessing(n)))
        
        // 🔥 KEY FIX: ساخت لینک با URI کامل
        "<a data-link-type='text' href='${nodeUri}'>${nodeTitle}</a>"
    }

    ['ورودی','خروجی','دوطرفه'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {
            def icon = 
                (type == 'ورودی')   ? '🔙 ' :  // 🔥 تغییر از '| 🔙' به '🔙'
                (type == 'خروجی')   ? '↗️ ' :
                                      '↔️ '
            nodes.each { n ->
                html << "<div style='margin-bottom: 3px; text-align: right'>${icon}${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}

// 🔥 تابع: فقط کانکتورهای جدید اضافه کن (برای گره‌های دیگر) - با URI کامل و آیکون اصلاح شده
def generateNewConnectorsHTML(grouped, existingUris = []) {
    def html = []
    def makeLink = { n ->
        def nodeUri = getNodeUri(n)
        
        // 🔥 بررسی تکراری بودن بر اساس URI کامل
        if (existingUris.contains(nodeUri)) {
            println "   ⏭️ لینک تکراری برای URI: ${nodeUri}"
            return ""
        }
        
        def nodeTitle = HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextForProcessing(n)))
        
        // 🔥 KEY FIX: ساخت لینک با URI کامل
        "<a data-link-type='text' href='${nodeUri}'>${nodeTitle}</a>"
    }

    ['ورودی','خروجی','دوطرفه'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {
            def icon = 
                (type == 'ورودی')   ? '🔙 ' :  // 🔥 تغییر از '| 🔙' به '🔙'
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

// 🔥 پردازش خطوط با منطق صحیح - نسخه اصلاح شده با الگوی صحیح URI
def processLinesToHTML(lines, backwardTitle, currentNode, mode = "One-way") {
    def result = []
    
    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed) return
        
        // 🔥 KEY FIX: اگر خط از قبل یک لینک HTML کامل است، آن را بدون تغییر حفظ کن
        if (trimmed.startsWith('<div') && trimmed.endsWith('</div>')) {
            // این یک لینک HTML از قبل فرمت شده است، بدون تغییر اضافه کن
            result << trimmed
            println "✅ حفظ لینک HTML موجود: ${trimmed.take(100)}..."
            return
        }
        
        // همچنین اگر خط فقط تگ <a> دارد (بدون div wrapper)
        if (trimmed.startsWith('<a') && trimmed.endsWith('</a>')) {
            // آن را در div بپیچان اما محتوای آن را تغییر نده
            result << "<div style='margin-bottom: 3px; text-align: right'>${trimmed}</div>"
            println "✅ بسته‌بندی لینک <a> در div: ${trimmed.take(80)}..."
            return
        }
        
        // Web 🌐 (متن ساده) - فقط URL
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
        // Freeplane 🔗 (متن ساده) - با پشتیبانی از mode و الگوی صحیح URI
        else if (trimmed.startsWith("freeplane:")) {
            def parts = trimmed.split(' ', 2)
            def uri = parts[0] ?: ""
            
            // 🔥 KEY FIX: اصلاح URI برای اطمینان از الگوی صحیح
            // بررسی کن که آیا URI به فرمت صحیح freeplane:/%20/... است یا نه
            if (!uri.startsWith("freeplane:/%20/")) {
                // اگر با freeplane:/ شروع می‌شود اما %20 ندارد، اضافه کن
                if (uri.startsWith("freeplane:/")) {
                    // حذف freeplane:/ و اضافه کردن %20/
                    def pathPart = uri.substring("freeplane:/".length())
                    // اگر pathPart با %20/ شروع نمی‌شود، اضافه کن
                    if (!pathPart.startsWith("%20/")) {
                        uri = "freeplane:/%20/${pathPart}"
                        println "   🔧 اصلاح URI به الگوی صحیح: ${uri.take(80)}..."
                    }
                }
            }
            
            def title
            
            if (backwardTitle) {
                title = backwardTitle
            } else {
                title = getTargetNodeTitle(uri, parts.length > 1 ? parts[1]?.trim() : null)
            }
            
            def icon
            if (mode == "Two-way") {
                icon = "🔗↔️ "
            } else {
                if (backwardTitle) {
                    icon = "🔗🔙 "
                } else {
                    icon = "🔗↗️ "
                }
            }
            
            result << "<div style='margin-bottom: 3px; text-align: right'>${icon}<a data-link-type='text' href='${uri}'>${HtmlUtils.toXMLEscapedText(title)}</a></div>"
        }
        // متن عادی (نه لینک)
        else {
            if (!trimmed.matches(/^(?:[↗↔]️?|🔙)\s*.+$/) && !trimmed.startsWith("<")) {
                result << HtmlUtils.toXMLEscapedText(trimmed)
            } else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
                // اگر از قبل HTML است، بدون تغییر بگذار
                result << trimmed
            }
        }
    }
    
    return result
}

// 🔥 استخراج URIهای کانکتورها از HTML - نسخه بهبود یافته برای URI کامل
def extractConnectedNodeUrisFromText(node) {
    def connectedUris = []
    def text = node.text ?: ""
    
    if (!text.contains("<body>")) return connectedUris
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        
        // 🔥 KEY FIX: فقط لینک‌های کانکتوری را شناسایی کن (آنهایی که آیکن ↗️ ↔️ 🔙 دارند)
        // اما لینک‌های Freeplane با آیکن 🔗 را نادیده بگیر!
        def connectorPattern = /<div[^>]*>\s*(?:[↗↔]️?|🔙)\s*<a[^>]*data-link-type=['"]text['"][^>]*href=['"]([^'"]+)['"][^>]*>/
        def connectorMatcher = (htmlContent =~ connectorPattern)
        
        connectorMatcher.each { match ->
            def nodeUri = match[1]
            if (nodeUri && !connectedUris.contains(nodeUri)) {
                connectedUris << nodeUri
                println "   🔍 یافت لینک کانکتوری: ${nodeUri}"
            }
        }
    }
    
    return connectedUris
}

// 🔥 حذف مستقیم کانکتور از HTML - با بررسی ایمنی - نسخه بهبود یافته برای URI کامل
def removeConnectorFromHTML(nodeText, sourceUri) {
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
        
        // 🔥 KEY FIX: فقط لینک‌های کانکتوری را حذف کن (آنهایی که آیکن ↗️ ↔️ 🔙 دارند)
        def escapedUri = java.util.regex.Pattern.quote(sourceUri)
        def connectorPattern = /<div[^>]*>\s*(?:[↗↔]️?|🔙)\s*<a[^>]*data-link-type=['"]text['"][^>]*href=['"]${escapedUri}['"][^>]*>.*?<\/a>\s*<\/div>/
        
        def newHtmlContent = htmlContent.replaceAll(connectorPattern, '')
        
        return before + newHtmlContent + after
    } catch (Exception e) {
        println "خطا در removeConnectorFromHTML: ${e.message}"
        return nodeText
    }
}

// 🔥 ساخت backward link در گره مقصد - نسخه کاملاً اصلاح شده
def createBackwardTextLinkIfNeeded(targetNode, sourceNode, sourceFreeplaneUri, mode) {
    def sourceId = sourceNode.id
    
    // 🔥 همیشه backward link ایجاد کن (حتی اگر از قبل وجود داشته باشد)
    // فقط بررسی کن که duplicate نباشد
    def sourceTitle = getFirstLineFromText(extractPlainTextForProcessing(sourceNode))
    println "🔗 ساخت backward link: ${targetNode.id} ← ${sourceId} با عنوان: ${sourceTitle}"
    
    // 🔥 KEY FIX: ساخت URI صحیح برای backward link با الگوی صحیح
    // استخراج بخش مپ از URI
    def mapUri = ""
    if (sourceFreeplaneUri.contains("#")) {
        def hashIndex = sourceFreeplaneUri.indexOf("#")
        mapUri = sourceFreeplaneUri.substring(0, hashIndex)
    } else {
        mapUri = sourceFreeplaneUri
    }
    
    // 🔥 ساخت URI جدید که به گره sourceNode اشاره کند با الگوی صحیح
    def backwardUri = "${mapUri}#${sourceId}"
    
    println "   URI اصلی (به مقصد): ${sourceFreeplaneUri}"
    println "   URI جدید (به مبدا): ${backwardUri}"
    
    // 🔥 استخراج محتوای فعلی گره مقصد
    def targetContentLines = extractNodeContent(targetNode)
    
    // 🔥 بررسی کن که آیا لینک مشابه از قبل وجود دارد
    def existingLink = false
    
    targetContentLines.each { line ->
        def trimmed = line.trim()
        if (trimmed.startsWith(backwardUri) || trimmed.startsWith(sourceFreeplaneUri)) {
            println "⚠️ لینک مشابه از قبل وجود دارد: ${line}"
            existingLink = true
        }
    }
    
    // 🔥 اگر لینک مشابه وجود ندارد، اضافه کن
    if (!existingLink) {
        // ساخت لینک جدید
        def newLine = backwardUri
        if (sourceTitle && sourceTitle != "لینک") {
            newLine = "${backwardUri} ${sourceTitle}"
        }
        
        targetContentLines = targetContentLines + [newLine]
        println "✅ اضافه کردن backward link جدید: ${newLine}"
    } else {
        println "⏭️ از ساخت لینک تکراری صرف نظر شد"
        return false
    }
    
    // 🔥 پردازش خطوط به HTML
    def targetHTML = processLinesToHTML(targetContentLines, sourceTitle, targetNode, mode)
    
    // اضافه کردن کانکتورها
    def existingConnectorUris = extractConnectedNodeUrisFromText(targetNode)
    def connectors = extractConnectedNodes(targetNode)
    def connectorsHTML = generateNewConnectorsHTML(connectors, existingConnectorUris)
    
    def finalHTML = targetHTML.join('\n')
    if (connectorsHTML) {
        finalHTML += "\n" + connectorsHTML
    }
    
    targetNode.text = "<html><body>${finalHTML}</body></html>"
    println "✅ backward link با موفقیت ایجاد/به‌روزرسانی شد (از ${targetNode.id} به ${sourceId})"
    return true
}

// 🔥 تابع جدید: استخراج ID گره از URI فری‌پلین
def extractNodeIdFromFreeplaneUri(uri) {
    if (!uri || !uri.contains("#")) return null
    return uri.substring(uri.lastIndexOf('#') + 1)
}

// 🔥 آپدیت همسایه‌ها - نسخه بهبود یافته
def updateOtherSideConnectors(centerNode, mode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        
        // محتوای اصلی را حفظ کن
        def contentLines = extractNodeContent(proxy)
        
        // فقط کانکتورهای جدید بساز
        def existingConnectorUris = extractConnectedNodeUrisFromText(proxy)
        def connectorsHTML = generateNewConnectorsHTML(extractConnectedNodes(proxy), existingConnectorUris)
        
        // 🔥 KEY FIX: اگر کانکتور جدید نیست → باز هم HTML اصلی را بساز (برای حفظ کانکتورهای موجود)
        def htmlLines = processLinesToHTML(contentLines, null, proxy, mode)
        
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

// 🔢 تابع جدید: حذف کانکتور از هر دو گره متصل - با URI کامل
def removeConnectorFromBothNodes(sourceNode, targetNode, mode) {
    def sourceUri = getNodeUri(sourceNode)
    def targetUri = getNodeUri(targetNode)
    
    println "🗑️ حذف کانکتور بین: ${sourceNode.id} و ${targetNode.id}"
    println "   URI منبع: ${sourceUri}"
    println "   URI مقصد: ${targetUri}"
    
    // 🔥 1. حذف لینک کانکتوری از گره منبع (به مقصد)
    def sourceText = sourceNode.text
    def cleanedSourceText = removeConnectorFromHTML(sourceText, targetUri)
    
    if (cleanedSourceText != sourceText) {
        sourceNode.text = cleanedSourceText
        // بعد از حذف، گره منبع را بازسازی کن
        def sourceContentLines = extractNodeContent(sourceNode)
        def sourceHtmlLines = processLinesToHTML(sourceContentLines, null, sourceNode, mode)
        def sourceConnectors = extractConnectedNodes(sourceNode)
        def sourceConnectorsHTML = generateAllConnectorsHTML(sourceConnectors)
        
        def sourceFinalHTML = sourceHtmlLines.join('\n')
        if (sourceConnectorsHTML) {
            sourceFinalHTML += "\n" + sourceConnectorsHTML
        }
        sourceNode.text = "<html><body>${sourceFinalHTML}</body></html>"
        println "✅ کانکتور از گره منبع ${sourceNode.id} حذف شد"
    } else {
        println "⚠️ کانکتوری در گره منبع ${sourceNode.id} برای حذف یافت نشد"
    }
    
    // 🔥 2. حذف لینک کانکتوری از گره مقصد (به منبع)
    def targetText = targetNode.text
    def cleanedTargetText = removeConnectorFromHTML(targetText, sourceUri)
    
    if (cleanedTargetText != targetText) {
        targetNode.text = cleanedTargetText
        // بعد از حذف، گره مقصد را بازسازی کن
        def targetContentLines = extractNodeContent(targetNode)
        def targetHtmlLines = processLinesToHTML(targetContentLines, null, targetNode, mode)
        def targetConnectors = extractConnectedNodes(targetNode)
        def targetConnectorsHTML = generateAllConnectorsHTML(targetConnectors)
        
        def targetFinalHTML = targetHtmlLines.join('\n')
        if (targetConnectorsHTML) {
            targetFinalHTML += "\n" + targetConnectorsHTML
        }
        targetNode.text = "<html><body>${targetFinalHTML}</body></html>"
        println "✅ کانکتور از گره مقصد ${targetNode.id} حذف شد"
    } else {
        println "⚠️ کانکتوری در گره مقصد ${targetNode.id} برای حذف یافت نشد"
    }
}

// 🔥 تابع جدید: استخراج لینک‌های Freeplane از محتوای گره
def extractFreeplaneLinksFromContent(contentLines) {
    def freeplaneUris = []
    
    contentLines.each { line ->
        def trimmed = line.trim()
        // 🔥 فقط خطوطی که با freeplane: شروع می‌شوند
        if (trimmed.startsWith("freeplane:")) {
            def parts = trimmed.split(' ', 2)
            if (parts[0]) {
                freeplaneUris << parts[0]
                println "📌 یافت لینک Freeplane: ${parts[0]} (به گره: ${extractNodeIdFromFreeplaneUri(parts[0])})"
            }
        }
    }
    
    return freeplaneUris
}

// ================= توابع جدید برای بررسی کل نقشه =================

// 🔥 تابع جدید: استخراج تمام لینک‌های HTML از یک گره (کانکتوری و فریپلنی با آیکن‌های مشخص)
def extractAllLinksFromNode(node) {
    def links = []
    def text = node.text ?: ""
    
    if (!text.contains("<body>")) return links
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        
        // 🔥 الگوی استخراج لینک‌های HTML با آیکن‌های مشخص شده
        // شامل: 🔗↔️, 🔗↗️, 🔗🔙, ↗️, ↔️, 🔙
        def linkPattern = /<div[^>]*>\s*((?:🔗)?[↗↔]️?|🔙)\s*<a[^>]*data-link-type=['"]text['"][^>]*href=['"]([^'"]+)['"][^>]*>([^<]*)<\/a>\s*<\/div>/
        def matcher = (htmlContent =~ /(?s)${linkPattern}/)
        
        matcher.each { match ->
            def icon = match[1]?.trim()
            def uri = match[2]?.trim()
            def title = match[3]?.trim()
            def fullHtml = match[0]?.trim()
            
            // 🔥 KEY FIX: فقط لینک‌هایی که آیکن‌های مجاز دارند
            if (icon in ["🔗↔️", "🔗↗️", "🔗🔙", "↗️", "↔️", "🔙", "↗", "↔"]) {
                // 🔥 بررسی کن که آیا عنوان با @ شروع می‌شود
                if (title && !title.startsWith("@")) {
                    def linkInfo = [
                        'icon': icon,
                        'uri': uri,
                        'title': title,
                        'fullHtml': fullHtml,
                        'isFreeplane': icon.contains("🔗"),
                        'isConnector': !icon.contains("🔗") && (icon.contains("↗") || icon.contains("↔") || icon.contains("🔙"))
                    ]
                    links << linkInfo
                    println "📌 یافت لینک ${linkInfo.isFreeplane ? 'فریپلنی' : 'کانکتوری'}: ${uri} با عنوان: ${title}"
                } else {
                    println "   ⏭️ لینک با عنوان @ نادیده گرفته شد: ${title}"
                }
            }
        }
    }
    
    return links
}

// 🔥 تابع جدید: بررسی وجود کانکتور بین دو گره
def connectorExistsBetween(sourceNode, targetNode) {
    def proxySource = asProxy(sourceNode)
    if (!proxySource) return false
    
    def connectors = extractConnectedNodes(proxySource)
    def allConnected = []
    allConnected.addAll(connectors['ورودی'] ?: [])
    allConnected.addAll(connectors['خروجی'] ?: [])
    allConnected.addAll(connectors['دوطرفه'] ?: [])
    
    return allConnected.contains(targetNode)
}

// 🔥 تابع جدید: بررسی وجود backward link در گره مقصد
def hasBackwardLink(targetNode, sourceNodeUri) {
    def targetLinks = extractAllLinksFromNode(targetNode)
    def sourceNodeId = extractNodeIdFromFreeplaneUri(sourceNodeUri)
    
    return targetLinks.any { link ->
        def linkNodeId = extractNodeIdFromFreeplaneUri(link.uri)
        return linkNodeId == sourceNodeId
    }
}

// 🔥 تابع جدید: به‌روزرسانی عنوان یک لینک
def updateLinkTitle(node, oldLinkHtml, newTitle) {
    def text = node.text ?: ""
    if (!text.contains("<body>")) return text
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        
        // استخراج بخش‌های لینک
        def pattern = /(<a[^>]*>)([^<]*)(<\/a>)/
        def newLinkHtml = oldLinkHtml.replaceAll(pattern) { full, startTag, oldTitle, endTag ->
            return "${startTag}${newTitle}${endTag}"
        }
        
        def newHtmlContent = htmlContent.replace(oldLinkHtml, newLinkHtml)
        return text.substring(0, s) + newHtmlContent + text.substring(e)
    }
    
    return text
}

// 🔥 تابع جدید: حذف یک لینک از گره
def removeLinkFromNode(node, linkHtml) {
    def text = node.text ?: ""
    if (!text.contains("<body>")) return text
    
    def s = text.indexOf("<body>") + 6
    def e = text.indexOf("</body>")
    if (s > 5 && e > s) {
        def htmlContent = text.substring(s, e)
        
        // حذف لینک از HTML
        def newHtmlContent = htmlContent.replace(linkHtml, '').trim()
        
        // حذف خطوط خالی اضافی
        newHtmlContent = newHtmlContent.replaceAll(/\n\s*\n\s*\n/, '\n\n')
        
        return text.substring(0, s) + newHtmlContent + text.substring(e)
    }
    
    return text
}

// 🔥 تابع جدید: پردازش یک گره و اصلاح لینک‌های آن
def processNodeLinks(node, processedNodes = []) {
    if (processedNodes.contains(node.id)) {
        return 0
    }
    
    processedNodes << node.id
    def changes = 0
    
    println "🔍 بررسی گره: ${node.id} - ${getFirstLineFromText(extractPlainTextForProcessing(node))}"
    
    // استخراج تمام لینک‌های گره
    def allLinks = extractAllLinksFromNode(node)
    println "   📌 یافت ${allLinks.size()} لینک"
    
    allLinks.eachWithIndex { link, index ->
        println "   ${index + 1}. نوع: ${link.icon}, URI: ${link.uri}, عنوان: ${link.title}"
        
        // بررسی URI
        if (!link.uri) {
            println "     ❌ URI نامعتبر - حذف لینک"
            node.text = removeLinkFromNode(node, link.fullHtml)
            changes++
            return
        }
        
        // استخراج ID گره مقصد
        def targetId = extractNodeIdFromFreeplaneUri(link.uri)
        if (!targetId) {
            println "     ❌ ID گره مقصد نامعتبر - حذف لینک"
            node.text = removeLinkFromNode(node, link.fullHtml)
            changes++
            return
        }
        
        // یافتن گره مقصد
        def targetNode = c.find { it.id == targetId }.find()
        if (!targetNode) {
            println "     ❌ گره مقصد یافت نشد (ID: ${targetId}) - حذف لینک"
            node.text = removeLinkFromNode(node, link.fullHtml)
            changes++
            return
        }
        
        // 🔥 کار ۱: به‌روزرسانی عنوان لینک
        def targetTitle = getFirstLineFromText(extractPlainTextForProcessing(targetNode))
        if (targetTitle && targetTitle != "لینک" && targetTitle != link.title) {
            println "     📝 به‌روزرسانی عنوان: '${link.title}' → '${targetTitle}'"
            node.text = updateLinkTitle(node, link.fullHtml, targetTitle)
            changes++
        }
        
        // 🔥 کار ۲: برای لینک‌های کانکتوری - بررسی وجود کانکتور فیزیکی
        if (link.isConnector) {
            if (!connectorExistsBetween(node, targetNode)) {
                println "     🗑️ لینک کانکتوری بدون اتصال فیزیکی - حذف از هر دو طرف"
                
                // حذف از گره فعلی
                node.text = removeLinkFromNode(node, link.fullHtml)
                changes++
                
                // حذف از گره مقصد (اگر لینک برگشت وجود دارد)
                def targetLinks = extractAllLinksFromNode(targetNode)
                targetLinks.each { targetLink ->
                    def sourceIdFromTargetLink = extractNodeIdFromFreeplaneUri(targetLink.uri)
                    if (sourceIdFromTargetLink == node.id) {
                        println "     🗑️ حذف لینک برگشت از گره ${targetNode.id}"
                        targetNode.text = removeLinkFromNode(targetNode, targetLink.fullHtml)
                        changes++
                    }
                }
            }
        }
        
        // 🔥 کار ۳: برای لینک‌های فریپلنی - بررسی دوطرفه بودن
        if (link.isFreeplane) {
            def hasBackward = hasBackwardLink(targetNode, link.uri)
            if (!hasBackward) {
                println "     ⚠️ لینک فریپلنی یک‌طرفه - حذف لینک"
                node.text = removeLinkFromNode(node, link.fullHtml)
                changes++
            }
        }
    }
    
    if (changes > 0) {
        println "   ✅ ${changes} تغییر در گره ${node.id}"
    }
    
    return changes
}

// 🔥 تابع جدید: پردازش کل نقشه
def processWholeMap() {
    println "🚀 شروع بررسی کل نقشه..."
    def processedNodes = []
    def totalChanges = 0
    def nodeCount = 0
    
    // پیمایش تمام گره‌های نقشه
    c.find { true }.each { node ->
        nodeCount++
        def changes = processNodeLinks(node, processedNodes)
        totalChanges += changes
        
        if (changes > 0) {
            println "   ✅ ${changes} تغییر در گره ${node.id}"
        }
    }
    
    println "🎉 بررسی کامل شد!"
    println "📊 آمار:"
    println "   تعداد گره‌ها: ${nodeCount}"
    println "   تعداد گره‌های پردازش شده: ${processedNodes.size()}"
    println "   تعداد کل تغییرات: ${totalChanges}"
    
    return totalChanges
}

// 🔥 تابع اصلی پردازش - نسخه اصلاح شده
def processNode(mode) {
    def node = c.selected
    if (!node) return

    println "🚀 شروع پردازش گره: ${node.id} - حالت: ${mode}"

    // 1. کانکتورهای قبلی را ذخیره کن (بر اساس URI)
    def previousConnectorUris = extractConnectedNodeUrisFromText(node)
    println "📌 کانکتورهای قبلی در متن: ${previousConnectorUris}"
    
    def previouslyConnectedNodes = []
    previousConnectorUris.each { uri ->
        def targetId = null
        if (uri.startsWith("freeplane:") && uri.contains("#")) {
            targetId = uri.substring(uri.lastIndexOf('#') + 1)
        } else if (uri.startsWith("#")) {
            targetId = uri.substring(1)
        }
        
        if (targetId) {
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                previouslyConnectedNodes << targetNode
            }
        }
    }

    // 2. محتوای واقعی گره را استخراج کن (فقط لینک‌های کانکتوری حذف می‌شوند)
    def contentLines = extractNodeContent(node)
    println "📄 محتوای استخراج شده (${contentLines.size()} خط):"
    contentLines.eachWithIndex { line, idx -> 
        if (line.startsWith('<')) {
            println "  ${idx}: [HTML] ${line.take(100)}..."
        } else {
            println "  ${idx}: ${line}"
        }
    }
    
    // 3. خطوط را پردازش کن (فقط لینک‌های جدید HTML می‌شوند)
    def processedLines = processLinesToHTML(contentLines, null, node, mode)
    
    // 4. همه کانکتورهای فعلی را بساز (با URI کامل)
    def connectors = extractConnectedNodes(node)
    println "🔗 کانکتورهای فعلی: ${connectors}"
    
    // 🔥 KEY FIX: فقط کانکتورهای فعلی را بساز (نه همه قبلی‌ها)
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
    println "✅ گره ${node.id} پردازش شد"

    // 7. 🔥 KEY FIX: استخراج لینک‌های Freeplane از contentLines اصلی
    def freeplaneUris = extractFreeplaneLinksFromContent(contentLines)
    println "🔍 یافتن ${freeplaneUris.size()} لینک Freeplane در گره ${node.id}"
    
    // 8. 🔥 ساخت backward link برای هر لینک Freeplane
    println "🔄 ساخت backward link‌ها (در هر دو حالت)"
    freeplaneUris.each { uri ->
        if (uri.contains("#")) {
            def targetId = extractNodeIdFromFreeplaneUri(uri)
            println "  🔍 جستجوی گره مقصد با ID: ${targetId}"
            def targetNode = c.find { it.id == targetId }.find()
            if (targetNode && targetNode != node) {
                println "  ✅ گره مقصد یافت شد: ${targetNode.id} (عنوان: ${getFirstLineFromText(extractPlainTextForProcessing(targetNode))})"
                def created = createBackwardTextLinkIfNeeded(targetNode, node, uri, mode)
                if (created) {
                    println "  ✅ backward link با موفقیت ایجاد شد (از ${targetNode.id} به ${node.id})"
                } else {
                    println "  ⚠️ backward link از قبل وجود داشت یا ایجاد نشد"
                }
            } else {
                println "  ❌ گره مقصد یافت نشد یا همان گره مبدا است"
            }
        }
    }

    // 9. آپدیت همسایه‌ها
    updateOtherSideConnectors(node, mode)
    
    // 10. حذف کانکتورهای حذف شده
    def currentConnected = []
    currentConnected.addAll(connectors['ورودی'] ?: [])
    currentConnected.addAll(connectors['خروجی'] ?: [])
    currentConnected.addAll(connectors['دوطرفه'] ?: [])
    
    def removedConnections = previouslyConnectedNodes.findAll { !currentConnected.contains(it) }
    println "🗑️ کانکتورهای حذف شده: ${removedConnections.collect { it.id }}"
    
    removedConnections.each { oldConnectedNode ->
        println "  🗑️ حذف کانکتور از گره: ${oldConnectedNode.id}"
        removeConnectorFromBothNodes(node, oldConnectedNode, mode)
    }
}

// ================= اجرا =================
try {
    def node = c.selected
    if (!node) {
        ui.showMessage("لطفاً روی یک گره کلیک کنید", 0)
        return
    }
    
    println "📍 گره انتخاب شده: ${node.id}"
    
    // نمایش دیالوگ اصلی
    def selectedOption = showMainDialog()
    
    if (selectedOption == "لینک‌سازی عادی") {
        println "🎯 حالت: لینک‌سازی عادی"
        def mode
        if (hasFreeplaneLink(node)) {
            def selectedMode = showLinkingModeDialog()
            if (selectedMode == null) {
                // کاربر Cancel زد
                println "❌ کاربر Cancel را زد"
                return
            }
            mode = selectedMode
            println "🎯 حالت لینک‌سازی: ${mode}"
        } else {
            mode = "One-way"
            println "🎯 حالت پیش‌فرض: ${mode}"
        }
        
        processNode(mode)
        
    } else if (selectedOption == "بررسی و اصلاح کل نقشه") {
        println "🎯 حالت: بررسی و اصلاح کل نقشه"
        
        // 🔥 KEY FIX: استفاده از JOptionPane.showConfirmDialog به جای ui.showConfirmMessage
        def result = JOptionPane.showConfirmDialog(
            ui.frame,
            "آیا از بررسی کل نقشه اطمینان دارید؟\nاین عملیات ممکن است زمان‌بر باشد.",
            "تأیید بررسی کل نقشه",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        
        if (result == JOptionPane.YES_OPTION) {
            def changes = processWholeMap()
            
            // 🔥 KEY FIX: استفاده از JOptionPane.showMessageDialog به جای ui.showMessage
            JOptionPane.showMessageDialog(
                ui.frame,
                "بررسی کل نقشه کامل شد!\n\n" +
                "تعداد تغییرات اعمال شده: ${changes}\n" +
                "لینک‌های ناسازگار اصلاح یا حذف شدند.",
                "نتیجه بررسی",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
        
    } else if (selectedOption == "بررسی گره انتخاب شده") {
        println "🎯 حالت: بررسی گره انتخاب شده"
        def changes = processNodeLinks(node, [])
        
        if (changes > 0) {
            // 🔥 KEY FIX: استفاده از JOptionPane.showMessageDialog
            JOptionPane.showMessageDialog(
                ui.frame,
                "بررسی گره کامل شد!\n\n" +
                "تعداد تغییرات اعمال شده: ${changes}",
                "نتیجه بررسی",
                JOptionPane.INFORMATION_MESSAGE
            )
        } else {
            JOptionPane.showMessageDialog(
                ui.frame,
                "هیچ تغییری در گره یافت نشد.\nهمه لینک‌ها به‌روز و معتبر هستند.",
                "نتیجه بررسی",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
   
} catch (e) {
    println "❌ خطا: ${e.message}"
    e.printStackTrace()
    ui.showMessage("خطا:\n${e.message}", 0)
}
