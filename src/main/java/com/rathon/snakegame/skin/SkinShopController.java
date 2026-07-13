package com.rathon.snakegame.skin;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 스킨 상점 REST — 카탈로그는 공개(게스트 렌더링용), 구매·장착은 인증 필수.
 */
@RestController
@RequestMapping("/api/skins")
@RequiredArgsConstructor
public class SkinShopController {

    private final SkinShopService skinShopService;

    /** 카탈로그 항목 DTO */
    public record SkinDto(String id, String displayName, String type, long price,
                          String primaryColor, String secondaryColor) {

        static SkinDto from(SkinCatalog skin) {
            return new SkinDto(skin.getId(), skin.getDisplayName(), skin.getType().name(),
                    skin.getPrice(), skin.getPrimaryColor(), skin.getSecondaryColor());
        }
    }

    /** 카탈로그 조회 — 게스트도 타 플레이어 스킨을 그려야 하므로 공개 */
    @GetMapping
    public List<SkinDto> catalog() {
        return Arrays.stream(SkinCatalog.values()).map(SkinDto::from).toList();
    }

    /** 스킨 구매 */
    @PostMapping("/{skinId}/purchase")
    public void purchase(@PathVariable String skinId, Principal principal) {
        skinShopService.purchase(principal.getName(), skinId);
    }

    /** 스킨 장착 */
    @PostMapping("/{skinId}/equip")
    public void equip(@PathVariable String skinId, Principal principal) {
        skinShopService.equip(principal.getName(), skinId);
    }
}
