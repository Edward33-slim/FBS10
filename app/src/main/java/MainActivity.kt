package com.fbs10.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(var title: String, val url: String) {
    override fun toString(): String = title
}

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private val bookmarksList = mutableListOf<Bookmark>()

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001
    private val PERMISSION_REQUEST_CODE = 2002

    private var isAdBlockEnabled = false
    private var isMediaBlocked = false
    private var isDesktopMode = false
    
    private var hideSponsored = false
    private var hideSuggestions = false
    private var hidePeopleYouMayKnow = false
    private var hideReels = false
    private var hideStories = false
    private var hidePagesYouMayLike = false
    private var hideUnfollowedGroups = false
    private var hideVerifiedPosts = false
    private var hideUnfollowedAccounts = false

    private val mobileUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    private val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences("FBS10_SETTINGS", Context.MODE_PRIVATE)
            loadSavedSettings()
            loadBookmarksFromStorage()

            webView = findViewById(R.id.webView)
            webView.setBackgroundColor(Color.BLACK)

            configureWebView()

            checkAndRequestStoragePermissions()

            val btnAdblock: ImageButton = findViewById(R.id.btn_adblock)
            val btnTools: ImageButton = findViewById(R.id.btn_tools)
            val btnBlockMedia: ImageButton = findViewById(R.id.btn_block_media)
            val btnStarGreen: ImageButton = findViewById(R.id.btn_star_green)
            val btnStarA: ImageButton = findViewById(R.id.btn_star_a)
            val btnShare: ImageButton = findViewById(R.id.btn_share)
            val btnDesktop: ImageButton = findViewById(R.id.btn_desktop)
            val btnNotifications: ImageButton = findViewById(R.id.btn_notifications)
            val btnMenu: ImageButton = findViewById(R.id.btn_menu)

            btnBlockMedia.setOnClickListener {
                isMediaBlocked = !isMediaBlocked
                saveSetting("isMediaBlocked", isMediaBlocked)
                showToast(if (isMediaBlocked) "تم حجب الوسائط" else "تم إظهار الوسائط")
                applyMediaFilter()
            }

            btnStarGreen.setOnClickListener {
                val currentUrl = webView.url ?: ""
                val currentTitle = webView.title ?: currentUrl
                if (currentUrl.isNotEmpty()) {
                    bookmarksList.add(Bookmark(currentTitle, currentUrl))
                    saveBookmarksToStorage()
                    showToast("تمت إضافة الصفحة للمفضلة")
                }
            }

            btnStarA.setOnClickListener {
                showBookmarksDialog()
            }

            btnAdblock.setOnClickListener {
                isAdBlockEnabled = !isAdBlockEnabled
                saveSetting("isAdBlockEnabled", isAdBlockEnabled)
                if (isAdBlockEnabled) {
                    applyAdBlockScript()
                    showToast("تم تفعيل مانع الإعلانات")
                } else {
                    showToast("تم إيقاف مانع الإعلانات")
                }
            }

            btnNotifications.setOnClickListener {
                isDesktopMode = false
                webView.settings.userAgentString = mobileUserAgent
                webView.settings.useWideViewPort = false
                webView.settings.loadWithOverviewMode = false
                webView.loadUrl("https://m.facebook.com/notifications")
            }

            btnShare.setOnClickListener {
                webView.settings.userAgentString = desktopUserAgent
                webView.settings.useWideViewPort = true
                webView.settings.loadWithOverviewMode = true
                webView.loadUrl("https://www.facebook.com/messages/")
            }

            btnDesktop.setOnClickListener {
                isDesktopMode = !isDesktopMode
                if (isDesktopMode) {
                    webView.settings.userAgentString = desktopUserAgent
                    webView.settings.useWideViewPort = true
                    webView.settings.loadWithOverviewMode = true
                    showToast("تم تفعيل وضع سطح المكتب")
                    val currentUrl = webView.url
                    if (currentUrl != null && currentUrl.contains("m.facebook.com")) {
                        webView.loadUrl(currentUrl.replace("m.facebook.com", "www.facebook.com"))
                    } else {
                        webView.loadUrl("https://www.facebook.com")
                    }
                } else {
                    webView.settings.userAgentString = mobileUserAgent
                    webView.settings.useWideViewPort = false
                    webView.settings.loadWithOverviewMode = false
                    showToast("تم إلغاء وضع سطح المكتب")
                    val currentUrl = webView.url
                    if (currentUrl != null && currentUrl.contains("www.facebook.com")) {
                        webView.loadUrl(currentUrl.replace("www.facebook.com", "m.facebook.com"))
                    } else {
                        webView.loadUrl("https://m.facebook.com")
                    }
                }
            }

            btnTools.setOnClickListener {
                showFilterMenuDialog()
            }

            btnMenu.setOnClickListener {
                isDesktopMode = false
                webView.settings.userAgentString = mobileUserAgent
                webView.settings.useWideViewPort = false
                webView.settings.loadWithOverviewMode = false
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("https://m.facebook.com")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("حدث خطأ في تشغيل التطبيق")
        }
    }

    private fun configureWebView() {
        try {
            val settings: WebSettings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true

            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = mobileUserAgent

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webView, true)
            }

            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            webView.webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!isDesktopMode && url != null && !url.contains("facebook.com/messages")) {
                        webView.settings.userAgentString = mobileUserAgent
                    }
                    applyMediaFilter()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (isAdBlockEnabled) applyAdBlockScript()
                    applyMediaFilter()
                    applyContentFilters()
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        applyAllCommentsScript()
                        applyMediaFilter()
                    }, 500)

                    try {
                        CookieManager.getInstance().flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent()
                    return try {
                        startActivityForResult(intent!!, FILE_CHOOSER_REQUEST_CODE)
                        true
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        showToast("تعذر فتح مدير الملفات")
                        false
                    }
                }
            }

            webView.loadUrl("https://m.facebook.com")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyAllCommentsScript() {
        val js = """
            javascript:(function() {
                if (window.isFetchingComments) return;
                window.isFetchingComments = true;

                function clickMoreComments() {
                    var buttons = document.querySelectorAll('div[role="button"], a, span');
                    var clicked = false;

                    for (var i = 0; i < buttons.length; i++) {
                        var text = (buttons[i].innerText || buttons[i].textContent || "").trim();
                        if (
                            text.indexOf('عرض المزيد من التعليقات') !== -1 || 
                            text.indexOf('View more comments') !== -1 ||
                            text.indexOf('عرض التعليقات السابقة') !== -1 ||
                            text.indexOf('View previous comments') !== -1 ||
                            text.indexOf('عرض ردود إضافية') !== -1 ||
                            text.indexOf('View more replies') !== -1 ||
                            text.indexOf('عرض كل التعليقات') !== -1 ||
                            text.indexOf('View all comments') !== -1
                        ) {
                            buttons[i].click();
                            clicked = true;
                        }
                    }

                    if (clicked) {
                        setTimeout(clickMoreComments, 1000);
                    } else {
                        window.isFetchingComments = false;
                    }
                }

                setTimeout(clickMoreComments, 300);
            })()
        """.trimIndent()
        webView.loadUrl(js)
    }

    private fun applyMediaFilter() {
        val css = if (isMediaBlocked) {
            """
            javascript:(function() {
                var style = document.getElementById('media-block-style');
                if(!style) {
                    style = document.createElement('style');
                    style.id = 'media-block-style';
                    document.head.appendChild(style);
                }
                style.innerHTML = "img:not([src*='emoji']):not([src*='static.xx']):not([class*='emoji']):not([alt*='emoji']):not([src*='rsrc.php']), video, svg:not([class*='like']), [style*='background-image'] { display: none !important; visibility: hidden !important; }";
            })()
            """.trimIndent()
        } else {
            """
            javascript:(function() {
                var style = document.getElementById('media-block-style');
                if(style) style.remove();
            })()
            """.trimIndent()
        }
        webView.loadUrl(css)
    }

    private fun applyContentFilters() {
        val jsScript = """
            javascript:(function() {
                var hideSponsored = $hideSponsored;
                var hideSuggestions = $hideSuggestions;
                var hidePeople = $hidePeopleYouMayKnow;
                var hideReels = $hideReels;
                var hideStories = $hideStories;
                var hidePages = $hidePagesYouMayLike;
                var hideUnfollowedGroups = $hideUnfollowedGroups;
                var hideVerified = $hideVerifiedPosts;
                var hideUnfollowed = $hideUnfollowedAccounts;

                function filterFeed() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        if (!v.paused) {
                            v.pause();
                        }
                        v.removeAttribute('autoplay');
                        v.autoplay = false;
                    });

                    var selectors = [
                        'div[data-story-id]',
                        'article',
                        'div[role="feed"] > div',
                        'div[id^="u_"]',
                        'div[class*="story"]',
                        'div[data-ft]',
                        'div[role="article"]',
                        'div[role="region"]',
                        'section'
                    ];
                    
                    var posts = document.querySelectorAll(selectors.join(','));
                    
                    posts.forEach(function(post) {
                        var text = post.innerText || post.textContent || "";
                        var html = post.innerHTML || "";
                        var shouldHide = false;

                        if (hideSponsored && !shouldHide) {
                            if (text.indexOf("مُموَّل") !== -1 || text.indexOf("Sponsored") !== -1 || text.indexOf("اعلان") !== -1 || html.indexOf("sponsored_link") !== -1) {
                                shouldHide = true;
                            }
                        }

                        if (hideSuggestions && !shouldHide) {
                            if (text.indexOf("اقتراحات قد تعجبك") !== -1 || 
                                text.indexOf("Suggested for you") !== -1 || 
                                text.indexOf("مقترحة لك") !== -1 || 
                                text.indexOf("منشورات مقترحة") !== -1 ||
                                text.indexOf("أشخاص قد تعرفهم") !== -1 || 
                                text.indexOf("People You May Know") !== -1 ||
                                text.indexOf("قصص") !== -1 || 
                                text.indexOf("Stories") !== -1 ||
                                html.indexOf("/stories/") !== -1 ||
                                post.querySelector('div[aria-label*="قصة"]') !== null) {
                                shouldHide = true;
                            }
                        }

                        if (hidePeople && !shouldHide) {
                            if (text.indexOf("أشخاص قد تعرفهم") !== -1 || text.indexOf("People You May Know") !== -1) {
                                shouldHide = true;
                            }
                        }

                        if (hideReels && !shouldHide) {
                            if (text.indexOf("ريلز") !== -1 || text.indexOf("Reels") !== -1 || text.indexOf("قطع فيديو قصيرة") !== -1 || html.indexOf("/reels/") !== -1) {
                                shouldHide = true;
                            }
                        }

                        if (hideStories && !shouldHide) {
                            if (text.indexOf("قصص") !== -1 || text.indexOf("Stories") !== -1 || html.indexOf("/stories/") !== -1 || post.querySelector('div[aria-label*="قصة"]') !== null) {
                                shouldHide = true;
                            }
                        }

                        if (hidePages && !shouldHide) {
                            if (text.indexOf("صفحات قد تعجبك") !== -1 || text.indexOf("Pages you may like") !== -1) {
                                shouldHide = true;
                            }
                        }

                        if (hideUnfollowedGroups && !shouldHide) {
                            if ((text.indexOf("انضمام") !== -1 || text.indexOf("Join") !== -1 || text.indexOf("مجموعة غير متابعة") !== -1 || text.indexOf("Suggested group") !== -1) && (text.indexOf("مجموعة") !== -1 || text.indexOf("Group") !== -1)) {
                                shouldHide = true;
                            }
                        }

                        if (hideVerified && !shouldHide) {
                            var hasVerifiedBadge = post.querySelector('svg[aria-label*="موّثق"], svg[aria-label*="Verified"], svg[aria-label*="الحساب الموثّق"], svg[title*="Verified"], i[aria-label*="Verified"]') !== null;
                            if (hasVerifiedBadge) {
                                shouldHide = true;
                            }
                        }

                        if (hideUnfollowed && !shouldHide) {
                            var hasFollowBtn = false;
                            var btns = post.querySelectorAll('div[role="button"], a, span');
                            btns.forEach(function(btn) {
                                var btnText = (btn.innerText || "").trim();
                                if (btnText === "متابعة" || btnText === "Follow" || btnText === "+ متابعة" || btnText === "+ Follow") {
                                    hasFollowBtn = true;
                                }
                            });
                            if (hasFollowBtn) {
                                shouldHide = true;
                            }
                        }

                        if (shouldHide) {
                            post.style.setProperty("display", "none", "important");
                            post.style.setProperty("visibility", "hidden", "important");
                        }
                    });
                }

                filterFeed();

                if (!window.filterObserver) {
                    window.filterObserver = new MutationObserver(function() {
                        filterFeed();
                    });
                    window.filterObserver.observe(document.body, { childList: true, subtree: true });
                }
            })()
        """.trimIndent()
        webView.loadUrl(jsScript)
    }

    private fun saveBookmarksToStorage() {
        try {
            val jsonArray = JSONArray()
            for (bookmark in bookmarksList) {
                val jsonObject = JSONObject()
                jsonObject.put("title", bookmark.title)
                jsonObject.put("url", bookmark.url)
                jsonArray.put(jsonObject)
            }
            prefs.edit().putString("SAVED_BOOKMARKS", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadBookmarksFromStorage() {
        bookmarksList.clear()
        try {
            val jsonString = prefs.getString("SAVED_BOOKMARKS", null)
            if (!jsonString.isNullOrEmpty()) {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val title = jsonObject.optString("title", "")
                    val url = jsonObject.optString("url", "")
                    if (url.isNotEmpty()) {
                        bookmarksList.add(Bookmark(title, url))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBookmarksDialog() {
        if (bookmarksList.isEmpty()) {
            showToast("قائمة المفضلات فارغة")
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("المفضلات")

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, bookmarksList)
        listView.adapter = adapter

        val dialog = builder.setView(listView)
            .setNegativeButton("إغلاق", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedBookmark = bookmarksList[position]
            webView.loadUrl(selectedBookmark.url)
            dialog.dismiss()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedBookmark = bookmarksList[position]
            val options = arrayOf("إعادة تسمية", "حذف")

            AlertDialog.Builder(this)
                .setTitle(selectedBookmark.title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showRenameDialog(selectedBookmark, adapter)
                        1 -> {
                            bookmarksList.removeAt(position)
                            saveBookmarksToStorage()
                            adapter.notifyDataSetChanged()
                            showToast("تم حذف المفضلة")
                            if (bookmarksList.isEmpty()) dialog.dismiss()
                        }
                    }
                }
                .show()
            true
        }

        dialog.show()
    }

    private fun showRenameDialog(bookmark: Bookmark, adapter: ArrayAdapter<Bookmark>) {
        val input = EditText(this)
        input.setText(bookmark.title)

        AlertDialog.Builder(this)
            .setTitle("إعادة تسمية المفضلة")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    bookmark.title = newTitle
                    saveBookmarksToStorage()
                    adapter.notifyDataSetChanged()
                    showToast("تم تغيير التسمية")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

            val listPermissionsNeeded = mutableListOf<String>()
            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(permission)
                }
            }

            if (listPermissionsNeeded.isNotEmpty()) {
                requestPermissions(listPermissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return
            val result = if (resultCode == RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }

    private fun saveSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun loadSavedSettings() {
        isAdBlockEnabled = prefs.getBoolean("isAdBlockEnabled", false)
        isMediaBlocked = prefs.getBoolean("isMediaBlocked", false)
        hideSponsored = prefs.getBoolean("hideSponsored", false)
        hideSuggestions = prefs.getBoolean("hideSuggestions", false)
        hidePeopleYouMayKnow = prefs.getBoolean("hidePeopleYouMayKnow", false)
        hideReels = prefs.getBoolean("hideReels", false)
        hideStories = prefs.getBoolean("hideStories", false)
        hidePagesYouMayLike = prefs.getBoolean("hidePagesYouMayLike", false)
        hideUnfollowedGroups = prefs.getBoolean("hideUnfollowedGroups", false)
        hideVerifiedPosts = prefs.getBoolean("hideVerifiedPosts", false)
        hideUnfollowedAccounts = prefs.getBoolean("hideUnfollowedAccounts", false)
    }

    private fun showFilterMenuDialog() {
        val options = arrayOf(
            "إخفاء الممول" + if (hideSponsored) " [مفعل]" else "",
            "إخفاء اقتراحات قد تعجبك" + if (hideSuggestions) " [مفعل]" else "",
            "إخفاء أشخاص قد تعرفهم" + if (hidePeopleYouMayKnow) " [مفعل]" else "",
            "إخفاء الريلز" + if (hideReels) " [مفعل]" else "",
            "إخفاء القصص" + if (hideStories) " [مفعل]" else "",
            "إخفاء صفحات قد تعجبك" + if (hidePagesYouMayLike) " [مفعل]" else "",
            "إخفاء منشورات المجموعات غير المتابعة" + if (hideUnfollowedGroups) " [مفعل]" else "",
            "إخفاء المنشورات ذات علامة الصح الزرقاء" + if (hideVerifiedPosts) " [مفعل]" else "",
            "إخفاء منشورات حسابات لم أتابعها" + if (hideUnfollowedAccounts) " [مفعل]" else ""
        )

        AlertDialog.Builder(this)
            .setTitle("مرشحات المحتوى")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { hideSponsored = !hideSponsored; saveSetting("hideSponsored", hideSponsored) }
                    1 -> { hideSuggestions = !hideSuggestions; saveSetting("hideSuggestions", hideSuggestions) }
                    2 -> { hidePeopleYouMayKnow = !hidePeopleYouMayKnow; saveSetting("hidePeopleYouMayKnow", hidePeopleYouMayKnow) }
                    3 -> { hideReels = !hideReels; saveSetting("hideReels", hideReels) }
                    4 -> { hideStories = !hideStories; saveSetting("hideStories", hideStories) }
                    5 -> { hidePagesYouMayLike = !hidePagesYouMayLike; saveSetting("hidePagesYouMayLike", hidePagesYouMayLike) }
                    6 -> { hideUnfollowedGroups = !hideUnfollowedGroups; saveSetting("hideUnfollowedGroups", hideUnfollowedGroups) }
                    7 -> { hideVerifiedPosts = !hideVerifiedPosts; saveSetting("hideVerifiedPosts", hideVerifiedPosts) }
                    8 -> { hideUnfollowedAccounts = !hideUnfollowedAccounts; saveSetting("hideUnfollowedAccounts", hideUnfollowedAccounts) }
                }
                applyContentFilters()
                showToast("تم تحديث الفلتر")
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun applyAdBlockScript() {
        webView.loadUrl("javascript:(function() { " +
                "var elements = document.querySelectorAll('iframe, .ad, .ads, .banner');" +
                "for(var i=0; i<elements.length; i++) { elements[i].remove(); }" +
                "})()")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
