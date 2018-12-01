package com.machinerychorus.nuggetcounter

import io.kweb.plugins.KWebPlugin

class JsCookiePlugin : KWebPlugin() {
    override fun decorate(startHead: StringBuilder, endHead: StringBuilder) {
        startHead.appendln("""
            <script src="https://cdn.jsdelivr.net/npm/js-cookie@2/src/js.cookie.min.js"></script>
        """.trimIndent())
    }
}

val jsCookiePlugin = JsCookiePlugin()