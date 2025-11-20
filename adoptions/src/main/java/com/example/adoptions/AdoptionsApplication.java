package com.example.adoptions;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


@EnableMultiFactorAuthentication( authorities =  {
        FactorGrantedAuthority.OTT_AUTHORITY ,
        FactorGrantedAuthority.PASSWORD_AUTHORITY
})
@EnableResilientMethods
@Import(MyBeanRegistrar.class)
@ImportHttpServices(CatFactsClient.class)
@SpringBootApplication
public class AdoptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }

    @Bean
    JdbcUserDetailsManager jdbcUserDetailsManager(DataSource dataSource) {
        var u = new JdbcUserDetailsManager(dataSource);
        u.setEnableUpdatePassword(true);
        return u;
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return http ->

                http
                        .webAuthn( w -> w
                                .allowedOrigins("http://localhost:8080")
                                .rpName("bootiful")
                                .rpId("localhost")
                        )
                        .oneTimeTokenLogin(ott -> ott.tokenGenerationSuccessHandler(
                        (request, response, oneTimeToken) -> {

                            response.getWriter().println("you've got console mail!");
                            response.setContentType(MediaType.TEXT_PLAIN_VALUE);

                            IO.println("please go to http://localhost:8080/login/ott?token=" +
                                    oneTimeToken.getTokenValue());


                        }
                ))
                ;
    }
}


class MyRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IO.println("hi!");
    }
}

class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
        for (var i = 0; i < 10; i++)
            registry.registerBean(MyRunner.class);
    }
}

@Controller
@ResponseBody
class MeController {

    @GetMapping("/")
    Map<String, String> me(Principal principal) {
        return Map.of("name", principal.getName());
    }
}


// https://www.catfacts.net/api

record CatFact(String fact) {
}

record CatFacts(Collection<CatFact> facts) {
}

interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}

@Controller
@ResponseBody
class CatFactsController {

    private final CatFactsClient client;

    CatFactsController(CatFactsClient client) {
        this.client = client;
    }

    private final AtomicInteger counter = new AtomicInteger(0);

    @ConcurrencyLimit(10)
    @Retryable(maxAttempts = 4, includes = IllegalStateException.class)
    @GetMapping("/cats/facts")
    Collection<CatFact> facts() {

        if (this.counter.incrementAndGet() < 4) {
            IO.println("oops!");
            throw new IllegalStateException("oops!");
        }
        IO.println("cat facts!");
        return this.client.facts().facts();
    }

}


@Controller
@ResponseBody
class AdoptionsController {

    private final AdoptionsService adoptionsService;

    AdoptionsController(AdoptionsService adoptionsService) {
        this.adoptionsService = adoptionsService;
    }

    @PostMapping("/dogs/{dogId}/adoptions")
    void adopt(@PathVariable int dogId, @RequestParam String owner) {
        this.adoptionsService.adopt(dogId, owner);
    }
}

@Service
class AdoptionsService {

    private final DogRepository repository;

    AdoptionsService(DogRepository repository) {
        this.repository = repository;
    }

    void adopt(int id, String owner) {
        this.repository.findById(id).ifPresent(dog -> {
            var updated = this.repository.save(new Dog(dog.id(), dog.name(), dog.description(), owner));
            IO.println("adopted " + updated);
        });
    }
}

// tony hoare
// null away

interface DogRepository extends ListCrudRepository<@NonNull Dog, @NonNull Integer> {
}

// look mom, no Lombok!
record Dog(@Id int id, String name, String description, String owner) {
}