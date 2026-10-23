package com.stock;

import com.stock.service.ExcelExporter;
import com.stock.service.Importer;
import com.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;DB_CLOSE_DELAY=-1",
        "logging.file.name=",
        "stock.login.enabled=true"})
@AutoConfigureMockMvc
class AuthAndImportTest {

    @Autowired MockMvc mvc;
    @Autowired StockService service;
    @Autowired ExcelExporter exporter;
    @Autowired Importer importer;

    private MockHttpSession login(String user, String pass) throws Exception {
        var s = new MockHttpSession();
        mvc.perform(post("/api/login").session(s).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")).andExpect(status().isOk());
        return s;
    }

    @Test
    void adminSetsOwnPassword() throws Exception {
        var admin = login("admin", "admin");
        mvc.perform(put("/api/me/password").session(admin).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"admin\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/me/password").session(admin).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"s3cret\"}")).andExpect(jsonPath("$.defaultPassword").value(false));
        var again = login("admin", "s3cret");
        // повертаємо admin / admin для інших тестів
        String body = mvc.perform(get("/api/users").session(again)).andReturn().getResponse().getContentAsString();
        String id = body.replaceAll("(?s).*\\{\"id\":(\\d+),\"username\":\"admin\".*", "$1");
        mvc.perform(put("/api/users/" + id).session(again).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"admin\"}")).andExpect(status().isOk());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ping")).andExpect(status().isOk());

        var admin = login("admin", "admin");
        mvc.perform(get("/api/me").session(admin)).andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.defaultPassword").value(true));
        mvc.perform(post("/api/users").session(admin).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ivan\",\"password\":\"1234\",\"role\":\"USER\"}")).andExpect(status().isCreated());

        var user = login("ivan", "1234");
        mvc.perform(post("/api/categories").session(user).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/parts/1/adjust").session(user).header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/parts/1/history").session(user)).andExpect(jsonPath("$[0].username").value("ivan"));

        // Без заголовка X-Stock зміни не приймаються
        mvc.perform(post("/api/parts/1/adjust").session(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":1}")).andExpect(status().isForbidden());

        mvc.perform(post("/api/login").header("X-Stock", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ivan\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void exportThenImportRoundTrips() throws Exception {
        Long cat = service.createCategory("Round").id();
        var p = service.createPart(new com.stock.web.Dto.CreatePart(cat, "Bolt", 5, 2), "t");
        var out = new ByteArrayOutputStream();
        exporter.write(null, out);

        service.adjust(p.id(), 10, null, "t");                       // стало 15
        var res = importer.importXlsx(new ByteArrayInputStream(out.toByteArray()), "admin");
        assertEquals(0, res.categoriesCreated());
        assertEquals(0, res.partsCreated());
        assertEquals(5, service.listParts(cat).get(0).quantity()); // повернулось до 5 з файлу
        assertFalse(service.listParts(cat).stream().anyMatch(x -> x.name().equals("Total")));
    }
}
