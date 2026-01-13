package com.ubertimetracker.util

/**
 * Multi-language detector for Uber Eats app online/offline status.
 * Supports: English, German, Arabic, Russian, French, Spanish, Italian,
 * Portuguese, Turkish, Hindi, Japanese, and more.
 */
object UberEatsLanguageDetector {

    enum class UberStatus {
        ONLINE,
        OFFLINE,
        UNKNOWN
    }

    // Online patterns for different languages
    private val onlinePatterns = listOf(
        // English
        "online", "you're online", "accepting orders", "you are online",
        "go offline", "stop accepting", "available",
        
        // German
        "online", "du bist online", "bestellungen annehmen", "verfügbar",
        "offline gehen", "aufträge annehmen",
        
        // Arabic
        "متصل", "أنت متصل", "قبول الطلبات", "متاح", "جاهز للعمل",
        "إيقاف الاتصال", "قبول طلبات",
        
        // Russian
        "онлайн", "вы онлайн", "принимать заказы", "доступен",
        "выйти из сети", "принимаю заказы", "в сети",
        
        // French
        "en ligne", "vous êtes en ligne", "accepter commandes",
        "disponible", "passer hors ligne",
        
        // Spanish
        "en línea", "estás en línea", "aceptar pedidos",
        "disponible", "desconectarse",
        
        // Italian
        "online", "sei online", "accetta ordini",
        "disponibile", "vai offline",
        
        // Portuguese
        "online", "você está online", "aceitar pedidos",
        "disponível", "ficar offline",
        
        // Turkish
        "çevrimiçi", "çevrimiçisiniz", "sipariş kabul et",
        "müsait", "çevrimdışı ol",
        
        // Hindi
        "ऑनलाइन", "आप ऑनलाइन हैं", "ऑर्डर स्वीकार करें",
        "उपलब्ध", "ऑफ़लाइन जाएं",
        
        // Japanese
        "オンライン", "オンラインです", "注文を受け付け",
        "利用可能", "オフラインにする",
        
        // Chinese (Simplified)
        "在线", "您已上线", "接受订单", "可用",
        
        // Chinese (Traditional)
        "線上", "您已上線", "接受訂單",
        
        // Korean
        "온라인", "온라인 상태", "주문 수락",
        
        // Dutch
        "online", "je bent online", "bestellingen accepteren",
        
        // Polish
        "online", "jesteś online", "przyjmuj zamówienia",
        
        // Ukrainian
        "онлайн", "ви онлайн", "приймати замовлення",
        
        // Thai
        "ออนไลน์", "คุณออนไลน์อยู่", "รับออเดอร์",
        
        // Vietnamese
        "trực tuyến", "bạn đang trực tuyến", "nhận đơn hàng",
        
        // Indonesian
        "online", "anda online", "terima pesanan"
    )

    // Offline patterns for different languages
    private val offlinePatterns = listOf(
        // English
        "offline", "you're offline", "go online", "not accepting",
        "start accepting", "unavailable",
        
        // German
        "offline", "du bist offline", "online gehen", "nicht verfügbar",
        "aufträge starten",
        
        // Arabic
        "غير متصل", "أنت غير متصل", "الاتصال", "غير متاح",
        "بدء الاتصال", "ابدأ العمل",
        
        // Russian
        "офлайн", "вы офлайн", "выйти в сеть", "недоступен",
        "начать принимать", "не в сети",
        
        // French
        "hors ligne", "vous êtes hors ligne", "passer en ligne",
        "non disponible",
        
        // Spanish
        "fuera de línea", "estás desconectado", "conectarse",
        "no disponible",
        
        // Italian
        "offline", "sei offline", "vai online",
        "non disponibile",
        
        // Portuguese
        "offline", "você está offline", "ficar online",
        "indisponível",
        
        // Turkish
        "çevrimdışı", "çevrimdışısınız", "çevrimiçi ol",
        "müsait değil",
        
        // Hindi
        "ऑफ़लाइन", "आप ऑफ़लाइन हैं", "ऑनलाइन जाएं",
        "अनुपलब्ध",
        
        // Japanese
        "オフライン", "オフラインです", "オンラインにする",
        "利用不可",
        
        // Chinese (Simplified)
        "离线", "您已下线", "上线",
        
        // Chinese (Traditional)
        "離線", "您已下線", "上線",
        
        // Korean
        "오프라인", "오프라인 상태", "온라인 전환",
        
        // Dutch
        "offline", "je bent offline", "online gaan",
        
        // Polish
        "offline", "jesteś offline", "przejdź online",
        
        // Ukrainian
        "офлайн", "ви офлайн", "вийти в мережу",
        
        // Thai
        "ออฟไลน์", "คุณออฟไลน์อยู่", "เปิดออนไลน์",
        
        // Vietnamese
        "ngoại tuyến", "bạn đang ngoại tuyến", "lên mạng",
        
        // Indonesian
        "offline", "anda offline", "online sekarang"
    )

    // Uber Eats app identifiers
    private val uberEatsPackages = listOf(
        "com.ubercab.eats",
        "com.ubercab.driver",
        "com.ubercab"
    )

    /**
     * Detects online/offline status from screen text content.
     * @param screenText The text content from accessibility service
     * @return UberStatus - ONLINE, OFFLINE, or UNKNOWN
     */
    fun detectStatus(screenText: String): UberStatus {
        val lowerText = screenText.lowercase()
        
        // Check for online patterns first (more specific)
        for (pattern in onlinePatterns) {
            if (lowerText.contains(pattern.lowercase())) {
                // Make sure it's not a "go online" button (which means currently offline)
                val goOnlinePatterns = listOf(
                    "go online", "online gehen", "الاتصال", "выйти в сеть",
                    "passer en ligne", "conectarse", "vai online", "ficar online",
                    "çevrimiçi ol", "ऑनलाइन जाएं", "オンラインにする",
                    "上线", "上線", "온라인 전환", "online gaan", "przejdź online",
                    "вийти в мережу", "เปิดออนไลน์", "lên mạng", "online sekarang"
                )
                
                val isGoOnlineButton = goOnlinePatterns.any { 
                    lowerText.contains(it.lowercase()) 
                }
                
                if (!isGoOnlineButton) {
                    return UberStatus.ONLINE
                }
            }
        }
        
        // Check for offline patterns
        for (pattern in offlinePatterns) {
            if (lowerText.contains(pattern.lowercase())) {
                return UberStatus.OFFLINE
            }
        }
        
        return UberStatus.UNKNOWN
    }

    /**
     * Check if the package name belongs to Uber Eats
     */
    fun isUberEatsApp(packageName: String?): Boolean {
        return packageName != null && uberEatsPackages.any { 
            packageName.contains(it, ignoreCase = true) 
        }
    }

    /**
     * Get status display text
     */
    fun getStatusDisplayText(status: UberStatus): String {
        return when (status) {
            UberStatus.ONLINE -> "🟢 Online"
            UberStatus.OFFLINE -> "🔴 Offline"
            UberStatus.UNKNOWN -> "⚪ Unknown"
        }
    }
}
