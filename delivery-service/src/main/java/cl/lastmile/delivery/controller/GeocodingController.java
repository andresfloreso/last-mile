package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.geo.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Endpoint delgado sobre GeocodingService: el frontend lo usa para el buscador
 * de direcciones ("Pulsa para añadir más" de la app de referencia), mostrando
 * al usuario la coordenada exacta antes de confirmar la parada.
 */
@RestController
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/search")
    public List<GeocodeResult> search(@RequestParam String q, @RequestParam(required = false) String commune) {
        Optional<GeocodingService.GeoPoint> result = geocodingService.geocode(q, commune);
        return result
                .map(p -> List.of(new GeocodeResult(q, p.latitude(), p.longitude(), p.confidence())))
                .orElseGet(List::of);
    }

    public record GeocodeResult(String address, double latitude, double longitude, double confidence) {}
}
