package cl.lastmile.auth;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #44: con PUBLIC_REGISTRATION_ENABLED=true el registro público vuelve a existir,
 * pero sigue creando solo CLIENT aunque el body pida otro rol (HU-05).
 * El caso por defecto (404) está en UserManagementIT.
 */
@SpringBootTest(properties = "auth.public-registration-enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicRegistrationIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void habilitadoCreaSoloClientAunqueElBodyPidaAdmin() throws Exception {
        String email = "intruso-" + UUID.randomUUID().toString().substring(0, 8) + "@lastmile.cl";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Intruso","email":"%s","password":"clave12345","role":"ADMIN"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CLIENT"));

        assertThat(users.findByEmail(email)).get().extracting(User::getRole).isEqualTo(User.Role.CLIENT);
    }
}
