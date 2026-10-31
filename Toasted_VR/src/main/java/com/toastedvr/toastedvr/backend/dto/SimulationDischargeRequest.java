package com.toastedvr.toastedvr.backend.dto;

import java.util.List;

// Las alertas proactivas que sonaron en Unity, con el segundo del tueste. Viajan en la traza.
public record SimulationDischargeRequest(List<RoastTraceRequest.Alert> alerts) {
}
