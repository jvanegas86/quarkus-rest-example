package org.example;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.example.dto.MeasurementDto;
import org.jboss.logging.Logger;

@Path("/measurement")
public class MeasurementResource {

    private static final Logger LOG = Logger.getLogger(MeasurementResource.class);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String receive(MeasurementDto dto) {
        LOG.infof("🔹 Recibido: %s=%.2f %s [%s]", dto.type, dto.value, dto.unit, dto.timestamp);
        return "ACK - RECEIVED " + dto.type;
    }
}
