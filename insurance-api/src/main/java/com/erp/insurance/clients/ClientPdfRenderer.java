package com.erp.insurance.clients;

import com.erp.insurance.clients.ClientDtos.ClientPlan;
import com.erp.insurance.clients.ClientDtos.ClientSummary;
import com.erp.insurance.clients.ClientService.ClientRecord;
import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.policies.PolicyEntity;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class ClientPdfRenderer {
    private static final Color BRAND = new Color(15, 118, 110);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BORDER = new Color(226, 232, 240);
    private static final Color SOFT = new Color(240, 253, 250);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"));
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.forLanguageTag("es"));

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
    private final Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(15, 23, 42));
    private final Font label = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private final Font value = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(30, 41, 59));
    private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private final Font tableHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);

    public byte[] render(ClientRecord record) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, output);
            document.addTitle("Ficha del cliente " + record.summary().crmId());
            document.addCreator("ERP Insurance");
            document.open();
            header(document, record.summary());
            personalData(document, record.patient(), record.summary());
            currentPlan(document, record.summary().currentPlan());
            policies(document, record);
            footer(document);
        } catch (DocumentException exception) {
            throw new IllegalStateException("No se pudo generar el PDF del cliente", exception);
        } finally {
            if (document.isOpen()) document.close();
        }
        return output.toByteArray();
    }

    private void header(Document document, ClientSummary summary) throws DocumentException {
        Paragraph brand = new Paragraph("NEXO ERP · Seguros médicos ACA", small);
        document.add(brand);
        Paragraph name = new Paragraph(summary.fullName(), title);
        name.setSpacingBefore(6);
        document.add(name);
        Paragraph crm = new Paragraph();
        crm.add(new Chunk("CRM ID  ", label));
        crm.add(new Chunk(summary.crmId(), FontFactory.getFont(FontFactory.COURIER_BOLD, 11, BRAND)));
        document.add(crm);
        Paragraph description = new Paragraph(summary.description(), value);
        description.setSpacingBefore(8);
        description.setSpacingAfter(14);
        document.add(description);
    }

    private void personalData(Document document, PatientEntity patient, ClientSummary summary) throws DocumentException {
        section(document, "Datos del cliente");
        PdfPTable table = grid(3);
        field(table, "Correo electrónico", summary.email());
        field(table, "Teléfono", summary.phone());
        field(table, "Ciudad", summary.city());
        field(table, "Fecha de nacimiento", formatDate(patient.getDateOfBirth()));
        field(table, "Edad", summary.age() == null ? null : summary.age() + " años");
        field(table, "Ingreso anual del hogar", patient.getHouseholdIncome() == null ? null
                : ClientService.money(new BigDecimal(patient.getHouseholdIncome())));
        field(table, "Elegibilidad", ClientService.eligibilityLabel(summary.acaEligibilityStatus()));
        field(table, "Estado de la cuenta", accountLabel(summary.accountStatus()));
        field(table, "Pólizas registradas", String.valueOf(summary.policies()));
        document.add(table);
    }

    private void currentPlan(Document document, ClientPlan plan) throws DocumentException {
        section(document, "Plan médico vigente");
        if (plan == null) {
            document.add(new Paragraph("El cliente no tiene un plan médico registrado.", value));
            return;
        }
        PdfPTable table = grid(3);
        field(table, "Aseguradora", plan.insurer());
        field(table, "Plan", plan.planName());
        field(table, "Número de póliza", plan.policyNumber());
        field(table, "Prima mensual", plan.zeroPremium() ? "$0.00 (subsidio ACA)" : ClientService.money(plan.premiumAmount()));
        field(table, "Vigencia", formatDate(plan.coverageStart()) + " a " + formatDate(plan.coverageEnd()));
        field(table, "Estado", policyLabel(plan.status()));
        document.add(table);
        if (plan.zeroPremium()) {
            Paragraph note = new Paragraph("Plan con prima cero: el crédito fiscal anticipado de la ACA cubre la totalidad de la prima.", small);
            note.setSpacingBefore(4);
            document.add(note);
        }
    }

    private void policies(Document document, ClientRecord record) throws DocumentException {
        section(document, "Historial de pólizas");
        if (record.policies().isEmpty()) {
            document.add(new Paragraph("Sin pólizas registradas.", value));
            return;
        }
        PdfPTable table = new PdfPTable(new float[] {2.2f, 2.2f, 2.4f, 2.6f, 1.4f, 1.4f});
        table.setWidthPercentage(100);
        for (String column : new String[] {"Póliza", "Aseguradora", "Plan", "Vigencia", "Prima", "Estado"}) {
            PdfPCell cell = new PdfPCell(new Phrase(column, tableHead));
            cell.setBackgroundColor(BRAND);
            cell.setBorderColor(BRAND);
            cell.setPadding(5);
            table.addCell(cell);
        }
        boolean shaded = false;
        for (PolicyEntity policy : record.policies()) {
            BigDecimal premium = policy.getPremiumAmount();
            String[] values = {
                    policy.getPolicyNumber(), policy.getInsurer(), policy.getPlanName(),
                    formatDate(policy.getCoverageStart()) + " a " + formatDate(policy.getCoverageEnd()),
                    premium != null && premium.signum() == 0 ? "$0 ACA" : ClientService.money(premium),
                    policyLabel(policy.getStatus())};
            for (String text : values) {
                PdfPCell cell = new PdfPCell(new Phrase(orDash(text), value));
                cell.setBorderColor(BORDER);
                cell.setPadding(5);
                if (shaded) cell.setBackgroundColor(SOFT);
                table.addCell(cell);
            }
            shaded = !shaded;
        }
        document.add(table);
    }

    private void footer(Document document) throws DocumentException {
        Paragraph footer = new Paragraph("Generado el " + ZonedDateTime.now(ZoneId.of("UTC")).format(STAMP)
                + ". Documento confidencial: contiene información médica protegida (PHI).", small);
        footer.setSpacingBefore(18);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void section(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, heading);
        paragraph.setSpacingBefore(10);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private PdfPTable grid(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private void field(PdfPTable table, String name, String content) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setPaddingBottom(7);
        cell.addElement(new Paragraph(name, label));
        cell.addElement(new Paragraph(orDash(content), value));
        table.addCell(cell);
    }

    private static String formatDate(String iso) {
        if (iso == null) return null;
        try {
            return formatDate(LocalDate.parse(iso));
        } catch (RuntimeException exception) {
            return iso;
        }
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE);
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String accountLabel(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> "Activo";
            case PENDING -> "Pendiente";
            case INACTIVE -> "Inactivo";
        };
    }

    private static String policyLabel(PolicyStatus status) {
        return switch (status) {
            case PENDING -> "Pendiente";
            case ACTIVE -> "Vigente";
            case TERMINATED -> "Terminada";
        };
    }
}
