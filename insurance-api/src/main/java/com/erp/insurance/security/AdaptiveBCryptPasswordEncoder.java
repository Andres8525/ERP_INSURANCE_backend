package com.erp.insurance.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
