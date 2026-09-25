package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.dto.RankingResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.RankingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roasting")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    // RF017: top del nivel pedido (por defecto, el del jugador) y su posición.
    @GetMapping("/ranking")
    public RankingResponse getRanking(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) KnowledgeLevel level
    ) {
        return rankingService.getRanking(principal.getId(), level);
    }
}
