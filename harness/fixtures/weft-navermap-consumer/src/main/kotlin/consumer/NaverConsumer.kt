package consumer

import run.thespot.weft.map.navermap.NaverMap

/** Compile-only consumer proving the Naver provider is exported through the Weft BOM. */
public object NaverConsumer {
    public fun provider() = NaverMap

    public fun configuredProvider() = NaverMap.prepare {
        ncpKeyId = "REPLACE_WITH_NCP_KEY_ID"
    }
}
