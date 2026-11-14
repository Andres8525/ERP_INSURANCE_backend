package com.erp.insurance.demo;

import com.erp.insurance.audit.DataAnomalyEntity;
import com.erp.insurance.audit.DataAnomalyRepository;
import com.erp.insurance.auth.UserEntity;
import com.erp.insurance.auth.UserRepository;
import com.erp.insurance.claims.ClaimEntity;
import com.erp.insurance.claims.ClaimRepository;
import com.erp.insurance.domain.DomainEnums.AcaEligibilityStatus;
import com.erp.insurance.domain.DomainEnums.AccountStatus;
import com.erp.insurance.domain.DomainEnums.AffectedRecordType;
import com.erp.insurance.domain.DomainEnums.AnomalyResolutionStatus;
import com.erp.insurance.domain.DomainEnums.AnomalySeverity;
import com.erp.insurance.domain.DomainEnums.AnomalyType;
import com.erp.insurance.domain.DomainEnums.BatchResolutionAction;
import com.erp.insurance.domain.DomainEnums.ClaimPriority;
import com.erp.insurance.domain.DomainEnums.ClaimStatus;
import com.erp.insurance.domain.DomainEnums.PolicyStatus;
import com.erp.insurance.domain.DomainEnums.UserRole;
import com.erp.insurance.patients.PatientEntity;
import com.erp.insurance.patients.PatientRepository;
import com.erp.insurance.policies.PolicyEntity;
import com.erp.insurance.policies.PolicyRepository;
import com.erp.insurance.security.EmailLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "NexoDemo2026!";
    public static final String DEMO_ADMIN = "admin@nexo.demo";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final PatientRepository patients;
    private final PolicyRepository policies;
    private final ClaimRepository claims;
    private final DataAnomalyRepository anomalies;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository users, PatientRepository patients, PolicyRepository policies,
                          ClaimRepository claims, DataAnomalyRepository anomalies, PasswordEncoder encoder) {
        this.users = users;
        this.patients = patients;
        this.policies = policies;
        this.claims = claims;
        this.anomalies = anomalies;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        Instant now = Instant.now();

        String hash = encoder.encode(DEMO_PASSWORD);
        UserEntity admin = users.save(new UserEntity(DEMO_ADMIN, hash, UserRole.ADMIN));
        users.save(new UserEntity("auditor@nexo.demo", hash, UserRole.AUDITOR));
        users.save(new UserEntity("agente@nexo.demo", hash, UserRole.AGENT));

        PatientEntity maria = patient("María", "González", "maria.gonzalez@example.test", "(305) 555-0142", "1986-03-14", "41800.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity james = patient("James", "Rodriguez", "james.rodriguez@example.test", "(407) 555-0118", "1979-11-02", "52300.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity lucia = patient("Lucía", "Martínez", "lucia.martinez@example.test", "(813) 555-0190", "1992-07-21", "36500.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity luciaDup = patient("Lucia M.", "Martinez", "lmartinez92@example.test", "(813) 555-0190", "1992-07-21", null, AcaEligibilityStatus.PENDING, AccountStatus.PENDING);
        PatientEntity robert = patient("Robert", "Chen", "robert.chen@example.test", "(305) 555-0164", "1974-05-09", "38400.00", AcaEligibilityStatus.PENDING, AccountStatus.PENDING);
        PatientEntity ana = patient("Ana", "Pérez", "ana.perez@example.test", "305-555-01", "1995-01-30", "29900.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity david = patient("David", "Williams", "david.williams@exmaple.test", "(813) 555-0107", "1968-09-17", "61200.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity elena = patient("Elena", "Morales", "elena.morales@example.test", "(305) 555-0133", "1989-12-05", "44750.00", AcaEligibilityStatus.ELIGIBLE, AccountStatus.ACTIVE);
        PatientEntity patricia = patient("Patricia", "López", "patricia.lopez@example.test", "(407) 555-0181", "1961-06-11", "33100.00", AcaEligibilityStatus.INELIGIBLE, AccountStatus.INACTIVE);

        int year = LocalDate.now().getYear();
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        LocalDate dec31 = LocalDate.of(year, 12, 31);
        policy(maria, "AMB-2847-11903", "Ambetter", "Clear Silver 73", jan1, dec31, "428.16", PolicyStatus.ACTIVE, true);
        PolicyEntity mariaOverlap = policy(maria, "AMB-2847-12011", "Ambetter", "Clear Silver 73", LocalDate.of(year, 2, 1), dec31, "428.16", PolicyStatus.PENDING, true);
        PolicyEntity jamesPolicy = policy(james, "OSC-5521-00871", "Oscar Health", "Classic Bronze EPO", jan1, dec31, "482.16", PolicyStatus.ACTIVE, true);
        policy(lucia, "BCB-7712-30045", "Florida Blue", "BlueSelect Silver", jan1, dec31, "389.40", PolicyStatus.ACTIVE, false);
        policy(robert, "MOL-3301-44120", "Molina Healthcare", "Silver 150", LocalDate.of(year, 3, 1), dec31, "312.75", PolicyStatus.PENDING, false);
        policy(ana, "OSC-5521-01934", "Oscar Health", "Simple Silver EPO", jan1, dec31, "355.00", PolicyStatus.ACTIVE, false);
        policy(david, "AET-9910-22871", "Aetna CVS Health", "Gold Plus HMO", jan1, dec31, "512.90", PolicyStatus.ACTIVE, false);
        policy(elena, "AMB-2847-15560", "Ambetter", "Balanced Care 12", jan1, dec31, "401.30", PolicyStatus.ACTIVE, false);
        policy(patricia, "CIG-4420-77810", "Cigna", "Connect Bronze", LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31), "298.00", PolicyStatus.TERMINATED, false);

        claim(maria, "Consulta de especialidad cardiológica", "Miami", ClaimPriority.HIGH, null,
                List.of(ClaimStatus.OPEN), now.minus(Duration.ofHours(2)), "Transporte médico", "Recogida en domicilio 08:30");
        claim(james, "Estudios de laboratorio y panel metabólico", "Orlando", ClaimPriority.NORMAL, "185.00",
                List.of(ClaimStatus.OPEN), now.minus(Duration.ofHours(6)), null, null);
        claim(david, "Traslado de alta hospitalaria", "Tampa", ClaimPriority.HIGH, null,
                List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW), now.minus(Duration.ofHours(1)), "Coordinación de cuidados", "Enfermera asignada: Andrea Torres");
        claim(robert, "Terapia física post-operatoria (8 sesiones)", "Tampa", ClaimPriority.NORMAL, "860.00",
                List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW), now.minus(Duration.ofDays(1)), null, null);
        claim(elena, "Procedimiento ambulatorio", "Miami", ClaimPriority.NORMAL, "1240.00",
                List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW, ClaimStatus.APPROVED), now.minus(Duration.ofDays(1)), "Entrega de equipo médico", "Muletas y férula");
        claim(patricia, "Servicio fuera de red", "Orlando", ClaimPriority.NORMAL, "640.00",
                List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW, ClaimStatus.REJECTED), now.minus(Duration.ofDays(9)), null, null);

        anomalies.saveAll(List.of(
                new DataAnomalyEntity(AnomalyType.POLICY_OVERLAP, AnomalySeverity.RED, "Pólizas solapadas",
                        "Dos pólizas Ambetter Clear Silver 73 activas en el mismo periodo de cobertura.",
                        AffectedRecordType.POLICY, mariaOverlap.getId(), maria)
                        .withAnalysis("Marketplace", 94, "Inicio de cobertura", jan1.toString(), LocalDate.of(year, 2, 1).toString(),
                                "Confirmación de cobertura de la aseguradora")
                        .detectedAt(now.minus(Duration.ofMinutes(48))),
                new DataAnomalyEntity(AnomalyType.OTHER, AnomalySeverity.RED, "Discrepancia de facturación",
                        "La prima registrada no coincide con la factura vigente de la aseguradora.",
                        AffectedRecordType.POLICY, jamesPolicy.getId(), james)
                        .withAnalysis("Facturación", 91, "Prima mensual", "482.16", "428.16", "Factura vigente de Oscar Health")
                        .withSuggestion("premiumAmount", "428.16")
                        .detectedAt(now.minus(Duration.ofMinutes(72))),
                new DataAnomalyEntity(AnomalyType.DUPLICATE, AnomalySeverity.YELLOW, "Posible cliente duplicado",
                        "Coincidencia del 94% entre dos fichas de afiliado (fecha de nacimiento y teléfono).",
                        AffectedRecordType.PATIENT, luciaDup.getId(), lucia)
                        .withAnalysis("CRM", 94, "Identidad del miembro", "Lucía Martínez", "Lucia M. Martinez",
                                "Misma fecha de nacimiento y teléfono")
                        .detectedAt(now.minus(Duration.ofMinutes(110))),
                new DataAnomalyEntity(AnomalyType.OTHER, AnomalySeverity.YELLOW, "Elegibilidad por verificar",
                        "El ingreso declarado difiere del comprobante adjunto a la solicitud ACA.",
                        AffectedRecordType.PATIENT, robert.getId(), robert)
                        .withAnalysis("ACA", 82, "Ingreso anual", "38400.00", "39200.00", "Solicitud ACA y comprobante de ingresos")
                        .detectedAt(now.minus(Duration.ofHours(19))),
                new DataAnomalyEntity(AnomalyType.OTHER, AnomalySeverity.YELLOW, "Teléfono con formato inválido",
                        "El teléfono del afiliado no cumple el formato E.164 / NANP.",
                        AffectedRecordType.PATIENT, ana.getId(), ana)
                        .withAnalysis("CRM", 88, "Teléfono", "305-555-01", "(305) 555-0177", "Último contacto verificado por el agente")
                        .withSuggestion("phone", "(305) 555-0177")
                        .detectedAt(now.minus(Duration.ofHours(21))),
                new DataAnomalyEntity(AnomalyType.OTHER, AnomalySeverity.RED, "Correo electrónico rebotado",
                        "Las notificaciones de renovación rebotan: dominio de correo con error tipográfico.",
                        AffectedRecordType.PATIENT, david.getId(), david)
                        .withAnalysis("Marketplace", 97, "Correo electrónico", "david.williams@exmaple.test",
                                "david.williams@example.test", "Rebote SMTP 550 y registro del Marketplace")
                        .withSuggestion("email", "david.williams@example.test")
                        .detectedAt(now.minus(Duration.ofHours(23))),
                new DataAnomalyEntity(AnomalyType.OBSOLETE_FILE, AnomalySeverity.YELLOW, "Documento obsoleto",
                        "Expediente de una póliza terminada sin actividad en más de 12 meses.",
                        AffectedRecordType.PATIENT, patricia.getId(), patricia)
                        .withAnalysis("Archivo", 76, "Última actividad", LocalDate.of(year - 1, 12, 31).toString(),
                                "Sin actividad", "Política de retención documental")
                        .detectedAt(now.minus(Duration.ofDays(2)))));

        for (int i = 0; i < 3; i++) {
            DataAnomalyEntity resolved = new DataAnomalyEntity(AnomalyType.DUPLICATE, AnomalySeverity.YELLOW,
                    "Duplicado consolidado", "Fichas consolidadas por el equipo de auditoría.",
                    AffectedRecordType.PATIENT, elena.getId(), elena)
                    .withAnalysis("CRM", 90, "Identidad del miembro", "-", "-", "Revisión manual")
                    .detectedAt(now.minus(Duration.ofDays(3)));
            resolved.setResolutionStatus(AnomalyResolutionStatus.ARCHIVED);
            resolved.setResolutionAction(BatchResolutionAction.ARCHIVAR_DEFINITIVO);
            resolved.setResolvedBy(admin);
            resolved.setResolvedAt(now.minus(Duration.ofHours(30 + i)));
            anomalies.save(resolved);
        }

        log.info("Demo data loaded. Login with {} / {} (demo profile only)", DEMO_ADMIN, DEMO_PASSWORD);
    }

    private PatientEntity patient(String firstName, String lastName, String email, String phone, String dob,
                                  String income, AcaEligibilityStatus eligibility, AccountStatus status) {
        PatientEntity patient = new PatientEntity();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setEmail(email);
        patient.setEmailLookupHash(EmailLookup.hash(email));
        patient.setPhone(phone);
        patient.setDateOfBirth(dob);
        patient.setHouseholdIncome(income);
        patient.setAcaEligibilityStatus(eligibility);
        patient.setAccountStatus(status);
        return patients.save(patient);
    }

    private PolicyEntity policy(PatientEntity patient, String number, String insurer, String plan, LocalDate start,
                                LocalDate end, String premium, PolicyStatus status, boolean hasAnomalies) {
        PolicyEntity policy = new PolicyEntity(patient, number, insurer, plan, start, end, new BigDecimal(premium), status);
        policy.setHasAnomalies(hasAnomalies);
        return policies.save(policy);
    }

    private void claim(PatientEntity patient, String description, String location, ClaimPriority priority,
                       String amount, List<ClaimStatus> path, Instant updatedAt, String serviceName, String serviceDetails) {
        ClaimEntity claim = new ClaimEntity("CL-" + (48100 + (int) claims.count() * 37), patient, description);
        claim.setLocation(location);
        claim.setPriority(priority);
        claim.setAmount(amount == null ? null : new BigDecimal(amount));
        if (serviceName != null) claim.addService(serviceName, serviceDetails, updatedAt.plus(Duration.ofDays(1)));
        path.forEach(status -> claim.changeStatus(status, status == ClaimStatus.OPEN ? "Siniestro registrado" : null));
        claim.backdate(updatedAt.minus(Duration.ofDays(2)), updatedAt);
        claims.save(claim);
    }
}
