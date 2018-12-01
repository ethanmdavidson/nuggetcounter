package com.machinerychorus.nuggetcounter

import io.kweb.WebBrowser
import io.kweb.dom.element.Element
import io.kweb.dom.element.KWebDSL
import java.util.concurrent.CompletableFuture

fun Element.jsCookie(): JsCookieReceiver {
    assertPluginLoaded(JsCookiePlugin::class)
    return JsCookieReceiver(this.browser)
}

@KWebDSL
class JsCookieReceiver(internal val webBrowser: WebBrowser) {

    fun set(name:String, value:String) {
        webBrowser.evaluate("Cookies.set('$name', '$value');")
    }

    //TODO: should return null when cookie with this name doesn't exist. currently returns empty string
    fun get(name:String) : CompletableFuture<String> {
        return webBrowser.evaluate("Cookies.get('$name');")
    }

    fun remove(name:String){
        webBrowser.evaluate("Cookies.remove('$name');")
    }
}