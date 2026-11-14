package com.erp.insurance.clients;

import com.erp.insurance.clients.ClientDtos.ClientSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Clientes CRM", description = "Listado de clientes con CRM ID, plan médico vigente y ficha en PDF")
@RequestMapping("/api/clients")
public class ClientController {
    private final ClientService clients;

    public ClientController(ClientService clients) {
        this.clients = clients;
    }

    @Operation(summary = "Listar clientes", description = "Clientes ordenados por nombre; q busca en CRM ID, nombre, correo, ciudad, aseguradora y plan.")
    @GetMapping
    public List<ClientSummary> list(@RequestParam(required = false) String q) {
        return clients.list(q);
    }

    @Operation(summary = "Resumen de un cliente")
    @GetMapping("/{id}")
    public ClientSummary get(@PathVariable UUID id) {
        return clients.get(id).summary();
    }
}
