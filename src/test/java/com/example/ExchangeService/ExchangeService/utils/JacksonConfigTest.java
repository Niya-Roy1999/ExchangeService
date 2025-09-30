package com.example.ExchangeService.ExchangeService.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("customObjectMapper")
    private ObjectMapper customObjectMapper;

    static class Dummy {
        private String name;
        public String getName() {return name;}
        public void setName(String name) {this.name = name;}
    }

    @Test
    void shouldLoadBothBeans() {
        assertNotNull(objectMapper);
        assertNotNull(customObjectMapper);
    }

    @Test
    void primaryMapperShouldHandleJavaTime() throws JsonProcessingException {
        Instant now = Instant.now();
        String json = objectMapper.writeValueAsString(now);
        Instant result = objectMapper.readValue(json, Instant.class);
        assertEquals(
                now.truncatedTo(ChronoUnit.MILLIS),  result.truncatedTo(ChronoUnit.MILLIS)
        );
    }

    @Test
    void customMapperShouldFailForJavaTime() {
        Instant now = Instant.now();
        assertThrows(JsonProcessingException.class,
                () -> customObjectMapper.writeValueAsString(now));
    }

    @Test
    void shouldSerializeInstantAsIsoString() throws Exception {
        Instant now = Instant.parse("2025-09-30T10:15:30Z");
        String json = objectMapper.writeValueAsString(now);
        assertTrue(json.contains("2025-09-30T10:15:30Z")); // ISO string
        assertFalse(json.matches("\\d+"));                 // not numeric timestamp
    }

    @Test
    void shouldIgnoreUnknownProperties() throws Exception {
        String json = "{\"name\":\"test\",\"extra\":\"ignored\"}";
        Dummy dummy = objectMapper.readValue(json, Dummy.class);
        assertEquals("test", dummy.getName());
    }

    @Test
    void primaryAndCustomMapperShouldBehaveDifferently() {
        assertNotEquals(
                objectMapper.getRegisteredModuleIds(),
                customObjectMapper.getRegisteredModuleIds()
        );
    }
}
