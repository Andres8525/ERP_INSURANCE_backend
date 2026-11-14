package com.erp.insurance.catalogs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class CatalogApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode read(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void catalogsArePublicAndSearchable() throws Exception {
        JsonNode diabetes = read("/api/catalogs/icd10?q=diabetes");
        assertThat(diabetes).isNotEmpty();
        assertThat(diabetes).allSatisfy(entry -> assertThat(entry.get("description").asText().toLowerCase()).contains("diabetes"));

        JsonNode byCode = read("/api/catalogs/icd10?q=e11");
        assertThat(byCode).allSatisfy(entry -> assertThat(entry.get("code").asText()).startsWith("E11"));
        assertThat(byCode.get(0).get("chapterName").asText()).isEqualTo("Enfermedades endocrinas, nutricionales y metabólicas");

        JsonNode respiratory = read("/api/catalogs/icd10?chapter=X");
        assertThat(respiratory).allSatisfy(entry -> assertThat(entry.get("chapter").asText()).isEqualTo("X"));

        JsonNode accents = read("/api/catalogs/medications?q=acetaminofen");
        assertThat(accents.get(0).get("name").asText()).isEqualTo("Acetaminofén");

        JsonNode controlled = read("/api/catalogs/medications?controlled=true");
        assertThat(controlled).isNotEmpty().allSatisfy(entry -> assertThat(entry.get("controlled").asBoolean()).isTrue());

        JsonNode info = read("/api/catalogs");
        assertThat(info.get("version").asText()).hasSize(12);
        assertThat(info.get("icd10Codes").asInt()).isGreaterThan(50);
    }

    @Test
    void catalogResponsesAreCacheableAndRevalidatedWithEtag() throws Exception {
        MockHttpServletResponse first = mvc.perform(get("/api/catalogs/icd10")).andExpect(status().isOk()).andReturn().getResponse();
        String etag = first.getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        assertThat(first.getHeader(HttpHeaders.CACHE_CONTROL)).contains("public").contains("max-age=86400");

        mvc.perform(get("/api/catalogs/icd10").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }
}
