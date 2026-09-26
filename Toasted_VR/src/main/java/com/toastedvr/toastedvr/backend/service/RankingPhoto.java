package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.dto.RankingMovementResponse;
import java.util.Map;
import java.util.Objects;

// "Foto" de un ranking tal como la ve un jugador: la posición de cada usuario
// del top (por id) y la posición propia (null si no está en el ranking).
public record RankingPhoto(Map<Long, Integer> topPositions, Integer ownPosition) {

    public RankingPhoto {
        topPositions = Map.copyOf(topPositions);
    }

    /** Hay cambio si alguien subió, bajó, entró o salió del top, o cambió la posición propia. */
    public boolean differsFrom(RankingPhoto other) {
        return !topPositions.equals(other.topPositions) || !Objects.equals(ownPosition, other.ownPosition);
    }

    /** Movimiento de un usuario del top respecto a esta foto (la vista antes). */
    public RankingMovementResponse movementOf(Long userId, int currentPosition) {
        return RankingMovementResponse.between(topPositions.get(userId), currentPosition);
    }

    /** Movimiento de la posición propia, aunque esté fuera del top. */
    public RankingMovementResponse ownMovement(int currentPosition) {
        return RankingMovementResponse.between(ownPosition, currentPosition);
    }
}
