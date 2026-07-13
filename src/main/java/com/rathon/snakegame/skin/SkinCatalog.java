package com.rathon.snakegame.skin;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 스킨 카탈로그 — 서버 코드로 정의하는 정적 목록.
 * 색상 값은 클라이언트 렌더링 힌트이며, 판정에는 관여하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum SkinCatalog {

    // 기본 — 무료, 게스트와 동일한 색
    DEFAULT("default", "기본", SkinType.SOLID, 0, "#c4913e", "#c4913e"),

    // 유료 단색 팔레트
    CRIMSON("crimson", "진홍", SkinType.SOLID, 50, "#e0455a", "#e0455a"),
    EMERALD("emerald", "에메랄드", SkinType.SOLID, 50, "#2ecc71", "#2ecc71"),
    VIOLET("violet", "바이올렛", SkinType.SOLID, 80, "#9b59b6", "#9b59b6"),
    GOLD("gold", "황금", SkinType.SOLID, 120, "#f1c40f", "#f1c40f"),

    // 패턴 스킨
    BEE_STRIPE("bee-stripe", "꿀벌 줄무늬", SkinType.STRIPE, 200, "#f1c40f", "#2c3e50"),
    CANDY_STRIPE("candy-stripe", "사탕 줄무늬", SkinType.STRIPE, 200, "#ff6b81", "#f8f9fa"),
    OCEAN_GRADIENT("ocean-gradient", "바다 그라데이션", SkinType.GRADIENT, 300, "#00c6ff", "#0072ff"),
    SUNSET_GRADIENT("sunset-gradient", "노을 그라데이션", SkinType.GRADIENT, 300, "#ff9966", "#ff5e62");

    /** 클라이언트·DB에서 쓰는 스킨 식별자 */
    private final String id;
    /** 표시 이름 */
    private final String displayName;
    /** 렌더링 타입 */
    private final SkinType type;
    /** 가격 (크레딧) */
    private final long price;
    /** 주 색상 */
    private final String primaryColor;
    /** 보조 색상 — 줄무늬·그라데이션에 사용 */
    private final String secondaryColor;

    /** 무료 스킨 여부 */
    public boolean isFree() {
        return price == 0;
    }

    /** id로 카탈로그 조회 */
    public static Optional<SkinCatalog> findById(String id) {
        return Arrays.stream(values())
                .filter(skin -> skin.id.equals(id))
                .findFirst();
    }

    /** 스킨 렌더링 타입 */
    public enum SkinType {
        SOLID, STRIPE, GRADIENT
    }
}
