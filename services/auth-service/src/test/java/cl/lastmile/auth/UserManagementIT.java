package cl.lastmile.auth;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-05 de punta a punta contra PostgreSQL real: el ADMIN inicial se crea desde
 * propiedades (equivalentes a BOOTSTRAP_ADMIN_*), y los tokens se obtienen con
 * el login real, igual que en producción.
 */
@SpringBootTest(properties = {
        "bootstrap.admin.email=root@lastmile.cl",
        "bootstrap.admin.password=clave-admin-123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserManagementIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@lastmile.cl";
    }

    /** Un CLIENT ya no se registra solo (#44): lo crea el ADMIN y luego inicia sesión. */
    private String clientToken(String email) throws Exception {
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + loginAsBootstrapAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(createUserBody(email, "CLIENT")))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"clave12345"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String loginAsBootstrapAdmin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"root@lastmile.cl","password":"clave-admin-123"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.at("/user/role").asText()).isEqualTo("ADMIN");
        return node.get("accessToken").asText();
    }

    private static String createUserBody(String email, String role) {
        return """
                {"fullName":"Pedro Conductor","email":"%s","password":"clave12345","role":"%s"}""".formatted(email, role);
    }

    @Test
    void elRegistroPublicoEstaDesactivadoPorDefecto() throws Exception {
        String email = uniqueEmail("intruso");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Intruso","email":"%s","password":"clave12345","role":"ADMIN"}""".formatted(email)))
                .andExpect(status().isNotFound());

        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    void crearUsuarioSinTokenDevuelve401() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(uniqueEmail("x"), "DRIVER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearUsuarioConTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", "Bearer no-es-un-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(uniqueEmail("x"), "DRIVER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearUsuarioConTokenNoAdminDevuelve403() throws Exception {
        String clientToken = clientToken(uniqueEmail("cliente"));
        String email = uniqueEmail("escalada");

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(email, "ADMIN")))
                .andExpect(status().isForbidden());

        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    void adminCreaUsuarioConRolYDevuelve201() throws Exception {
        String adminToken = loginAsBootstrapAdmin();
        String email = uniqueEmail("conductor");

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(email, "DRIVER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(users.findByEmail(email)).get().extracting(User::getRole).isEqualTo(User.Role.DRIVER);
    }

    @Test
    void adminConRolInvalidoDevuelve400() throws Exception {
        String adminToken = loginAsBootstrapAdmin();

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(uniqueEmail("x"), "SUPERUSER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elBootstrapCreaUnSoloAdmin() {
        assertThat(users.findAll().stream().filter(u -> u.getRole() == User.Role.ADMIN)).hasSize(1);
    }

    @Test
    void elEndpointEstaDocumentadoEnOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users'].post").exists());
    }
}
