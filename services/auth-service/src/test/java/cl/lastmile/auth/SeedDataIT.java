package cl.lastmile.auth;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-01: la semilla de infra/seed/auth crea un usuario por rol y es idempotente. */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/auth"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeedDataIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired Flyway flyway;

    @Test
    void creaUnUsuarioPorRol() {
        var roles = users.findAll().stream().map(User::getRole).collect(Collectors.toSet());
        assertThat(roles).containsExactlyInAnyOrder(User.Role.values());
        assertThat(users.count()).isEqualTo(User.Role.values().length);
    }

    @Test
    void losUsuariosSemillaPuedenIniciarSesion() throws Exception {
        for (String email : new String[]{"admin", "despacho", "conductor", "operador", "cliente"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"%s@lastmile.cl","password":"lastmile123"}""".formatted(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"despacho@lastmile.cl","password":"lastmile123"}"""))
                .andExpect(jsonPath("$.user.role").value("DISPATCHER"));
    }

    @Test
    void esIdempotente() {
        long before = users.count();
        flyway.migrate();
        assertThat(users.count()).isEqualTo(before);
        assertThat(Arrays.stream(User.Role.values())).allMatch(users::existsByRole);
    }
}
