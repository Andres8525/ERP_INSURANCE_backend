package com.erp.insurance.clients;

import com.erp.insurance.clients.ClientDtos.ClientSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ClientPdfRenderer pdfRenderer;

    public ClientController(ClientService clients, ClientPdfRenderer pdfRenderer) {
        this.clients = clients;
        this.pdfRenderer = pdfRenderer;
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

    @Operation(summary = "Descargar ficha en PDF", description = "Datos del cliente, plan médico vigente e historial de pólizas.")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        ClientService.ClientRecord record = clients.get(id);
        byte[] body = pdfRenderer.render(record);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("cliente-" + record.summary().crmId() + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
