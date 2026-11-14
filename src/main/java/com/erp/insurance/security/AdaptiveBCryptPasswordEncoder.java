package com.erp.insurance.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BCrypt que pide re-cifrar cualquier hash cuyo coste sea distinto del configurado (mayor o menor).
 * El BCryptPasswordEncoder estándar solo actualiza hashes más débiles; aquí también se bajan los
 * de coste mayor para mantener el login rápido en servidores con poca CPU.
 * Spring Security llama a upgradeEncoding tras un login correcto y guarda el nuevo hash mediante
 * UserDetailsPasswordService.
 */
public class AdaptiveBCryptPasswordEncoder extends BCryptPasswordEncoder {
    private static final Pattern COST = Pattern.compile("^\\$2[aby]?\\$(\\d\\d)\\$");
    private final int strength;

    public AdaptiveBCryptPasswordEncoder(int strength) {
        super(strength);
        this.strength = strength;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) return false;
        Matcher matcher = COST.matcher(encodedPassword);
        return matcher.find() && Integer.parseInt(matcher.group(1)) != strength;
    }
}
