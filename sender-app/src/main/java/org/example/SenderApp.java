package org.example;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.example.client.ReceiverClient;
import org.example.dto.MeasurementDto;
import org.jboss.logging.Logger;
import io.quarkus.runtime.Startup;

import java.time.Instant;

@Startup
@ApplicationScoped
public class SenderApp {

    private static final Logger LOG = Logger.getLogger(SenderApp.class);

    @RestClient
    ReceiverClient receiverClient;

    @PostConstruct
    public void init() {
        MeasurementDto dto = new MeasurementDto();
        dto.type = "Temperatura";
        dto.value = 25.4;
        dto.unit = "°C";
        dto.timestamp = Instant.now().toString();

        LOG.info("📤 Enviando DTO...");
        try {
            String response = receiverClient.sendMeasurement(dto);
            LOG.infof("📥 Respuesta recibida: %s", response);
        } catch (Exception e) {
            LOG.error("❌ Error enviando DTO", e);
        }
    }
}
