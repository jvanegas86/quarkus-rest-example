package org.example.client;

import org.example.dto.MeasurementDto;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/measurement")
@RegisterRestClient(configKey = "receiver-api")
public interface ReceiverClient {

    @POST
    @Consumes("application/json")
    @Produces("text/plain")
    String sendMeasurement(MeasurementDto dto);
}
