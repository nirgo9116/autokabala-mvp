package com.autokabala.listener

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class ICountWebViewActivity : ComponentActivity() {

    companion object {
        private const val TAG             = "ICountWebView"
        private const val EXTRA_USER        = "icount_user"
        private const val EXTRA_CID         = "icount_cid"
        private const val EXTRA_PASS        = "icount_pass"
        private const val EXTRA_CLIENT_NAME = "client_name"
        private const val EXTRA_AMOUNT      = "amount"
        private const val EXTRA_DESCRIPTION = "description"
        private const val ICOUNT_URL        = "https://app.icount.co.il/hash/create_doc.php?doctype=receipt"

        fun launch(
            context:     Context,
            user:        String,
            cid:         String,
            pass:        String,
            clientName:  String,
            amount:      Double,
            description: String
        ) {
            context.startActivity(
                Intent(context, ICountWebViewActivity::class.java)
                    .putExtra(EXTRA_USER,        user)
                    .putExtra(EXTRA_CID,         cid)
                    .putExtra(EXTRA_PASS,        pass)
                    .putExtra(EXTRA_CLIENT_NAME, clientName)
                    .putExtra(EXTRA_AMOUNT,      amount)
                    .putExtra(EXTRA_DESCRIPTION, description)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private lateinit var webView: WebView
    private var step1Injected = false  // client selection injected
    private var step2Injected = false  // amount + description injected

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientName  = intent.getStringExtra(EXTRA_CLIENT_NAME) ?: ""
        val amount      = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val amountStr   = if (amount == amount.toLong().toDouble())
            amount.toLong().toString() else "%.2f".format(amount)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            // Pipe JS console → Logcat
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    Log.d(TAG, "[JS ${msg.messageLevel()}] ${msg.message()} @ ${msg.sourceId()}:${msg.lineNumber()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "onPageFinished: $url")
                    when {
                        // Not on the receipt page yet → auto-login
                        !url.contains("create_doc", ignoreCase = true) ->
                            autoLogin(view)

                        // Step 2: URL now has client_id= → fill amount + description
                        url.contains("client_id=", ignoreCase = true) && !step2Injected -> {
                            step2Injected = true
                            view.postDelayed({
                                autoFillStep2(view, amountStr, description)
                            }, 800)
                        }

                        // Step 1: initial receipt page → select client
                        !step1Injected -> {
                            step1Injected = true
                            view.postDelayed({
                                autoFillStep1(view, clientName)
                            }, 800)
                        }
                    }
                }
            }
            loadUrl(ICOUNT_URL)
        }

        val banner = TextView(this).apply {
            text = "ממלא אוטומטית — לקוח: $clientName  |  סכום: ₪$amountStr"
            textSize = 12f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFF1C3A2B.toInt())
            setTextColor(0xFFB2DFDB.toInt())
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(banner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    // ── Auto-login ──────────────────────────────────────────────────────────────
    private fun autoLogin(view: WebView) {
        val user = intent.getStringExtra(EXTRA_USER)?.takeIf { it.isNotBlank() } ?: return
        val cid  = intent.getStringExtra(EXTRA_CID) ?.takeIf { it.isNotBlank() } ?: return
        val pass = intent.getStringExtra(EXTRA_PASS)?.takeIf { it.isNotBlank() } ?: return
        fun String.esc() = replace("\\", "\\\\").replace("'", "\\'")

        view.evaluateJavascript("""
            (function() {
                function fill(el, val) {
                    if (!el) return;
                    var s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    s.call(el, val);
                    el.dispatchEvent(new Event('input',  {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                }
                var u = document.querySelector('input[name="user"]')
                     || document.querySelector('input[type="email"]')
                     || document.querySelector('input[placeholder*="\u05de\u05d9\u05d9\u05dc"]');
                var c = document.querySelector('input[name="cid"]')
                     || document.querySelector('input[placeholder*="\u05de\u05d6\u05d4\u05d4"]');
                var p = document.querySelector('input[type="password"]');
                fill(u, '${user.esc()}');
                fill(c, '${cid.esc()}');
                fill(p, '${pass.esc()}');
                setTimeout(function() {
                    var btn = document.querySelector('button[type="submit"]')
                           || document.querySelector('input[type="submit"]');
                    if (btn) btn.click();
                }, 300);
            })();
        """.trimIndent(), null)
    }

    // ── Step 1: select client via Bootstrap Select ──────────────────────────────
    private fun autoFillStep1(view: WebView, clientName: String) {
        fun String.esc() = replace("\\", "\\\\").replace("'", "\\'")

        view.evaluateJavascript("""
            (function() {
                var CLIENT = '${clientName.esc()}';

                function poll(fn, cb, maxMs) {
                    var start = Date.now();
                    var t = setInterval(function() {
                        var el = fn();
                        if (el) { clearInterval(t); cb(el); }
                        else if (Date.now() - start > (maxMs || 15000)) clearInterval(t);
                    }, 200);
                }

                poll(function() {
                    return document.querySelector('select#ac_me')
                        || document.querySelector('select[name="selectedclient"]');
                }, function(sel) {
                    var opts = sel.options;
                    var firstWord = CLIENT.split(' ')[0];
                    var match = null;
                    for (var i = 0; i < opts.length; i++) {
                        if (opts[i].text.trim().includes(CLIENT)) { match = opts[i]; break; }
                        if (!match && opts[i].text.trim().includes(firstWord)) match = opts[i];
                    }
                    if (!match) {
                        console.log('No option found for: ' + CLIENT + ' (options: ' + opts.length + ')');
                        return;
                    }
                    console.log('Selecting client: ' + match.text + ' val=' + match.value);
                    sel.value = match.value;
                    if (window.$ && ${'$'}(sel).data('selectpicker')) {
                        ${'$'}(sel).selectpicker('val', match.value);
                    }
                    sel.dispatchEvent(new Event('change', {bubbles: true}));
                    if (window.$) ${'$'}(sel).trigger('change');

                    // Click advance button if the form doesn't navigate automatically
                    setTimeout(function() {
                        var btn = document.querySelector('button.btn-primary:not([disabled])')
                               || document.querySelector('input[type="submit"]')
                               || document.querySelector('button[type="submit"]');
                        if (btn && btn.offsetParent !== null) {
                            console.log('Clicking advance button: ' + btn.textContent.trim());
                            btn.click();
                        }
                    }, 400);
                }, 10000);
            })();
        """.trimIndent(), null)
    }

    // ── Step 2: fill amount + description (runs after page navigates to client_id= URL) ──
    private fun autoFillStep2(view: WebView, amount: String, description: String) {
        fun String.esc() = replace("\\", "\\\\").replace("'", "\\'")

        view.evaluateJavascript("""
            (function() {
                var AMOUNT      = '${amount.esc()}';
                var DESCRIPTION = '${description.esc()}';

                function nativeFill(el, val) {
                    var proto = (el.tagName === 'TEXTAREA')
                        ? window.HTMLTextAreaElement.prototype
                        : window.HTMLInputElement.prototype;
                    var s = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    s.call(el, val);
                    ['input','keyup','change','blur'].forEach(function(ev) {
                        el.dispatchEvent(new Event(ev, {bubbles:true}));
                    });
                    if (window.$) ${'$'}(el).trigger('change').trigger('input').trigger('keyup');
                }

                function poll(fn, cb, maxMs) {
                    var start = Date.now();
                    var t = setInterval(function() {
                        var el = fn();
                        if (el) { clearInterval(t); cb(el); }
                        else if (Date.now() - start > (maxMs || 15000)) {
                            clearInterval(t);
                            fn(); // last attempt without visibility check
                        }
                    }, 200);
                }

                // Debug: dump all inputs AND contenteditable elements
                function dumpFields(label) {
                    var all = document.querySelectorAll('input,textarea,select');
                    var names = [];
                    for (var i = 0; i < all.length; i++) {
                        var e = all[i];
                        names.push((e.name||'?') + ':' + (e.type||'?') + ':' + (e.offsetParent?'V':'H'));
                    }
                    console.log(label + '_INPUTS: ' + names.join(' | '));
                    var edits = document.querySelectorAll('[contenteditable]');
                    var editInfo = [];
                    for (var j = 0; j < edits.length; j++) {
                        var ed = edits[j];
                        editInfo.push((ed.className||'?') + ':' + (ed.getAttribute('data-field')||ed.getAttribute('data-name')||'?') + ':' + (ed.offsetParent?'V':'H') + ':' + ed.textContent.trim().substring(0,10));
                    }
                    console.log(label + '_EDITABLE: ' + editInfo.join(' | '));
                }
                setTimeout(function() { dumpFields('T1500'); }, 1500);
                setTimeout(function() { dumpFields('T4000'); }, 4000);

                // Amount / price field — try every known iCount naming pattern
                poll(function() {
                    var el = document.querySelector('input[name="items[0][unitprice]"]')
                          || document.querySelector('input[name="unitprice[0]"]')
                          || document.querySelector('input[name="items[0][price]"]')
                          || document.querySelector('input[name="price[0]"]')
                          || document.querySelector('input[name="items[0][amount]"]')
                          || document.querySelector('input[name="amount[0]"]')
                          || document.querySelector('input[name="item_price[0]"]')
                          || document.querySelector('input[name="price"]')
                          || document.querySelector('input[name="amount"]')
                          || document.querySelector('input[name="unitprice"]')
                          || document.querySelector('input.price')
                          || document.querySelector('input.unitprice')
                          || document.querySelector('input[placeholder*="\u05de\u05d7\u05d9\u05e8"]')
                          || document.querySelector('input[placeholder*="\u05e1\u05db\u05d5\u05dd"]');
                    if (el && el.offsetParent !== null) return el;
                    // Broad fallback: any visible writable input whose name suggests price
                    var inputs = document.querySelectorAll('input[type="number"],input[type="text"]');
                    for (var i = 0; i < inputs.length; i++) {
                        var inp = inputs[i];
                        if (!inp.offsetParent || inp.readOnly || inp.disabled) continue;
                        var n = (inp.name || '').toLowerCase();
                        if (n.indexOf('cash') !== -1 || n.indexOf('qty') !== -1 || n.indexOf('quan') !== -1 || n.indexOf('discount') !== -1 || n.indexOf('vat') !== -1) continue;
                        if (n.indexOf('price') !== -1 || n.indexOf('unitprice') !== -1 || n.indexOf('amount') !== -1) return inp;
                    }
                    return null;
                }, function(el) {
                    console.log('Amount field found: name=' + el.name + ' type=' + el.type);
                    nativeFill(el, AMOUNT);
                    // Try to trigger iCount's own row-total recalculation
                    setTimeout(function() {
                        if (typeof calcRow === 'function') { try { calcRow(0); } catch(e){} }
                        if (typeof calc_total === 'function') { try { calc_total(); } catch(e){} }
                        if (typeof recalc === 'function') { try { recalc(); } catch(e){} }
                        // Also try to directly fill the row-total field
                        var totalEl = document.querySelector('input[name="items[0][total]"]')
                                   || document.querySelector('input[name="total[0]"]')
                                   || document.querySelector('input[name="items[0][sum]"]')
                                   || document.querySelector('input[name="items[0][price_total]"]')
                                   || document.querySelector('input[name="line_total[0]"]');
                        if (totalEl) { console.log('Row-total field: name=' + totalEl.name); nativeFill(totalEl, AMOUNT); }
                    }, 400);
                }, 15000);

                // Description field
                poll(function() {
                    var el = document.querySelector('input[name="description"]')
                          || document.querySelector('textarea[name="description"]')
                          || document.querySelector('input[name="item_desc"]')
                          || document.querySelector('input[name="details"]')
                          || document.querySelector('input[placeholder*="\u05ea\u05d9\u05d0\u05d5\u05e8"]')
                          || document.querySelector('textarea[placeholder*="\u05ea\u05d9\u05d0\u05d5\u05e8"]');
                    return (el && el.offsetParent !== null) ? el : null;
                }, function(el) {
                    console.log('Description field found: name=' + el.name);
                    if (DESCRIPTION) nativeFill(el, DESCRIPTION);
                }, 15000);

                // Payment method: click the ⊕ אמצעי תשלום add button,
                // then select מזומן, then fill the sum field
                setTimeout(function() {
                    // Find the "add payment" button — contains "אמצעי תשלום" text and is a link/button
                    var addBtn = null;
                    var candidates = document.querySelectorAll('a, button, span');
                    for (var i = 0; i < candidates.length; i++) {
                        var el = candidates[i];
                        var txt = el.textContent.trim();
                        if (txt.indexOf('\u05d0\u05de\u05e6\u05e2\u05d9 \u05ea\u05e9\u05dc\u05d5\u05dd') !== -1
                            && (el.tagName === 'A' || el.tagName === 'BUTTON' || el.closest('a') || el.closest('button'))) {
                            addBtn = el.closest('a') || el.closest('button') || el;
                            break;
                        }
                    }
                    if (!addBtn) { console.log('Add payment button not found'); return; }
                    console.log('Clicking add payment button: ' + addBtn.textContent.trim().substring(0,30));
                    addBtn.click();

                    // After the payment section opens, select מזומן and fill sum
                    setTimeout(function() {
                        // Look for מזומן option (radio, button, or list item)
                        var mazBtn = null;
                        var opts = document.querySelectorAll('input[type="radio"], a, button, li, label');
                        for (var j = 0; j < opts.length; j++) {
                            var t = (opts[j].textContent || opts[j].value || '').trim();
                            if (t === '\u05de\u05d6\u05d5\u05de\u05df' || t.indexOf('\u05de\u05d6\u05d5\u05de\u05df') === 0) {
                                mazBtn = opts[j]; break;
                            }
                        }
                        if (mazBtn) {
                            console.log('Clicking מזומן: tag=' + mazBtn.tagName);
                            mazBtn.click();
                        } else {
                            console.log('\u05de\u05d6\u05d5\u05de\u05df option not found after add click');
                        }

                        // Fill cash sum after מזומן is selected
                        setTimeout(function() {
                            var sumEl = document.querySelector('input[name="cash[sum]"]')
                                     || document.querySelector('input[name="cash[0][sum]"]')
                                     || document.querySelector('input[name*="cash"][name*="sum"]');
                            if (sumEl && sumEl.offsetParent !== null) {
                                console.log('Cash sum fill: name=' + sumEl.name);
                                nativeFill(sumEl, AMOUNT);
                            } else {
                                console.log('Cash sum field not visible after מזומן select');
                            }
                        }, 600);
                    }, 600);
                }, 3000);

            })();
        """.trimIndent(), null)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
