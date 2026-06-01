package com.hanako.mobileshell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_URL = "http://192.168.31.11:14500/mobile/"
        private const val PREFS_NAME = "hanako_shell"
        private const val KEY_URL = "server_url"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val LOAD_TIMEOUT_MS = 15_000L
        private const val FILE_CHOOSER_REQUEST = 1001
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var errorDetail: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnChangeUrl: Button
    private lateinit var prefs: SharedPreferences

    private var currentUrl = DEFAULT_URL
    private var accessKey = ""

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var isLoading = false
    private var hasMainFrameError = false
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentUrl = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        accessKey = prefs.getString(KEY_ACCESS_KEY, "") ?: ""

        rootContainer = findViewById(R.id.root_container)
        webView = findViewById(R.id.web_view)
        errorView = findViewById(R.id.error_view)
        errorDetail = findViewById(R.id.error_detail)
        btnRetry = findViewById(R.id.btn_retry)
        btnChangeUrl = findViewById(R.id.btn_change_url)

        setupSystemBarInsets()
        setupWebView()
        setupBackPress()
        setupRetry()
        setupChangeUrl()

        if (savedInstanceState == null) {
            loadTargetUrl()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null)
        rootContainer.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun setupSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(0x00000000)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return if (isAllowedTargetUri(uri)) false
                else { showError(getString(R.string.error_external_blocked)); true }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                isLoading = true; hasMainFrameError = false; hideError(); startLoadTimeout()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false; cancelLoadTimeout()
                if (!hasMainFrameError) {
                    hideError()
                    // Bootstrap 轮询 DOM 就绪，其余注入延迟统一缩短为 300ms
                    injectBootstrapLoader()
                    webView.postDelayed({
                        // Back nav V2: preview > jian > sidebar > exit priority
                        webView.loadUrl("javascript:" + "if(!window.__hanaBackNav){window.__hanaBackNav=true;window.__hanaHandleBack=function(){var pb=document.getElementById('tbTogglePreview');if(pb&&pb.classList.contains('active')){console.log('[Shell] back: closing preview');pb.click();return'handled'}var jp=document.getElementById('jianSidebar');if(jp&&!jp.classList.contains('collapsed')){console.log('[Shell] back: closing jian panel directly');var sc=document.querySelector('button.mobile-drawer-scrim[aria-label=\"关闭侧边栏\"]');if(sc){sc.click();return'handled'}var jb=document.getElementById('tbToggleLeft');if(jb){jb.click();return'handled'}}var sb=document.querySelector('[class*=\"sidebar\" i]');if(sb&&sb.offsetWidth>50){var cs2=getComputedStyle(sb);if(cs2.display!=='none'&&cs2.visibility!=='hidden'){console.log('[Shell] back: closing sidebar');var bt=document.getElementById('tbToggleLeft');if(bt){bt.click();return'handled'}}}var btn=document.getElementById('tbToggleLeft');if(btn&&btn.offsetParent){console.log('[Shell] back: opening sidebar');btn.click();return'handled'}console.log('[Shell] back: exiting');return'exit'};}")
                        // Enter fix: window capture + ProseMirror view API, fallback to br insertion
                        webView.loadUrl("javascript:" + "(function(){if(window.__hanaEnterFix)return;window.__hanaEnterFix=true;function getPMView(el){var d=el.pmViewDesc;while(d){if(d.node&&d.node.view)return d.node.view;d=d.parent}var k=Object.keys(el).find(function(x){return x.startsWith('__reactFiber')});if(k){var f=el[k];while(f){if(f.stateNode&&f.stateNode.view)return f.stateNode.view;f=f.return}}return null}window.addEventListener('keydown',function(e){if(!e.isTrusted)return;if(e.key!=='Enter'||e.shiftKey||e.ctrlKey||e.altKey||e.metaKey||e.isComposing)return;var t=e.target;var pm=t.closest&&t.closest('.ProseMirror')||document.querySelector('.ProseMirror');if(!pm)return;if(!t.isContentEditable&&!t.closest('[contenteditable],textarea,[role=textbox]'))return;e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();var view=getPMView(pm);if(view){view.dispatch(view.state.tr.insertText('\\n'));console.log('[Shell] Enter: inserted via ProseMirror view')}else{var sel=window.getSelection();if(sel.rangeCount>0){var r=sel.getRangeAt(0);r.deleteContents();var br=document.createElement('br');r.insertNode(br);r.setStartAfter(br);r.collapse(true);sel.removeAllRanges();sel.addRange(r)}console.log('[Shell] Enter: fallback br insertion')}},true)})()")
                        // Auto-close sidebar on chat selection
                        webView.loadUrl("javascript:" + "(function(){if(window.__hanaAutoCloseSb)return;window.__hanaAutoCloseSb=true;document.addEventListener('click',function(e){var sb=document.querySelector('[class*=\"sidebar\" i]');if(!sb||sb.offsetWidth<50)return;if(!sb.contains(e.target))return;if(e.target.closest('#tbToggleLeft,#newSessionBtn,#sidebarCollapseBtn,button[aria-label],.tb-toggle,.sidebar-action-btn'))return;console.log('[Shell] auto-close: click in sidebar, closing');setTimeout(function(){var bt=document.getElementById('tbToggleLeft');if(bt){console.log('[Shell] auto-close: toggling');bt.click()}},300)},true)})()")
                    }, 300)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    isLoading = false; hasMainFrameError = true; cancelLoadTimeout()
                    android.util.Log.e("MobileShell", "Error: ${error?.description} at ${request.url}")
                    showError(getString(R.string.error_default_detail))
                }
            }
        }

        webView.setDownloadListener { url, _, _, _, _ -> android.util.Log.w("MobileShell", "Download: $url") }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: android.webkit.ConsoleMessage?): Boolean {
                cm?.let { android.util.Log.d("HanakoShell", "[Web] ${it.messageLevel()}: ${it.message()}") }
                return true
            }

            override fun onShowFileChooser(wv: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = cb
                val intent = params?.createIntent()
                if (intent != null) { try { startActivityForResult(intent, FILE_CHOOSER_REQUEST) } catch (_: Exception) { fileChooserCallback = null } }
                else { fileChooserCallback = null }
                return true
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = if (resultCode == RESULT_OK && data?.data != null) arrayOf(data.data!!) else emptyArray()
            fileChooserCallback?.onReceiveValue(results); fileChooserCallback = null
        }
    }

    // ── Back Press: single evaluateJavascript, no loadUrl race ──
    // 'handled' = sidebar was opened, eat the event
    // 'exit'    = sidebar already visible, exit app
    // 'back'    = no sidebar found, try WebView goBack
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(window.__hanaHandleBack&&window.__hanaHandleBack())||'back'") { result ->
                    val state = result?.trim('"') ?: "back"
                    when (state) {
                        "handled" -> { /* sidebar toggled, do nothing */ }
                        "exit" -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                        else -> {
                            if (webView.canGoBack()) webView.goBack()
                            else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                        }
                    }
                }
            }
        })
    }

    private fun setupRetry() { btnRetry.setOnClickListener { loadTargetUrl() } }

    private fun setupChangeUrl() {
        btnChangeUrl.setOnClickListener {
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 32, 64, 16) }
            val urlInput = EditText(this).apply { setText(currentUrl); setSingleLine(true); hint = getString(R.string.dialog_hint_url) }
            val keyInput = EditText(this).apply { setText(accessKey); setSingleLine(true); hint = "桌面端显示的访问密钥" }
            layout.addView(TextView(this).apply { text = "服务器地址"; textSize = 13f })
            layout.addView(urlInput)
            layout.addView(TextView(this).apply { text = "访问密钥（可留空）"; textSize = 13f; setPadding(0, 24, 0, 0) })
            layout.addView(keyInput)
            AlertDialog.Builder(this).setTitle(R.string.dialog_title_change_url).setView(layout)
                .setPositiveButton("确定") { _, _ ->
                    val url = urlInput.text.toString().trim()
                    if (url.isNotEmpty()) {
                        currentUrl = if (url.endsWith("/")) url else "$url/"
                        accessKey = keyInput.text.toString().trim()
                        prefs.edit().putString(KEY_URL, currentUrl).putString(KEY_ACCESS_KEY, accessKey).apply()
                        CookieManager.getInstance().removeAllCookies(null)
                        loadTargetUrl()
                    }
                }.setNegativeButton("取消", null).show()
        }
    }

    // ── Bootstrap: poll for DOM readiness ─────────────────────

    private fun injectBootstrapLoader() {
        val s = "(function(){if(window.__hanaBoot)return;window.__hanaBoot=true;" +
            "var MAX=40,INT=200,TRIES=0;" +
            "function poll(){" +
            "if(document.readyState!=='complete'){if(++TRIES<MAX)setTimeout(poll,INT);return};" +
            "var root=document.querySelector('.mobile-desktop-root,.ProseMirror,body');" +
            "if(!root){if(++TRIES<MAX){setTimeout(poll,INT);return}}" +
            "window.__hanaDomReady=(Date.now());" +
            "console.log('[Shell] DOM ready t='+(TRIES*INT+100)+'ms')" +
            "};setTimeout(poll,100)})()"
        webView.loadUrl("javascript:" + s)
    }

    // ── Enter Key: force newline instead of send ──────────────
    // Strategy: intercept real Enter → preventDefault → dispatch synthetic
    // Enter on ProseMirror element so its keymap handles it natively.
    // Guard !e.isTrusted avoids infinite loop on the synthetic event.

    private fun injectEnterKeyFix() {
        val s = "(function(){" +
            "if(window.__hanaEnterFix)return;window.__hanaEnterFix=true;" +
            "document.addEventListener('keydown',function(e){" +
            "if(!e.isTrusted)return;" +
            "if(e.key!=='Enter'||e.shiftKey||e.ctrlKey||e.altKey||e.metaKey||e.isComposing)return;" +
            "var t=e.target;" +
            "if(!t.isContentEditable&&!t.closest('.ProseMirror,[contenteditable],textarea,[role=textbox]'))return;" +
            "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();" +
            "var pm=t.closest('.ProseMirror')||document.querySelector('.ProseMirror');" +
            "if(pm){pm.focus();" +
            "var se=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true,composed:true});" +
            "pm.dispatchEvent(se);}" +
            "},true)})"
        android.util.Log.d("HanakoShell", "EnterKey JS len=" + s.length)
        android.util.Log.d("HanakoShell", "EnterKey JS tail=" + s.substring(Math.max(0, s.length - 100)))
        webView.loadUrl("javascript:" + s)
    }

    // ── Back Navigation: open sidebar (chat list) ────────────

    private fun injectBackNavigation() {
        val s = "if(!window.__hanaBackNav){window.__hanaBackNav=true;" +
            "window.__hanaHandleBack=function(){" +
            "var sb=document.querySelector('[class*=\"sidebar\" i]:not(.collapsed):not(.closed)');" +
            "if(sb&&sb.offsetWidth>0)return false;" +
            "var btn=document.getElementById('tbToggleLeft');" +
            "if(btn&&btn.offsetParent){btn.click();return true}" +
            "btn=document.querySelector('[class*=\"toggle-left\" i],[class*=\"tb-toggle\" i],[aria-label*=\"sidebar\" i]');" +
            "if(btn&&btn.offsetParent){btn.click();return true}" +
            "return false};}"
        android.util.Log.d("HanakoShell", "BackNav JS: " + s)
        webView.loadUrl("javascript:" + s)
    }

    // ── DOM Diagnostic (one-shot, remove after debugging) ────

    private fun injectDomDiagnostic() {
        val s = "(function(){try{var R={};" +
            "var btn=document.getElementById('tbToggleLeft');" +
            "R.toggleBtn=btn?{cls:btn.className,vis:btn.offsetHeight>0}:null;" +
            "var sb=document.querySelector('[class*=\"sidebar\" i]:not([class*=\"no-sidebar\"])');" +
            "R.sidebar=sb?{cls:sb.className.substring(0,80),vis:sb.offsetHeight>0,disp:getComputedStyle(sb).display,kids:sb.children.length}:null;" +
            "if(!sb){var all=document.querySelectorAll('*');R.sidebarSearch=[];" +
            "for(var i=0;i<all.length&&i<500;i++){var c=all[i].className;if(typeof c==='string'&&c.indexOf('sidebar')>=0)R.sidebarSearch.push({tag:all[i].tagName,cls:c.substring(0,60),vis:all[i].offsetHeight>0})}}" +
            "var pm=document.querySelector('.ProseMirror');" +
            "R.editor=pm?{cls:pm.className.substring(0,80),tag:pm.tagName,ce:pm.contentEditable,hasView:!!(pm.pmViewDesc&&pm.pmViewDesc.view)}:null;" +
            "var ia=pm?pm.closest('[class*=input]'):null;" +
            "R.inputArea=ia?{cls:ia.className.substring(0,80)}:null;" +
            "var sendBtns=document.querySelectorAll('[class*=send]');R.sendBtns=[];" +
            "for(var i=0;i<sendBtns.length;i++){var c=typeof sendBtns[i].className==='string'?sendBtns[i].className:'';R.sendBtns.push({cls:c.substring(0,60),tag:sendBtns[i].tagName,vis:sendBtns[i].offsetHeight>0})};" +
            "var allBtns=document.querySelectorAll('button[id]');R.btnsWithId=[];" +
            "for(var i=0;i<allBtns.length&&i<10;i++){R.btnsWithId.push({id:allBtns[i].id,cls:allBtns[i].className.substring(0,40)})};" +
            "console.log('HANAKO_DOM_DIAG:'+JSON.stringify(R));" +
            "}catch(e){console.log('HANAKO_DOM_DIAG_ERR:'+e.message)}})()"
        webView.loadUrl("javascript:" + s)
    }

    // ── Pull-UP Refresh (bottom to top) ───────────────────────

    private fun injectPullUpRefresh() {
        val s = "(function puRef(){if(window.__hanaPullRefresh)return;" +
            "if(!window.__hanaDomReady){setTimeout(puRef,200);return};" +
            "window.__hanaPullRefresh=true;" +
            "var TH=80,MP=140,CD=2000,LR=0;" +
            "var ind=document.createElement('div');ind.id='hana-pull-indicator';" +
            "ind.innerHTML='<div class=\\\"hana-pull-spinner\\\"></div><span class=\\\"hana-pull-text\\\">上拉刷新</span>';" +
            "document.body.appendChild(ind);" +
            "var st=document.createElement('style');st.id='hana-pull-style';" +
            "st.textContent='#hana-pull-indicator{position:fixed;bottom:-60px;left:50%;transform:translateX(-50%);display:flex;align-items:center;gap:8px;z-index:99999;opacity:0;transition:bottom .2s ease-out,opacity .2s ease-out;pointer-events:none;padding:6px 16px;border-radius:20px;background:rgba(0,0,0,.06);backdrop-filter:blur(8px)}" +
            ".hana-pull-spinner{width:18px;height:18px;border:2px solid rgba(0,0,0,.1);border-top-color:rgba(0,0,0,.45);border-radius:50%}" +
            ".hana-pull-spinner.spinning{animation:hanaspin .7s linear infinite}" +
            ".hana-pull-text{font-size:13px;color:rgba(0,0,0,.45);user-select:none}" +
            "@keyframes hanaspin{to{transform:rotate(360deg)}}';" +
            "document.head.appendChild(st);" +
            "var sp=ind.querySelector('.hana-pull-spinner'),tx=ind.querySelector('.hana-pull-text');" +
            "var sy=0,sx=0,trk=false,pd=0;" +
            "var fs=function(t){var n=t;while(n&&n!==document.body){if(n.scrollHeight>n.clientHeight+4){var cs=window.getComputedStyle(n);if(/(auto|scroll|overlay)/.test(cs.overflowY))return n}n=n.parentElement}return document.scrollingElement||document.documentElement};" +
            "document.addEventListener('touchstart',function(e){if(e.touches.length!==1)return;var sc=fs(e.target);if(sc&&sc.scrollTop+sc.clientHeight<sc.scrollHeight-4)return;" +
            "sy=e.touches[0].clientY;sx=e.touches[0].clientX;trk=true;pd=0},{passive:true,capture:true});" +
            "document.addEventListener('touchmove',function(e){if(!trk)return;var dy=sy-e.touches[0].clientY;var dx=Math.abs(sx-e.touches[0].clientX);" +
            "if(dy<0||dx>dy*1.5){trk=false;return}pd=Math.min(dy,MP);var pg=Math.min(pd/TH,1);" +
            "ind.style.bottom=(-60+pd*0.6)+'px';ind.style.opacity=String(pg*0.9);sp.style.transform='rotate('+(pd*2.5)+')';" +
            "tx.textContent=pd>=TH?'松手刷新':'上拉刷新'},{passive:true,capture:true});" +
            "document.addEventListener('touchend',function(){if(!trk)return;trk=false;" +
            "if(pd>=TH){var now=Date.now();if(now-LR<CD){reset();return}LR=now;" +
            "ind.style.bottom='12px';ind.style.opacity='1';sp.classList.add('spinning');tx.textContent='正在刷新...';" +
            "setTimeout(function(){window.location.reload()},350)}else{reset()}" +
            "function reset(){ind.style.bottom='-60px';ind.style.opacity='0';setTimeout(function(){sp.classList.remove('spinning');tx.textContent='上拉刷新';sp.style.transform=''},200)}}" +
            "},{passive:true,capture:true})}catch(_){}})();"
        webView.loadUrl("javascript:" + s)
    }

    // ── Access Key Auto-Fill ──────────────────────────────────

    private fun injectAccessKey() {
        if (accessKey.isEmpty()) return
        val ek = accessKey
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val s = "(function akFill(){if(!window.__hanaDomReady){setTimeout(akFill,200);return};" +
            "if(window.__hanaAccessKey)return;window.__hanaAccessKey=true;" +
            "try{var k='" + ek + "';" +
            "var f=function(){var els=document.querySelectorAll('input[type=password],input[type=text]');" +
            "for(var i=0;i<els.length;i++){var el=els[i];var lb=((el.placeholder||'')+(el.name||'')+(el.id||'')).toLowerCase();" +
            "if(/key|密钥|access|密码|token|password/.test(lb)){el.value=k;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return true}}" +
            "var pw=document.querySelector('input[type=password]');if(pw){pw.value=k;pw.dispatchEvent(new Event('input',{bubbles:true}));return true}return false};" +
            "if(!f()){setTimeout(f,500);setTimeout(f,1500)}}catch(_){}})();"
        webView.loadUrl("javascript:" + s)
    }

    // ── Load & Error ──────────────────────────────────────────

    private fun loadTargetUrl() { hasMainFrameError = false; hideError(); webView.loadUrl(currentUrl) }
    private fun showError(d: String) { errorDetail.text = d; errorView.visibility = View.VISIBLE }
    private fun hideError() { errorView.visibility = View.GONE }
    private fun startLoadTimeout() { cancelLoadTimeout(); timeoutHandler.postDelayed({ if (isLoading) { isLoading = false; hasMainFrameError = true; webView.stopLoading(); showError(getString(R.string.error_timeout_detail)) } }, LOAD_TIMEOUT_MS) }
    private fun cancelLoadTimeout() { timeoutHandler.removeCallbacksAndMessages(null) }
    private fun isAllowedTargetUri(uri: Uri): Boolean { try { val s = Uri.parse(currentUrl); val p = if (uri.port == -1) 80 else uri.port; val sp = if (s.port == -1) 80 else s.port; return uri.host == s.host && p == sp } catch (_: Exception) { return false } }
}
