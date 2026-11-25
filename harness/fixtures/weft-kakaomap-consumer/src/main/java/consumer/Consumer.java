package consumer;

import run.thespot.weft.map.kakaomap.KakaoMap;

public final class Consumer {
    public static Object provider() {
        return KakaoMap.INSTANCE;
    }
}
