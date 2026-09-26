package com.toastedvr.toastedvr.backend.dto;

// Movimiento de una fila respecto a la última vez que el jugador vio el
// ranking: UP/DOWN con los puestos, NEW si no estaba y SAME si sigue igual.
public record RankingMovementResponse(Direction direction, int places) {

    public enum Direction { UP, DOWN, NEW, SAME }

    public static RankingMovementResponse between(Integer previousPosition, int currentPosition) {
        if (previousPosition == null) {
            return new RankingMovementResponse(Direction.NEW, 0);
        }
        int difference = previousPosition - currentPosition;
        if (difference > 0) {
            return new RankingMovementResponse(Direction.UP, difference);
        }
        if (difference < 0) {
            return new RankingMovementResponse(Direction.DOWN, -difference);
        }
        return new RankingMovementResponse(Direction.SAME, 0);
    }
}
