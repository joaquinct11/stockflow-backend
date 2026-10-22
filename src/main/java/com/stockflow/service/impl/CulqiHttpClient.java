package com.stockflow.service.impl;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Abstracción mínima para el HTTP de Culqi.
 * Sustituye java.net.http.HttpClient para evitar que WEPollSelectorImpl
 * (JDK 21, Windows) cree un Pipe con Unix Domain Sockets al arrancar Spring.
 */
@FunctionalInterface
public interface CulqiHttpClient {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
