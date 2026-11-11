package com.stockflow.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("GlobalExceptionHandler — manejo de excepciones HTTP")
class GlobalExceptionHandlerTest {

    /** Controlador mínimo para ejercitar los handlers. Solo admite POST. */
    @RestController
    @RequestMapping("/test")
    static class TestController {
        @PostMapping
        public String postOnly() { return "ok"; }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("HttpRequestMethodNotSupportedException → HTTP 405")
    class MetodoNoPermitido {

        @Test
        @DisplayName("GET en ruta que solo admite POST devuelve 405 sin detalles internos")
        void get_enRutaSoloPost_retorna405() throws Exception {
            mockMvc.perform(get("/test").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                    .andExpect(jsonPath("$.mensaje").exists())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.path").exists());
        }

        @Test
        @DisplayName("DELETE en ruta que solo admite POST devuelve 405")
        void delete_enRutaSoloPost_retorna405() throws Exception {
            mockMvc.perform(delete("/test"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.status").value(405));
        }

        @Test
        @DisplayName("POST válido en la misma ruta no devuelve 405")
        void post_valido_noRetorna405() throws Exception {
            mockMvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }
}
