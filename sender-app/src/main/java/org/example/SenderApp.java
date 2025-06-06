package org.example;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.example.client.ReceiverClient;
import org.example.dto.MeasurementDto;
import org.jboss.logging.Logger;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.*;

@Startup
@ApplicationScoped
public class SenderApp {

    private static final Logger LOG = Logger.getLogger(SenderApp.class);

    @Inject
    @RestClient
    ReceiverClient receiverClient;

    // Pool de hilos para el procesamiento de tramas en paralelo de manera asíncrona con futuro completable.
    private final ExecutorService executor = new ThreadPoolExecutor(
            10, // núm. mínimo de hilos
            50, // núm. máximo de hilos
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), // cola de tareas en espera
            new ThreadPoolExecutor.AbortPolicy()
    );
    {
        ((ThreadPoolExecutor) executor).setRejectedExecutionHandler((runnable, exec) -> {
            LOG.warn("Tarea rechazada: el sistema está saturado (pool + cola llenos). Trama descartada.");
        });
    }

    @PostConstruct
    void init() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(5050)) {
                LOG.info("Servidor TCP escuchando en puerto 5050...");
                while (true) {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                }
            } catch (IOException e) {
                LOG.error("Error en el servidor TCP", e);
            }
        },"tcp-server-listener").start();
    }

    void handleConnection(Socket socket) {
        executor.submit(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    MeasurementDto dto = parseMeasurement(line);
                    // LOG DE TAMAÑO DEL POOL
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                    LOG.infof("Pool info → Activas: %d | En cola: %d",
                            pool.getActiveCount(), pool.getQueue().size());
                    // Procesar en segundo plano SIN bloquear este hilo
                    CompletableFuture
                            .supplyAsync(() -> receiverClient.sendMeasurement(dto), executor)
                            .orTimeout(3, TimeUnit.SECONDS)
                            .exceptionally(ex -> {
                                LOG.warn("Error procesando REST: " + ex.getMessage());
                                return null;
                            })
                            .thenAcceptAsync(response -> {
                                if (response != null) {
                                    try {
                                        synchronized (writer) {
                                            writer.write(response);
                                            writer.newLine();
                                            writer.flush();
                                        }
                                    } catch (IOException e) {
                                        LOG.error("Error escribiendo en el socket", e);
                                    }
                                }
                            }, executor); // Usa el mismo pool para la escritura
                }

                LOG.info("Cliente cerró conexión");//No está llegando acá
                writer.close();
                reader.close();
                socket.close();

            } catch (IOException e) {
                LOG.error("Error en la conexión TCP", e);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        });
    }

    MeasurementDto parseMeasurement(String input) {
        String[] parts = input.split(";");
        MeasurementDto dto = new MeasurementDto();
        dto.type = parts[0];
        dto.value = Double.parseDouble(parts[1]);
        dto.unit = parts[2];
        dto.timestamp = parts[3];
        return dto;
    }

    // Cierre ordenado del pool de hilos
    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
