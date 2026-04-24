package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val userAgent: String,
    val clientId: Int,
    val clientScreen: String? = null,
    val osVersion: String? = null,
    val referer: String? = null,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            clientScreen = clientScreen,
            osVersion = osVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData
        )
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"

        private const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        private const val USER_AGENT_MWEB = "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)"
        private const val USER_AGENT_TVHTML5 = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
        private const val USER_AGENT_ANDROID_VR = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        val MWEB = YouTubeClient(
            clientName = "MWEB",
            clientVersion = "2.20260316.00.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_MWEB,
            clientId = 2,
            clientScreen = "WATCH"
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260316.01.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_WEB,
            clientId = 1,
            clientScreen = "WATCH"
        )

        val WEB_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "WEB_EMBEDDED_PLAYER",
            clientVersion = "1.20260316.01.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_WEB,
            clientId = 56,
            clientScreen = "EMBED"
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260311.03.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            clientId = 67,
            clientScreen = "WATCH",
            referer = REFERER_YOUTUBE_MUSIC
        )

        val TVHTML5_SIMPLY = YouTubeClient(
            clientName = "TVHTML5_SIMPLY",
            clientVersion = "1.0",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = USER_AGENT_WEB,
            clientId = 75,
            clientScreen = "WATCH"
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260315.18.00",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = USER_AGENT_TVHTML5,
            clientId = 7,
            clientScreen = "WATCH"
        )

        val VISION_OS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            userAgent = USER_AGENT_WEB,
            clientId = 101,
            clientScreen = "WATCH"
        )

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.62.27",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = USER_AGENT_ANDROID_VR,
            clientId = 28,
            clientScreen = "WATCH"
        )
    }
}
