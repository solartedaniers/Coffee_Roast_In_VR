package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.dto.RankingChangesResponse;
import com.toastedvr.toastedvr.backend.dto.RankingResponse;
import com.toastedvr.toastedvr.backend.dto.RankingSeenRequest;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.RankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roasting")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    // RF017: top del nivel pedido (por defecto, el del jugador), su posición,
    // el movimiento de cada fila y la firma del ranking mostrado.
    @GetMapping("/ranking")
    public RankingResponse getRanking(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) KnowledgeLevel level
    ) {
        return rankingService.getRanking(principal.getId(), level);
    }

    // Marca como visto exactamente el ranking que se mostró con esa firma.
    @PutMapping("/ranking/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRankingSeen(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody RankingSeenRequest request
    ) {
        rankingService.markSeen(principal.getId(), request.signature());
    }

    // Consulta liviana para el punto de aviso de la corona.
    @GetMapping("/ranking/changes")
    public RankingChangesResponse getRankingChanges(@AuthenticationPrincipal UserPrincipal principal) {
        return rankingService.getChanges(principal.getId());
    }
}
